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
package db

import org.jetbrains.exposed.v1.core.Table

/*
 * Stores compact cluster summaries for search (one row per coordinate)
 */
object SearchIndexTable : Table("search_index") {

    val coordinate = varchar("coordinate", 50)
    val clusterTypeId = integer("cluster_type_id")
    val uploaderSteamIdHash = varchar("uploader_steam_id_hash", 80)
    val gameVersion = integer("game_version")
    val uploadDate = long("upload_date")
    val data = blob("data")

    override val primaryKey = PrimaryKey(coordinate)
}
