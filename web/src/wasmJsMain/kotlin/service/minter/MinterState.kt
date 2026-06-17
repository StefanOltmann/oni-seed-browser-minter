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

/*
 * Complete state of the minter, passed to the UI via callback.
 * Compose recomposes when this data class changes.
 *
 * This is an immutable snapshot — the minter creates a new copy
 * on every state update via the onStateUpdate callback.
 *
 * @param isRunning Whether the minter is currently active
 * @param currentSeed The seed currently being processed (increments continuously)
 * @param workers Status of each worker for the worker panel
 * @param totalUploaded Number of successfully uploaded clusters
 * @param totalErrors Number of failed uploads or generations
 * @param elapsedMs Time since the minter started (for rate calculation)
 * @param recentLogs Recent log entries (capped at 100)
 */
data class MinterState(
    val isRunning: Boolean = false,
    val currentSeed: Long = 0,
    val workers: List<WorkerStatus> = emptyList(),
    val totalUploaded: Long = 0,
    val totalErrors: Long = 0,
    val elapsedMs: Long = 0,
    val recentLogs: List<LogEntry> = emptyList()
)
