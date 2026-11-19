package ir.byalireza.pingly.ui.gallery

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import ir.byalireza.pingly.AppContainer
import ir.byalireza.pingly.PinglyApp
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.data.repo.MediaItem
import ir.byalireza.pingly.data.repo.MediaKind
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.ChipItem
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.ConfirmDialog
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.FilterChips
import ir.byalireza.pingly.ui.components.LargeHeader
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.StatusChip
import ir.byalireza.pingly.ui.components.TonalButton
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Downscales a picked photo to ≤1600 px and re-encodes it as JPEG (EXIF rotation applied on API 28+). */
object ImageCompressor {
    private const val MAX_DIMENSION = 1600

    fun compress(context: Context, uri: Uri, quality: Int = 85): ByteArray {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = MAX_DIMENSION / max(w, h).toFloat()
                if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("decode failed")
            val scale = MAX_DIMENSION / max(decoded.width, decoded.height).toFloat()
            if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true) else decoded
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }
}

class GalleryViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val images = container.media.images

    init {
        viewModelScope.launch { runCatching { container.media.refresh(MediaKind.Image) } }
    }

    fun upload(uris: List<Uri>) = action(success = if (uris.size > 1) "عکس‌ها به گالری اضافه شدن" else "عکس به گالری اضافه شد") {
        for (uri in uris) {
            val bytes = withContext(Dispatchers.Default) { ImageCompressor.compress(getApplication(), uri) }
            container.media.upload(MediaKind.Image, bytes, title = null, durationMs = null)
        }
    }

    fun showOnPartnerPhone(item: MediaItem) = action(success = "عکس تمام‌صفحه روی گوشیش باز می‌شه") {
        container.events.send(EventType.Image, meta = buildJsonObject { put("mediaId", item.id) })
    }

    fun delete(item: MediaItem, onDone: () -> Unit) = action(success = "عکس پاک شد") {
        container.media.delete(item)
        onDone()
    }
}

/** Loads (and if needed downloads + decrypts) a media file for display. */
@Composable
fun rememberMediaFile(container: AppContainer, id: String): Result<File>? {
    val state by produceState<Result<File>?>(null, id) { value = runCatching { container.media.file(id) } }
    return state
}

@Composable
fun GalleryScreen(bottomInset: Dp, onBack: (() -> Unit)?, vm: GalleryViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val images by vm.images.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    val container = (LocalContext.current.applicationContext as PinglyApp).container
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) vm.upload(uris)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val shown = when (filter) {
        1 -> images.filter { it.mine }
        2 -> images.filter { !it.mine }
        else -> images
    }

    Column(Modifier.fillMaxSize().background(PinglyColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            LargeHeader(
                title = "گالری ما",
                subtitle = "عکس‌های رمزنگاری‌شده، فقط برای شما دو نفر",
                leading = onBack?.let { back -> @Composable { CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "بازگشت", back) } },
            ) {
                CircleIconButton(Icons.Rounded.AddPhotoAlternate, "افزودن عکس", pick, background = PinglyColors.Ink, tint = Color.White, bordered = false)
            }
            FilterChips(
                items = listOf(
                    ChipItem("همه", images.size),
                    ChipItem("از من", images.count { it.mine }),
                    ChipItem("از ${couple.partnerName}", images.count { !it.mine }),
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomInset),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) {
                item {
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(PinglyColors.SurfaceMuted), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = PinglyColors.Ink, strokeWidth = 2.5.dp)
                    }
                }
            }
            if (shown.isEmpty() && !busy) {
                item(span = { GridItemSpan(3) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            Icons.Rounded.PhotoLibrary, "هنوز عکسی اینجا نیست",
                            "یه عکس قشنگ اضافه کن تا هر وقت خواستی تمام‌صفحه روی گوشی ${couple.partnerName} نشونش بدی.",
                            tint = PinglyColors.Mint, background = PinglyColors.MintTint,
                        )
                        TonalButton("افزودن عکس", onClick = pick, icon = Icons.Rounded.AddPhotoAlternate, modifier = Modifier.padding(horizontal = 40.dp))
                    }
                }
            }
            items(shown, key = { it.id }) { item -> Thumbnail(container, item, onClick = { viewing = item.id }) }
        }
    }

    viewing?.let { id ->
        val item = images.firstOrNull { it.id == id }
        if (item == null) {
            viewing = null
        } else {
            Viewer(
                container = container,
                item = item,
                partnerName = couple.partnerName,
                busy = busy,
                onShow = { vm.showOnPartnerPhone(item) },
                onDelete = { deleting = item.id },
                onDismiss = { viewing = null },
            )
        }
    }
    deleting?.let { id ->
        ConfirmDialog(
            icon = Icons.Rounded.DeleteOutline,
            title = "این عکس پاک بشه؟",
            text = "از گالری هر دوتون پاک می‌شه.",
            confirm = "پاک کن",
            destructive = true,
            onConfirm = {
                images.firstOrNull { it.id == id }?.let { vm.delete(it) { viewing = null } }
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Thumbnail(container: AppContainer, item: MediaItem, onClick: () -> Unit) {
    val file = rememberMediaFile(container, item.id)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(PinglyColors.SurfaceMuted)
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            file == null -> CircularProgressIndicator(Modifier.size(22.dp), color = PinglyColors.InkFaint, strokeWidth = 2.dp)
            file.isFailure -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = PinglyColors.InkFaint)
            else -> AsyncImage(file.getOrNull(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Viewer(
    container: AppContainer,
    item: MediaItem,
    partnerName: String,
    busy: Boolean,
    onShow: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val file = rememberMediaFile(container, item.id)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(32.dp)).background(PinglyColors.Surface).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    if (item.mine) "اضافه‌شده توسط تو" else "از $partnerName",
                    color = PinglyColors.InkSoft, background = PinglyColors.SurfaceMuted,
                )
                Spacer(Modifier.weight(1f))
                Text(Persian.relative(item.createdAt), style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkFaint)
                Spacer(Modifier.size(8.dp))
                CircleIconButton(Icons.Rounded.Close, "بستن", onDismiss, size = 36.dp, background = PinglyColors.SurfaceMuted, bordered = false)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(24.dp)).background(PinglyColors.Ink), contentAlignment = Alignment.Center) {
                when {
                    file == null -> CircularProgressIndicator(color = PinglyColors.PinkSoft)
                    file.isFailure -> Text("این عکس با کلید گوشی قبلی رمز شده", color = Color.White, textAlign = TextAlign.Center)
                    else -> AsyncImage(file.getOrNull(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "نمایش روی گوشی $partnerName", onClick = onShow, icon = Icons.Rounded.Fullscreen,
                loading = busy, enabled = file?.isSuccess == true,
            )
            if (item.mine) {
                Spacer(Modifier.height(8.dp))
                TonalButton("حذف عکس", onClick = onDelete, icon = Icons.Rounded.DeleteOutline, tint = PinglyColors.Danger, background = PinglyColors.DangerTint)
            }
        }
    }
}
