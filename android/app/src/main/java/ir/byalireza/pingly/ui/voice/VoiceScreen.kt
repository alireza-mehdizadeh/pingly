package ir.byalireza.pingly.ui.voice

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.api.toPersianMessage
import ir.byalireza.pingly.data.repo.MediaItem
import ir.byalireza.pingly.data.repo.MediaKind
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmojiBadge
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PillButton
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.SectionTitle
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Records short AAC voice notes into the app cache. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(onLimitReached: () -> Unit) {
        cancel()
        val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioChannels(1)
        r.setMaxDuration(MAX_MS)
        r.setOutputFile(out.absolutePath)
        r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimitReached() }
        r.prepare()
        r.start()
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
    }

    fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    /** Returns the recording and its length, or null if it was too short / failed. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val out = file
        val duration = System.currentTimeMillis() - startedAt
        recorder = null
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || out == null || duration < 700) {
            out?.delete()
            return null
        }
        return out to duration.coerceAtMost(MAX_MS.toLong())
    }

    fun cancel() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        file?.delete()
        file = null
    }

    companion object {
        const val MAX_MS = 30_000
    }
}

sealed interface RecordState {
    data object Idle : RecordState
    data class Recording(val startedAt: Long, val levels: List<Float>) : RecordState
    data class Recorded(val file: File, val durationMs: Long) : RecordState
}

class VoiceViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val voices = container.media.voices
    val nowPlaying = container.sound.nowPlaying
    private val recorder = VoiceRecorder(app)
    private var meter: Job? = null

    private val _record = MutableStateFlow<RecordState>(RecordState.Idle)
    val record: StateFlow<RecordState> = _record.asStateFlow()

    init {
        viewModelScope.launch { runCatching { container.media.refresh(MediaKind.Voice) } }
    }

    fun startRecording() {
        container.sound.stop()
        try {
            recorder.start(onLimitReached = ::stopRecording)
        } catch (e: Exception) {
            toast("میکروفون در دسترس نیست 🎙️")
            return
        }
        val started = System.currentTimeMillis()
        _record.value = RecordState.Recording(started, emptyList())
        meter = viewModelScope.launch {
            while (true) {
                delay(90)
                val level = (recorder.amplitude() / 22_000f).coerceIn(0.05f, 1f)
                val current = _record.value as? RecordState.Recording ?: break
                _record.value = current.copy(levels = (current.levels + level).takeLast(36))
            }
        }
    }

    fun stopRecording() {
        meter?.cancel()
        val result = recorder.stop()
        _record.value = if (result != null) RecordState.Recorded(result.first, result.second) else {
            toast("صدا خیلی کوتاه بود؛ دکمه رو نگه دار و یه جمله بگو 🙂")
            RecordState.Idle
        }
    }

    fun discard() {
        (record.value as? RecordState.Recorded)?.file?.delete()
        container.sound.stop()
        _record.value = RecordState.Idle
    }

    fun previewRecording() {
        val rec = record.value as? RecordState.Recorded ?: return
        if (nowPlaying.value == "draft") container.sound.stop()
        else container.sound.playFile(rec.file, "draft", fromPartner = false)
    }

    fun save(title: String) {
        val rec = record.value as? RecordState.Recorded ?: return
        action(success = "صدات با رمزنگاری سرتاسری ذخیره شد 🔐") {
            container.media.upload(MediaKind.Voice, rec.file.readBytes(), title.ifBlank { "صدای من" }, rec.durationMs)
            rec.file.delete()
            _record.value = RecordState.Idle
        }
    }

    fun play(item: MediaItem) {
        if (nowPlaying.value == item.id) {
            container.sound.stop()
            return
        }
        viewModelScope.launch {
            try {
                container.sound.playFile(container.media.file(item.id), item.id, fromPartner = false)
            } catch (e: Exception) {
                toast(e.toPersianMessage())
            }
        }
    }

    fun sendToPartner(item: MediaItem) = action(success = "صدات داره روی گوشیش پخش می‌شه 🎧") {
        container.events.send(EventType.Voice, meta = buildJsonObject { put("mediaId", item.id) })
    }

    fun delete(item: MediaItem) = action(success = "پاک شد") { container.media.delete(item) }

    override fun onCleared() {
        recorder.cancel()
        container.sound.stop()
    }
}

@Composable
fun VoiceScreen(onBack: () -> Unit, vm: VoiceViewModel = viewModel()) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val couple by vm.couple.collectAsState()
    val voices by vm.voices.collectAsState()
    val record by vm.record.collectAsState()
    val playing by vm.nowPlaying.collectAsState()
    val busy by vm.busy.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else toaster.show("برای ضبط صدا، اجازه‌ی میکروفون لازمه 🎙️")
    }
    fun record() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(Unit) { onDispose { if (vm.record.value is RecordState.Recording) vm.stopRecording() } }

    PinglyBackground {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            TopBar("صدای من 🎙️", onBack = onBack, subtitle = "صدات رو ضبط کن تا هر وقت خواستی روی گوشی ${couple.partnerName} پخش بشه")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                PinglyCard {
                    AnimatedContent(record::class, label = "rec") { _ ->
                        when (val r = record) {
                            RecordState.Idle -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                RecordButton(recording = false, onClick = ::record)
                                Spacer(Modifier.height(10.dp))
                                Text("بزن و حرفت رو بزن (تا ۳۰ ثانیه)", style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft)
                            }
                            is RecordState.Recording -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Waveform(r.levels)
                                Spacer(Modifier.height(10.dp))
                                RecordingTimer(r.startedAt)
                                Spacer(Modifier.height(10.dp))
                                RecordButton(recording = true, onClick = vm::stopRecording)
                                Spacer(Modifier.height(6.dp))
                                Text("برای تموم کردن دوباره بزن", style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft)
                            }
                            is RecordState.Recorded -> Column(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EmojiBadge("🎧", background = PinglyColors.LilacMist)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "صدای تازه · ${Persian.duration(r.durationMs)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PinglyColors.Ink,
                                        modifier = Modifier.weight(1f),
                                    )
                                    PillButton(if (playing == "draft") "⏹" else "▶ گوش بده", onClick = vm::previewRecording, filled = false, color = PinglyColors.Lilac)
                                }
                                Spacer(Modifier.height(12.dp))
                                PinglyTextField(title, { title = it }, label = "اسم این صدا", placeholder = "مثلاً: صبح بخیر عشقم", maxLength = 40)
                                Spacer(Modifier.height(12.dp))
                                PrimaryButton("ذخیره 💾", onClick = { vm.save(title); title = "" }, loading = busy)
                                Spacer(Modifier.height(6.dp))
                                PillButton("دور بنداز", onClick = vm::discard, filled = false, color = PinglyColors.InkSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                    }
                }
                SectionTitle("صداهای ذخیره‌شده")
                if (voices.isEmpty()) {
                    EmptyState("هنوز صدایی نداری", "اولین صدات رو ضبط کن؛ مثلاً یه «دلم برات تنگ شده» با صدای خودت 🥰", Modifier.fillMaxWidth().padding(vertical = 16.dp))
                }
                voices.forEach { item ->
                    VoiceRow(
                        item = item,
                        partnerName = couple.partnerName,
                        playing = playing == item.id,
                        busy = busy,
                        onPlay = { vm.play(item) },
                        onSend = { vm.sendToPartner(item) },
                        onDelete = { deleting = item.id },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    deleting?.let { id ->
        ConfirmDialog(
            title = "این صدا پاک بشه؟",
            text = "از روی سرور و گوشی هر دوتون پاک می‌شه.",
            confirm = "پاکش کن",
            destructive = true,
            onConfirm = {
                voices.firstOrNull { it.id == id }?.let(vm::delete)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val scale by pulse.animateFloat(1f, if (recording) 1.12f else 1.04f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s")
    Surface(
        modifier = Modifier.size(96.dp).graphicsLayer { scaleX = scale; scaleY = scale }.bouncyClick(onClick = onClick),
        shape = CircleShape,
        color = if (recording) PinglyColors.Danger else PinglyColors.Pink,
        shadowElevation = 10.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (recording) "⏹" else "🎙️", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        }
    }
}

@Composable
private fun RecordingTimer(startedAt: Long) {
    var now by androidx.compose.runtime.remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    Text(
        "${Persian.duration(now - startedAt)} / ${Persian.duration(VoiceRecorder.MAX_MS.toLong())}",
        style = MaterialTheme.typography.titleMedium,
        color = PinglyColors.Danger,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Waveform(levels: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val bars = 36
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val level = levels.getOrNull(i - (bars - levels.size)) ?: 0.05f
            val h = size.height * level
            drawRoundRect(
                if (i % 2 == 0) PinglyColors.Pink else PinglyColors.Lilac,
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

@Composable
private fun VoiceRow(
    item: MediaItem,
    partnerName: String,
    playing: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 3.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp).bouncyClick(onClick = onPlay), shape = CircleShape, color = if (playing) PinglyColors.Lilac else PinglyColors.LilacMist) {
                    Box(contentAlignment = Alignment.Center) { Text(if (playing) "⏸" else "▶", style = MaterialTheme.typography.titleLarge, color = if (playing) Color.White else PinglyColors.LilacDeep) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            item.locked -> "🔒 صدای رمزشده"
                            else -> item.title ?: "صدای بی‌نام"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = PinglyColors.Ink,
                    )
                    Text(
                        listOfNotNull(
                            if (item.mine) "ضبط خودت" else "از طرف $partnerName",
                            item.durationMs?.let { Persian.duration(it) },
                            Persian.relative(item.createdAt),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = PinglyColors.InkSoft,
                    )
                }
            }
            if (item.mine && !item.locked) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("🔊 پخش روی گوشی $partnerName", onClick = onSend, enabled = !busy)
                    PillButton("🗑", onClick = onDelete, filled = false, color = PinglyColors.InkSoft)
                }
            }
        }
    }
}
