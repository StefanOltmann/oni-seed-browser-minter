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

/**
 * Result of an upload attempt.
 *
 * Sealed class allows exhaustive `when()` handling in the minter,
 * ensuring all error cases are explicitly handled in the UI.
 */
sealed class UploadResult {

    /** Upload succeeded. Contains the response body for logging. */
    data class Success(val body: String) : UploadResult()

    /** Server returned a non-2xx status code. Contains the HTTP status and response body. */
    data class Failure(val statusCode: Int, val message: String) : UploadResult()

    /** Connection error or other exception. Contains the original exception. */
    data class Error(val exception: Exception) : UploadResult()
}

/**
 * Dedicated HTTP client for uploading clusters to the server.
 *
 * Separated from the minter logic to encapsulate HTTP concerns:
 *  - Explicitly checks response status code (does not rely on Ktor's expectSuccess)
 *  - Reads the response body to ensure Ktor processes the full response
 *  - Catches connection errors and returns them as [UploadResult.Error]
 *
 * This design ensures the UI always receives structured error information
 * rather than raw exceptions.
 *
 * @property httpClient Ktor HTTP client instance (Js engine)
 */
class WebClient(private val httpClient: HttpClient) {

    /*
     * Upload cluster JSON to the server.
     *
     * The upload endpoint expects a JSON body with the cluster data.
     * The server stores the cluster in SQLite and returns a response.
     *
     * @param serverUrl Base URL of the server (e.g. "http://localhost:8080")
     * @param clusterJson JSON-serialized cluster data
     * @return UploadResult indicating success, HTTP failure, or connection error
     */
    suspend fun uploadCluster(serverUrl: String, clusterJson: String): UploadResult {
        return try {
            val response = httpClient.post("$serverUrl/upload") {
                contentType(ContentType.Application.Json)
                setBody(clusterJson)
            }

            /* Read body to ensure Ktor processes the full response */
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
            /* Connection errors, timeouts, etc. */
            UploadResult.Error(ex)
        }
    }
}
