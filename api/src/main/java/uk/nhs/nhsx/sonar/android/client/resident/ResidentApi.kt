/*
 * Copyright © 2020 NHSX. All rights reserved.
 *
 */

package uk.nhs.nhsx.sonar.android.client.resident

import org.json.JSONObject
import uk.nhs.nhsx.sonar.android.client.http.HttpClient
import uk.nhs.nhsx.sonar.android.client.http.HttpRequest
import uk.nhs.nhsx.sonar.android.client.security.EncryptionKeyStorage
import javax.inject.Inject

typealias ErrorCallback = (Exception) -> Unit

// Register endpoint: POST /api/devices/registrations -d '{ pushToken: "base64(probably)-blabla" }' --> 204 - No Content
// Confirm registration endpoint:
// POST /api/devices
// -d '{ "activationCode": "uuid-blabla..." }'
// -> 200 { "id": "uuid-blabalabla", "secretKey": "base 64 encoded hmac compatible key" }

class ResidentApi @Inject constructor(
    private val encryptionKeyStorage: EncryptionKeyStorage,
    private val httpClient: HttpClient
) {

    fun register(token: String, onSuccess: () -> Unit = {}, onError: ErrorCallback = {}) {
        val requestJson = JSONObject().apply {
            put("pushToken", token)
        }
        val request = HttpRequest("/api/devices/registrations", requestJson)

        httpClient.post(
            request,
            { onSuccess() },
            { exception -> onError(exception) }
        )
    }

    fun confirmDevice(
        activationCode: String,
        firebaseToken: String,
        onSuccess: (Registration) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val requestJson = JSONObject()
        requestJson.put("activationCode", activationCode)
        requestJson.put("pushToken", firebaseToken)

        val request = HttpRequest("/api/devices", requestJson)
        httpClient.post(
            request,
            { responseJson ->
                val key = responseJson.getString("secretKey")
                encryptionKeyStorage.putBase64Key(key)
                onSuccess(mapResponseToRegistration(responseJson))
            }, onError
        )
    }

    private fun mapResponseToRegistration(jsonObject: JSONObject): Registration {
        val residentId = jsonObject.getString("id")
        return Registration(residentId)
    }
}
