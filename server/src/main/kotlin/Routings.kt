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
import com.github.luben.zstd.Zstd
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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    install(CORS) {

        anyMethod()

        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.ContentEncoding)
        allowHeader(HttpHeaders.LastModified)

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

            launch {

                createSearchIndexes()
            }

            call.respond(HttpStatusCode.OK, "Started creating search indexes.")
        }

        get("/index/{cluster}") {

            val clusterType = ClusterType.entries.find {
                it.prefix == call.parameters["cluster"]
            }

            if (clusterType == null) {
                call.respond(HttpStatusCode.NotFound, "Cluster type not found.")
                return@get
            }

            val file = File(searchIndexDir, clusterType.prefix)

            if (file.exists().not()) {
                call.respond(HttpStatusCode.NotFound, "File not found.")
                return@get
            }

            call.response.headers.append(HttpHeaders.ContentEncoding, "zstd")

            call.respondBytes(
                contentType = ContentType.Application.ProtoBuf,
                bytes = file.readBytes()
            )
        }

        head("/index/{cluster}") {

            val clusterType = ClusterType.entries.find {
                it.prefix == call.parameters["cluster"]
            }

            if (clusterType == null) {
                call.respond(HttpStatusCode.NotFound, "Cluster type not found.")
                return@head
            }

            val file = File(searchIndexDir, clusterType.prefix)

            if (file.exists().not()) {
                call.respond(HttpStatusCode.NotFound, "File not found.")
                return@head
            }

            call.response.headers.apply {
                append(HttpHeaders.ContentType, ContentType.Application.ProtoBuf.toString())
                append(HttpHeaders.ContentEncoding, "zstd")
                append(HttpHeaders.LastModified, file.lastModified().toString())
            }

            call.respond(HttpStatusCode.OK)
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
private suspend fun createSearchIndexes() {

    try {

        log("[INDEX] Create search indexes from database ...")

        val start = Clock.System.now().toEpochMilliseconds()

        val counts = coroutineScope {

            ClusterType.entries.map { cluster ->

                async(Dispatchers.Default) {

                    var summaryCount = 0

                    val time = measureTime {

                        val summaries = mutableListOf<ClusterSummaryCompact>()

                        val resultRows = transaction(sqliteDatabase) {

                            SearchIndexTable
                                .select(SearchIndexTable.coordinate, SearchIndexTable.data)
                                .where { SearchIndexTable.clusterTypeId eq cluster.id.toInt() }
                                .orderBy(SearchIndexTable.uploadDate to SortOrder.DESC)
                                .iterator()
                        }

                        while (resultRows.hasNext()) {

                            val resultRow = resultRows.next()

                            val bytes = resultRow[SearchIndexTable.data].bytes

                            val summary = ProtoBuf.decodeFromByteArray<ClusterSummaryCompact>(bytes)

//                            val startingAsteroid = summary.asteroidSummaries.first()
//                            val traits = WorldTrait.fromMask(startingAsteroid.worldTraitsBitMask)
//
//                            val thisCoord = resultRow[SearchIndexTable.coordinate]
//
//                            if (
//                                traits.contains(WorldTrait.GeoDormant) ||
//                                traits.contains(WorldTrait.MetalPoor) ||
//                                traits.contains(WorldTrait.BouldersLarge) ||
//                                traits.contains(WorldTrait.BouldersMedium) ||
//                                traits.contains(WorldTrait.BouldersSmall) ||
//                                traits.contains(WorldTrait.BouldersMixed)
//                            ) {
//
//                                transaction(sqliteDatabase) {
//                                    SearchIndexTable
//                                        .deleteWhere { SearchIndexTable.coordinate eq thisCoord }
//                                }
//
//                                println("Deleted $thisCoord from index.")
//
//                            } else
                                summaries.add(summary)
                        }

                        summaryCount = summaries.size

                        val searchIndex = SearchIndex.create(
                            clusterType = cluster,
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                            summaries = summaries
                        )

                        val protobufBytes = ProtoBuf.encodeToByteArray(searchIndex)

                        val compressedProtobufBytes = Zstd.compress(protobufBytes, 19)

                        File(searchIndexDir, cluster.prefix).writeBytes(compressedProtobufBytes)

                        searchIndex.summaries.size
                    }

                    log("[INDEX] Processed ${cluster.prefix} with $summaryCount seeds in $time.")

                    summaryCount
                }
            }.awaitAll()
        }

        val totalCount = counts.sum()

        countFile.writeText(totalCount.toString())

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
