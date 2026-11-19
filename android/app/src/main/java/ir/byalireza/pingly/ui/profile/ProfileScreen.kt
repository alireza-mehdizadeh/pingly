package ir.byalireza.pingly.ui.profile

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.domain.Avatar
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.Avatar
import ir.byalireza.pingly.ui.components.FieldLabel
import ir.byalireza.pingly.ui.components.IconBadge
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.PinglyScreen
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors

class ProfileViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state

    fun save(name: String, avatar: String, onSaved: () -> Unit) = action {
        container.account.updateProfile(name = name, avatar = avatar)
        onSaved()
    }
}

/** Name + profile colour. Used once after sign-up and later from settings. */
@Composable
fun ProfileScreen(firstTime: Boolean, onDone: () -> Unit, onBack: (() -> Unit)? = null, vm: ProfileViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    var name by rememberSaveable { mutableStateOf(couple.me?.displayName.orEmpty()) }
    var avatar by rememberSaveable { mutableStateOf(couple.me?.avatar ?: Avatar.Bunny.id) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }

    PinglyScreen(
        title = if (firstTime) null else "ویرایش پروفایل",
        onBack = onBack,
        background = if (firstTime) PinglyColors.Surface else PinglyColors.Background,
        bottom = {
            PrimaryButton(
                if (firstTime) "ادامه" else "ذخیره‌ی تغییرات",
                onClick = { vm.save(name, avatar, onDone) },
                icon = if (firstTime) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check,
                loading = busy,
                enabled = name.isNotBlank(),
            )
        },
    ) {
        if (firstTime) {
            Spacer(Modifier.height(32.dp))
            Text("خوش اومدی!", style = MaterialTheme.typography.headlineLarge, color = PinglyColors.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "اسمت و یه رنگ برای پروفایلت انتخاب کن؛ پارتنرت تو رو این‌طوری می‌بینه.",
                style = MaterialTheme.typography.bodyLarge,
                color = PinglyColors.InkSoft,
            )
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name.ifBlank { null }, avatar, size = 112.dp, ring = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                name.ifBlank { "اسمت اینجا نشون داده می‌شه" },
                style = MaterialTheme.typography.titleLarge,
                color = if (name.isBlank()) PinglyColors.InkFaint else PinglyColors.Ink,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
        PinglyTextField(name, { name = it }, placeholder = "مثلاً زینب", label = "اسمت", icon = Icons.Rounded.Person, maxLength = 32)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Palette, PinglyColors.Lilac, PinglyColors.LilacTint, size = 30.dp)
            FieldLabel("رنگ پروفایل", Modifier.padding(start = 6.dp, top = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        PinglyCard(padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar.entries.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { option ->
                            val selected = option.id == avatar
                            val scale by animateFloatAsState(if (selected) 1.1f else 1f, label = "sel")
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .scale(scale)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(option.colors))
                                    .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                    .bouncyClick { avatar = option.id },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
