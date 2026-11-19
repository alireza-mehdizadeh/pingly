package ir.byalireza.pingly.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Mirrors the JSON returned by the Pingly server (server/src/models.ts).

@Serializable
data class AllowDto(
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    val voice: Boolean = true,
    val image: Boolean = true,
    val message: Boolean = true,
    val question: Boolean = true,
)

@Serializable
data class SettingsDto(val allow: AllowDto = AllowDto())

@Serializable
data class UserDto(
    val id: String,
    val phone: String,
    val displayName: String = "",
    val avatar: String = "bunny",
    val publicKey: String? = null,
    val paused: Boolean = false,
    val settings: SettingsDto = SettingsDto(),
    val createdAt: Long = 0,
)

@Serializable
data class PartnerDto(
    val id: String,
    val phone: String,
    val displayName: String = "",
    val avatar: String = "bunny",
    val publicKey: String? = null,
    val paused: Boolean = false,
    val online: Boolean = false,
    val lastSeenAt: Long? = null,
    val allow: AllowDto = AllowDto(),
)

@Serializable
data class PairDto(val id: String, val createdAt: Long)

@Serializable
data class InviterDto(val displayName: String = "", val avatar: String = "bunny", val phone: String)

@Serializable
data class IncomingInviteDto(val id: String, val from: InviterDto, val createdAt: Long, val expiresAt: Long)

@Serializable
data class OutgoingInviteDto(val id: String, val toPhone: String, val createdAt: Long, val expiresAt: Long)

@Serializable
data class InvitesDto(
    val incoming: List<IncomingInviteDto> = emptyList(),
    val outgoing: OutgoingInviteDto? = null,
)

@Serializable
data class MeDto(
    val user: UserDto,
    val pair: PairDto? = null,
    val partner: PartnerDto? = null,
    val invites: InvitesDto = InvitesDto(),
    val serverTime: Long = 0,
)

@Serializable
data class EventDto(
    val id: String,
    val type: String,
    val senderId: String,
    val recipientId: String,
    val meta: JsonObject = JsonObject(emptyMap()),
    val enc: String? = null,
    val replyTo: String? = null,
    val status: String = "sent",
    val createdAt: Long,
    val deliveredAt: Long? = null,
    val openedAt: Long? = null,
    val expiresAt: Long? = null,
)

@Serializable
data class MediaDto(
    val id: String,
    val ownerId: String,
    val kind: String,
    val size: Long,
    val encTitle: String? = null,
    val durationMs: Long? = null,
    val createdAt: Long,
)

@Serializable
data class OtpRequestResponse(val expiresIn: Int = 120, val retryAfter: Int = 60)

@Serializable
data class VerifyResponse(val token: String, val user: UserDto, val isNewUser: Boolean = false)

@Serializable
internal data class UserEnvelope(val user: UserDto)

@Serializable
internal data class EventEnvelope(val event: EventDto)

@Serializable
internal data class EventsEnvelope(val events: List<EventDto>)

@Serializable
internal data class MediaEnvelope(val media: MediaDto)

@Serializable
internal data class MediaListEnvelope(val media: List<MediaDto>)
