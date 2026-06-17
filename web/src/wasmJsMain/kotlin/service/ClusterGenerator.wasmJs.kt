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

package service

import de.stefan_oltmann.oni.model.Cluster
import worldgen.WorldgenMapData
import worldgen.WorldgenMapDataConverter
import kotlin.time.measureTimedValue

/*
 * Generates ONI clusters from worldgen coordinates.
 *
 * Each cluster type has its own prefix (e.g. "SNDST-A", "V-SNDST-C").
 * The coordinate format is: {prefix}-{seed}-0-0-0
 *
 * Uses the WorldgenWorkerPool for true parallel execution.
 * Each call to generateCluster() uses the worker assigned to the caller's index.
 */
object ClusterGenerator {

    private var worldgenVersion: String? = null

    private val clusterCache = LruCache<String, Cluster?>(100)

    /*
     * Generate a cluster for the given coordinate using a specific worker.
     *
     * @param coordinate The worldgen coordinate (e.g. "SNDST-A-0-0-0-0")
     * @param workerIndex The index of the WorldgenWorker to use for this generation
     */
    suspend fun generateCluster(coordinate: String, workerIndex: Int = 0): Cluster {

        val cachedCluster = clusterCache.get(coordinate)

        /* Respond from cache when possible. */
        if (cachedCluster != null)
            return cachedCluster

        val (cluster, duration) = measureTimedValue {

            if (worldgenVersion == null) {
                val worker = WorldgenWorkerPool.getWorker(0)
                worldgenVersion = worker.sendMessage("version") ?: ""
            }

            requireNotNull(worldgenVersion) { "Worldgen version not initialized." }

            val worker = WorldgenWorkerPool.getWorker(workerIndex)
            val json: String = worker.sendMessage("generate", coordinate) ?: ""

            val worldgenMapData = WorldgenMapData.fromJson(json)

            WorldgenMapDataConverter.convert(
                mapData = worldgenMapData,
                gameVersion = worldgenVersion!!.substringBefore('+').toInt()
            )
        }

        clusterCache.put(coordinate, cluster)

        println("[GENERATOR] generateCluster(): $coordinate | GENERATED in ${duration.inWholeMilliseconds}ms")

        return cluster
    }
}
