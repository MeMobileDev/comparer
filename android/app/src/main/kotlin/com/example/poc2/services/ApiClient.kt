package com.example.poc2.services

import android.util.Log
import com.example.poc2.model.ApiError
import com.example.poc2.model.ApiResult
import com.example.poc2.model.DialerPopupInfo
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    private const val TAG = "ApiClient"
    private const val BASE_URL =
        "https://client-glfunnel-service-144135692330.asia-south1.run.app/api/v1/lead/dialer-popup-info"

    fun fetchDialerPopup(uid: String, phoneNumber: String): ApiResult<DialerPopupInfo> {
        var connection: HttpURLConnection? = null

        return try {
            // Setup request
            val url = URL(BASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 7000
                readTimeout = 7000
            }

            // Request body
            val requestBody = JSONObject().apply {
                put("uid", uid)
                put("phoneNumber", phoneNumber)
            }

            BufferedOutputStream(connection.outputStream).use { out ->
                out.write(requestBody.toString().toByteArray())
                out.flush()
            }

            // Read response
            val statusCode = connection.responseCode
            val isSuccess = statusCode in 200..299
            val stream = if (isSuccess) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            Log.d(TAG, "Response ($statusCode): $responseText")

            if (isSuccess) {
                // Parse success response
                val json = JSONObject(responseText)
                val data = json.optJSONObject("data")
                    ?: return ApiResult.Failure(ApiError(500, "Invalid response format"))

                val info = DialerPopupInfo(
                    id = data.optString("id", ""),
                    name = data.optString("name", "Unknown"),
                    countryCode = data.optString("countryCode"),
                    phoneNumber = data.optString("phoneNumber"),
                    status = data.optString("status"),
                    leadQuality = data.optString("leadQuality"),
                    leadCharacter = data.optString("leadCharacter"),
                    notes = data.optString("notes"),
                    lastAct = data.optInt("lastAct", 0)
                )

                ApiResult.Success(info)
            } else {
                // Parse error response (plain text or HTML)
                val cleanedMessage = responseText
                    .replace(Regex("<[^>]*>"), "") // Remove HTML tags
                    .trim()
                    .ifBlank { "Server returned error ($statusCode)" }

                ApiResult.Failure(ApiError(statusCode, cleanedMessage))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            ApiResult.Failure(ApiError(-1, "Network error: ${e.localizedMessage}"))
        } finally {
            connection?.disconnect()
        }
    }
}