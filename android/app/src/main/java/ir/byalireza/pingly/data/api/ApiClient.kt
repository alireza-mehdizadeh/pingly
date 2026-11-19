package ir.byalireza.pingly.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Thin, typed client for the Pingly REST API. Every call runs on the IO dispatcher. */
class ApiClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: () -> String,
    private val token: () -> String?,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val binaryType = "application/octet-stream".toMediaType()

    /** Called whenever the server rejects our token, so the app can sign out cleanly. */
    var onUnauthorized: (() -> Unit)? = null

    // ── Auth ────────────────────────────────────────────────────────────────
    suspend fun requestOtp(phone: String): OtpRequestResponse =
        call("POST", "/v1/auth/otp/request", buildJsonObject { put("phone", phone) }, OtpRequestResponse.serializer(), auth = false)

    suspend fun verifyOtp(phone: String, code: String, device: String): VerifyResponse =
        call(
            "POST", "/v1/auth/otp/verify",
            buildJsonObject {
                put("phone", phone)
                put("code", code)
                put("device", device)
            },
            VerifyResponse.serializer(), auth = false,
        )

    suspend fun logout() {
        call("POST", "/v1/auth/logout", JsonObject(emptyMap()), JsonObject.serializer())
    }

    // ── Me ──────────────────────────────────────────────────────────────────
    suspend fun me(): MeDto = call("GET", "/v1/me", null, MeDto.serializer())

    suspend fun updateMe(patch: JsonObject): UserDto = call("PATCH", "/v1/me", patch, UserEnvelope.serializer()).user

    /** Permanently deletes the account and everything shared with the partner. */
    suspend fun deleteAccount() {
        call("DELETE", "/v1/me", null, JsonObject.serializer())
    }

    suspend fun setPaused(paused: Boolean): UserDto =
        call("POST", "/v1/me/pause", buildJsonObject { put("paused", paused) }, UserEnvelope.serializer()).user

    // ── Pairing ─────────────────────────────────────────────────────────────
    suspend fun invite(phone: String): InvitesDto =
        call("POST", "/v1/pair/invites", buildJsonObject { put("phone", phone) }, InvitesDto.serializer())

    suspend fun acceptInvite(id: String): MeDto = call("POST", "/v1/pair/invites/$id/accept", JsonObject(emptyMap()), MeDto.serializer())

    suspend fun declineInvite(id: String): InvitesDto =
        call("POST", "/v1/pair/invites/$id/decline", JsonObject(emptyMap()), InvitesDto.serializer())

    suspend fun cancelInvite(id: String): InvitesDto = call("DELETE", "/v1/pair/invites/$id", null, InvitesDto.serializer())

    suspend fun unpair(): MeDto = call("DELETE", "/v1/pair", null, MeDto.serializer())

    // ── Events ──────────────────────────────────────────────────────────────
    suspend fun sendEvent(type: String, meta: JsonObject, enc: String?, replyTo: String?): EventDto =
        call(
            "POST", "/v1/events",
            buildJsonObject {
                put("type", type)
                put("meta", meta)
                if (enc != null) put("enc", enc)
                if (replyTo != null) put("replyTo", replyTo)
            },
            EventEnvelope.serializer(),
        ).event

    suspend fun events(before: Long?, limit: Int = 50): List<EventDto> {
        val query = buildString {
            append("?limit=").append(limit)
            if (before != null) append("&before=").append(before)
        }
        return call("GET", "/v1/events$query", null, EventsEnvelope.serializer()).events
    }

    suspend fun ack(id: String) {
        call("POST", "/v1/events/$id/ack", JsonObject(emptyMap()), JsonObject.serializer())
    }

    suspend fun markOpened(id: String) {
        call("POST", "/v1/events/$id/opened", JsonObject(emptyMap()), JsonObject.serializer())
    }

    // ── Media ───────────────────────────────────────────────────────────────
    suspend fun uploadMedia(kind: String, bytes: ByteArray, encTitle: String?, durationMs: Long?): MediaDto {
        val url = url("/v1/media").newBuilder()
            .addQueryParameter("kind", kind)
            .apply {
                if (encTitle != null) addQueryParameter("title", encTitle)
                if (durationMs != null) addQueryParameter("durationMs", durationMs.toString())
            }
            .build()
        val request = Request.Builder().url(url).post(bytes.toRequestBody(binaryType)).withAuth().build()
        return parse(execute(request), MediaEnvelope.serializer()).media
    }

    suspend fun listMedia(kind: String? = null): List<MediaDto> =
        call("GET", "/v1/media" + (kind?.let { "?kind=$it" } ?: ""), null, MediaListEnvelope.serializer()).media

    suspend fun downloadMedia(id: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/v1/media/$id")).get().withAuth().build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw errorFrom(response.code, response.body?.string().orEmpty())
                response.body?.bytes() ?: ByteArray(0)
            }
        } catch (e: IOException) {
            throw ApiException(0, "network", cause = e)
        }
    }

    suspend fun deleteMedia(id: String) {
        call("DELETE", "/v1/media/$id", null, JsonObject.serializer())
    }

    // ── Plumbing ────────────────────────────────────────────────────────────
    private fun url(path: String) = (baseUrl().trimEnd('/') + path).toHttpUrl()

    private fun Request.Builder.withAuth(): Request.Builder = apply {
        token()?.let { header("Authorization", "Bearer $it") }
    }

    private suspend fun <T> call(
        method: String,
        path: String,
        body: JsonElement?,
        deserializer: DeserializationStrategy<T>,
        auth: Boolean = true,
    ): T {
        val requestBody: RequestBody? = body?.let { json.encodeToString(JsonElement.serializer(), it).toRequestBody(jsonType) }
        val builder = Request.Builder().url(url(path)).method(method, requestBody)
        if (auth) builder.withAuth()
        return parse(execute(builder.build()), deserializer)
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw errorFrom(response.code, text)
                text
            }
        } catch (e: IOException) {
            throw ApiException(0, "network", cause = e)
        }
    }

    private fun <T> parse(text: String, deserializer: DeserializationStrategy<T>): T = try {
        json.decodeFromString(deserializer, text.ifBlank { "{}" })
    } catch (e: Exception) {
        throw ApiException(200, "bad_response", cause = e)
    }

    private fun errorFrom(status: Int, text: String): ApiException {
        val error = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject }.getOrNull()
        val code = error?.get("code")?.jsonPrimitive?.content ?: if (status == 401) "unauthorized" else "server_error"
        val exception = ApiException(
            status = status,
            code = code,
            retryAfter = error?.get("retryAfter")?.jsonPrimitive?.intOrNull,
            attemptsLeft = error?.get("attemptsLeft")?.jsonPrimitive?.intOrNull,
        )
        if (status == 401) onUnauthorized?.invoke()
        return exception
    }
}
