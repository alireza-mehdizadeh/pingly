package ir.byalireza.pingly.data.repo

import ir.byalireza.pingly.data.api.ApiClient
import ir.byalireza.pingly.data.api.ApiException
import ir.byalireza.pingly.data.api.EventDto
import ir.byalireza.pingly.data.crypto.E2ee
import ir.byalireza.pingly.domain.EventStatus
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Sends interactions (encrypting their private part) and keeps the decrypted history. */
class EventRepository(
    private val api: ApiClient,
    private val account: AccountRepository,
    private val json: Json,
) {
    private val _history = MutableStateFlow<List<PinglyEvent>>(emptyList())
    val history: StateFlow<List<PinglyEvent>> = _history.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** Events arriving from the partner in real time, already decrypted. */
    private val _incoming = MutableSharedFlow<PinglyEvent>(extraBufferCapacity = 64)
    val incoming: SharedFlow<PinglyEvent> = _incoming.asSharedFlow()

    private val loadMutex = Mutex()

    fun decode(dto: EventDto): PinglyEvent {
        val type = EventType.fromWire(dto.type) ?: EventType.Ping
        var payload: JsonObject? = null
        var locked = false
        if (dto.enc != null) {
            val key = account.sharedKey()
            if (key == null) {
                locked = true
            } else {
                try {
                    payload = json.parseToJsonElement(E2ee.decryptText(key, dto.enc)).jsonObject
                } catch (e: Exception) {
                    locked = true
                }
            }
        }
        return PinglyEvent(
            id = dto.id,
            type = type,
            mine = dto.senderId == account.myId,
            status = EventStatus.fromWire(dto.status),
            createdAt = dto.createdAt,
            meta = dto.meta,
            payload = payload,
            locked = locked,
            replyTo = dto.replyTo,
        )
    }

    /**
     * Sends an interaction. `payload` is the private content; it is sealed with the couple's
     * end-to-end key before leaving the phone.
     */
    suspend fun send(
        type: EventType,
        meta: JsonObject = JsonObject(emptyMap()),
        payload: JsonObject? = null,
        replyTo: String? = null,
    ): PinglyEvent {
        val enc = payload?.let {
            val key = account.sharedKey() ?: throw ApiException(0, "missing_partner_key")
            E2ee.encryptText(key, json.encodeToString(JsonObject.serializer(), it))
        }
        val event = decode(api.sendEvent(type.wire, meta, enc, replyTo)).copy(payload = payload, locked = false)
        upsert(event)
        if (type == EventType.Answer && replyTo != null) setStatus(replyTo, EventStatus.Answered)
        return event
    }

    suspend fun refresh() = loadMutex.withLock {
        val page = api.events(before = null)
        _history.value = page.map(::decode)
        _hasMore.value = page.size >= PAGE
    }

    suspend fun loadMore() = loadMutex.withLock {
        if (!_hasMore.value) return@withLock
        val oldest = _history.value.lastOrNull()?.createdAt ?: return@withLock
        val page = api.events(before = oldest)
        _history.update { current -> (current + page.map(::decode)).distinctBy { it.id } }
        _hasMore.value = page.size >= PAGE
    }

    /** Called for every event pushed by the server. Returns the decoded event. */
    fun onIncoming(dto: EventDto): PinglyEvent {
        val event = decode(dto)
        upsert(event)
        if (event.type == EventType.Answer && event.replyTo != null) setStatus(event.replyTo, EventStatus.Answered)
        _incoming.tryEmit(event)
        return event
    }

    fun onStatus(id: String, status: String) = setStatus(id, EventStatus.fromWire(status))

    fun find(id: String): PinglyEvent? = _history.value.firstOrNull { it.id == id }

    /** The answer given to `questionId`, if it is in the loaded history. */
    fun answerTo(questionId: String): PinglyEvent? =
        _history.value.firstOrNull { it.type == EventType.Answer && it.replyTo == questionId }

    private fun setStatus(id: String, status: EventStatus) {
        _history.update { list -> list.map { if (it.id == id && rank(status) >= rank(it.status)) it.copy(status = status) else it } }
    }

    private fun rank(status: EventStatus) = when (status) {
        EventStatus.Sending -> 0
        EventStatus.Sent -> 1
        EventStatus.Expired -> 2
        EventStatus.Delivered -> 3
        EventStatus.Opened -> 4
        EventStatus.Answered -> 5
    }

    private fun upsert(event: PinglyEvent) {
        _history.update { list ->
            val without = list.filterNot { it.id == event.id }
            (listOf(event) + without).sortedByDescending { it.createdAt }
        }
    }

    fun clear() {
        _history.value = emptyList()
        _hasMore.value = true
    }

    private companion object {
        const val PAGE = 50
    }
}
