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

package service.minter

/**
 * A single log entry displayed in the log panel.
 *
 * Each entry has a level (INFO, WARN, ERROR), the associated seed,
 * an optional cluster coordinate, and a human-readable message.
 * Log entries are capped at 100 in the minter — older entries are discarded.
 *
 * @property level Log level (INFO, WARN, ERROR)
 * @property seed The seed this log entry is associated with
 * @property coordinate The cluster coordinate (empty for global messages)
 * @property message Human-readable log message
 */
data class LogEntry(
    val level: Level,
    val seed: Long,
    val coordinate: String,
    val message: String
) {
    /** Log severity level. */
    enum class Level { INFO, WARN, ERROR }
}
