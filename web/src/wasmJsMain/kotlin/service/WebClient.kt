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

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/*
 * Result of an upload attempt.
 * Either a success with the response body, or a failure with status code and message.
 */
sealed class UploadResult {
    data class Success(val body: String) : UploadResult()
    data class Failure(val statusCode: Int, val message: String) : UploadResult()
    data class Error(val exception: Exception) : UploadResult()
}

/*
 * Dedicated HTTP client for uploading clusters to the server.
 *
 * Separated from the minter logic to handle HTTP status codes properly.
 * Ktor's expectSuccess default only throws for 4xx/5xx, but we also want
 * to detect connection errors and log the response body.
 */
class WebClient(private val httpClient: HttpClient) {

    /*
     * Upload cluster JSON to the server.
     *
     * Explicitly checks the status code and reads the response body.
     * Returns a sealed class result for proper error handling in the UI.
     */
    suspend fun uploadCluster(serverUrl: String, clusterJson: String): UploadResult {
        return try {
            val response = httpClient.post("$serverUrl/upload") {
                contentType(ContentType.Application.Json)
                setBody(clusterJson)
            }

            val body = response.bodyAsText()

            if (response.status.isSuccess()) {
                UploadResult.Success(body)
            } else {
                UploadResult.Failure(
                    statusCode = response.status.value,
                    message = body.ifBlank { "HTTP ${response.status.value}" }
                )
            }
        } catch (ex: Exception) {
            UploadResult.Error(ex)
        }
    }
}
