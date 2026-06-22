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
 * Complete state of the minter, passed to the UI via callback.
 *
 * Compose recomposes when this data class changes.
 * This is an immutable snapshot — the minter creates a new copy
 * on every state update via the `onStateUpdate` callback.
 *
 * @property isRunning Whether the minter is currently active
 * @property currentSeed The seed currently being processed (increments continuously)
 * @property workers Status of each worker for the worker panel
 * @property totalUploaded Number of successfully uploaded clusters
 * @property totalSkipped Number of clusters skipped because they already exist
 * @property totalErrors Number of failed uploads or generations
 * @property elapsedMs Time since the minter started (for rate calculation)
 * @property recentLogs Recent log entries (capped at 100)
 */
data class MinterState(
    val isRunning: Boolean = false,
    val currentSeed: Long = 0,
    val workers: List<WorkerStatus> = emptyList(),
    val totalUploaded: Long = 0,
    val totalSkipped: Long = 0,
    val totalErrors: Long = 0,
    val elapsedMs: Long = 0,
    val recentLogs: List<LogEntry> = emptyList(),
    val csvCoordinateCount: Int = 0
)
