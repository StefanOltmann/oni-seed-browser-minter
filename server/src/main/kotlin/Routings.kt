/*
 * ONI Seed Browser
 * Copyright (C) 2026 Stefan Oltmann
 * https://stefan-oltmann.de
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import db.DatabaseFactory
import db.SearchIndexTable
import de.stefan_oltmann.oni.model.Cluster
import de.stefan_oltmann.oni.model.ClusterType
import de.stefan_oltmann.oni.model.search.ClusterSummaryCompact
import de.stefan_oltmann.oni.model.search.SearchIndex
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import util.ZipUtil
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.uuid.ExperimentalUuidApi

private val strictJson = Json {

    ignoreUnknownKeys = false

    encodeDefaults = true
}

private val dataDir: File = File("/data")

private val searchIndexDir: File = File(dataDir, "index")
private val countFile: File = File(searchIndexDir, "count")

private val sqliteDatabase = DatabaseFactory.init(
    url = "jdbc:sqlite:/data/oni-data.db?journal_mode=WAL",
    username = "",
    password = ""
)

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureRouting() {

    try {

        configureRoutingInternal()

    } catch (ex: Throwable) {

        log("Starting server $VERSION failed.")
        log(ex)
    }
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)
private fun Application.configureRoutingInternal() {

    val startTime = Clock.System.now().toEpochMilliseconds()

    println("[INIT] Starting Server at version $VERSION")

    if (!dataDir.exists())
        error("Data dir is missing!")

    searchIndexDir.mkdirs()

    install(ContentNegotiation) {
        json(strictJson)
    }

    install(Compression) {
        gzip {

            /* Apply gzip compression only when the client requests it via `Accept-Encoding: gzip` */
            priority = 1.0

            /* Only compress responses larger than 1 KB (for efficiency) */
            minimumSize(1024)

            matchContentType(ContentType.Application.Json, ContentType.Application.Zip)
        }
    }

    install(CORS) {

        anyMethod()

        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.ContentType)

        anyHost()
    }

    routing {

        get("/") {

            val uptimeMinutes = (Clock.System.now().toEpochMilliseconds() - startTime) / 1000 / 60

            val uptimeHours = uptimeMinutes / 60
            val minutes = uptimeMinutes % 60

            call.respondText("ONI Seed Browser Minter $VERSION (up since $uptimeHours hours and $minutes minutes)")
        }

        get("/exist") {

            val coordinate = call.request.queryParameters["coordinate"]

            if (coordinate.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing coordinate parameter")
                return@get
            }

            val exists = transaction(sqliteDatabase) {
                SearchIndexTable
                    .select(SearchIndexTable.coordinate)
                    .where { SearchIndexTable.coordinate eq coordinate }
                    .empty().not()
            }

            call.respond(HttpStatusCode.OK, exists)
        }

        get("/create") {

            createSearchIndexes()
        }

        post("/upload") {

            try {

                val start = Clock.System.now().toEpochMilliseconds()

                /*
                 * Receive the upload and perform further checks
                 */

                val originalData = call.receiveText()

                /* Read the JSON data and be strict. */
                val cluster = strictJson.decodeFromString<Cluster>(originalData)

                /*
                 * Reject doubled entries.
                 */

                val mapAlreadyExists = transaction(sqliteDatabase) {
                    SearchIndexTable
                        .select(SearchIndexTable.coordinate)
                        .where { SearchIndexTable.coordinate eq cluster.coordinate }
                        .empty().not()
                }

                if (mapAlreadyExists) {
                    call.respond(HttpStatusCode.Conflict, "Map ${cluster.coordinate} already exists.")
                    return@post
                }

                /*
                 * Save the upload to the database
                 */

                val uploadDate: Long = Clock.System.now().toEpochMilliseconds()

                /*
                 * Database updates
                 */

                transaction(sqliteDatabase) {

                    val clusterCoordinate = cluster.coordinate

                    /*
                     * Add to the search index
                     */

                    val summary = ClusterSummaryCompact.create(cluster)
                    val summaryBytes = ProtoBuf.encodeToByteArray(summary)

                    SearchIndexTable.insert {

                        it[SearchIndexTable.coordinate] = clusterCoordinate
                        it[SearchIndexTable.clusterTypeId] = cluster.cluster.id.toInt()
                        it[SearchIndexTable.uploaderSteamIdHash] = "generated"
                        it[SearchIndexTable.gameVersion] = cluster.gameVersion
                        it[SearchIndexTable.uploadDate] = uploadDate
                        it[SearchIndexTable.data] = ExposedBlob(summaryBytes)
                    }
                }

                /*
                 * Finalize
                 */

                call.respond(HttpStatusCode.OK, "Data was saved.")

                val duration = Clock.System.now().toEpochMilliseconds() - start

                log("[UPLOAD] ${cluster.coordinate} in $duration ms")

            } catch (ex: Exception) {

                log(ex)

                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
private fun createSearchIndexes() {

    try {

        log("[INDEX] Create search indexes from database ...")

        val start = Clock.System.now().toEpochMilliseconds()

        var count = 0L

        val countPerContributor = mutableMapOf<String, Long>()

        for (cluster in ClusterType.entries) {

            val time = measureTime {

                val summaries = mutableListOf<ClusterSummaryCompact>()

                val resultRows = transaction(sqliteDatabase) {

                    SearchIndexTable
                        .select(SearchIndexTable.uploaderSteamIdHash, SearchIndexTable.data)
                        .where { SearchIndexTable.clusterTypeId eq cluster.id.toInt() }
                        .orderBy(SearchIndexTable.uploadDate to SortOrder.DESC)
                        .iterator()
                }

                while (resultRows.hasNext()) {

                    val resultRow = resultRows.next()

                    val uploaderSteamIdHash = resultRow[SearchIndexTable.uploaderSteamIdHash]

                    /* Increase count */
                    countPerContributor[uploaderSteamIdHash] =
                        (countPerContributor[uploaderSteamIdHash] ?: 0L) + 1

                    val bytes = resultRow[SearchIndexTable.data].bytes

                    val summary = ProtoBuf.decodeFromByteArray<ClusterSummaryCompact>(bytes)

                    summaries.add(summary)
                }

                val searchIndex = SearchIndex.create(
                    clusterType = cluster,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    summaries = summaries
                )

                val protobufBytes = ProtoBuf.encodeToByteArray(searchIndex)

                val zippedProtobufBytes = ZipUtil.zipBytes(protobufBytes)

                File(searchIndexDir, cluster.prefix).writeBytes(zippedProtobufBytes)

                count += searchIndex.summaries.size
            }

            log("[INDEX] Processed ${cluster.prefix} in $time.")
        }

        countFile.writeText(count.toString())

        val duration = Clock.System.now().toEpochMilliseconds() - start

        log("[INDEX] Created search indexes in $duration ms.")

    } catch (ex: Exception) {

        log(ex)
    }
}

private fun log(message: String) =
    println(message)

private fun log(ex: Throwable) =
    ex.printStackTrace()
