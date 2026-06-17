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
 * Status of a single worker, displayed in the worker panel.
 *
 * Each worker corresponds to one Web Worker thread. The status shows
 * whether the worker is idle, generating a cluster, or uploading it.
 *
 * @param index Worker index (0-based), corresponds to the Web Worker index
 * @param phase Current phase (idle, generating, or uploading)
 * @param coordinate The cluster coordinate being processed (empty when idle)
 */
data class WorkerStatus(
    val index: Int,
    val phase: WorkerPhase = WorkerPhase.IDLE,
    val coordinate: String = ""
)
