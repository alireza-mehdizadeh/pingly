package ir.byalireza.pingly.data.repo

import android.content.Context
import ir.byalireza.pingly.data.api.ApiClient
import ir.byalireza.pingly.data.api.ApiException
import ir.byalireza.pingly.data.api.MediaDto
import ir.byalireza.pingly.data.crypto.E2ee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

enum class MediaKind(val wire: String) { Voice("voice"), Image("image") }

data class MediaItem(
    val id: String,
    val mine: Boolean,
    val kind: MediaKind,
    val title: String?,
    val durationMs: Long?,
    val createdAt: Long,
    /** Sealed for a previous phone's keys; can't be opened here. */
    val locked: Boolean,
)

/**
 * The couple's voice notes and photos. Bytes are encrypted on this phone before upload and
 * decrypted into the app-private cache when played or shown.
 */
class MediaRepository(
    context: Context,
    private val api: ApiClient,
    private val account: AccountRepository,
) {
    private val cacheDir = File(context.noBackupFilesDir, "media").apply { mkdirs() }

    private val _voices = MutableStateFlow<List<MediaItem>>(emptyList())
    val voices: StateFlow<List<MediaItem>> = _voices.asStateFlow()

    private val _images = MutableStateFlow<List<MediaItem>>(emptyList())
    val images: StateFlow<List<MediaItem>> = _images.asStateFlow()

    private fun flowFor(kind: MediaKind) = if (kind == MediaKind.Voice) _voices else _images

    private fun decode(dto: MediaDto): MediaItem {
        val key = account.sharedKey()
        var locked = key == null
        val title = dto.encTitle?.let { enc ->
            key?.let { runCatching { E2ee.decryptText(it, enc) }.onFailure { locked = true }.getOrNull() }
        }
        return MediaItem(
            id = dto.id,
            mine = dto.ownerId == account.myId,
            kind = if (dto.kind == "voice") MediaKind.Voice else MediaKind.Image,
            title = title,
            durationMs = dto.durationMs,
            createdAt = dto.createdAt,
            locked = locked,
        )
    }

    suspend fun refresh(kind: MediaKind) {
        flowFor(kind).value = api.listMedia(kind.wire).map(::decode)
    }

    suspend fun upload(kind: MediaKind, plain: ByteArray, title: String?, durationMs: Long?): MediaItem {
        val key = account.sharedKey() ?: throw ApiException(0, "missing_partner_key")
        val sealed = withContext(Dispatchers.Default) { E2ee.encrypt(key, plain) }
        val encTitle = title?.takeIf { it.isNotBlank() }?.let { E2ee.encryptText(key, it.trim()) }
        val dto = api.uploadMedia(kind.wire, sealed, encTitle, durationMs)
        // Keep the plaintext we already have, so playing/showing it needs no download.
        withContext(Dispatchers.IO) { File(cacheDir, dto.id).writeBytes(plain) }
        val item = decode(dto).copy(title = title?.trim(), locked = false)
        flowFor(kind).update { listOf(item) + it.filterNot { m -> m.id == item.id } }
        return item
    }

    /** Decrypted file for `id`, downloading it on first use. */
    suspend fun file(id: String): File = withContext(Dispatchers.IO) {
        val cached = File(cacheDir, id)
        if (cached.exists() && cached.length() > 0) return@withContext cached
        val key = account.sharedKey() ?: throw ApiException(0, "missing_partner_key")
        val plain = runCatching { E2ee.decrypt(key, api.downloadMedia(id)) }
            .getOrElse { if (it is ApiException) throw it else throw ApiException(0, "media_locked", cause = it) }
        val tmp = File(cacheDir, "$id.part")
        tmp.writeBytes(plain)
        tmp.renameTo(cached)
        cached
    }

    suspend fun delete(item: MediaItem) {
        api.deleteMedia(item.id)
        File(cacheDir, item.id).delete()
        flowFor(item.kind).update { list -> list.filterNot { it.id == item.id } }
    }

    fun clear() {
        _voices.value = emptyList()
        _images.value = emptyList()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
