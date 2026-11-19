package ir.byalireza.pingly.ui.profile

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.domain.Avatar
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.AvatarBubble
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyTextField
import ir.byalireza.pingly.ui.components.PrimaryButton
import ir.byalireza.pingly.ui.components.TopBar
import ir.byalireza.pingly.ui.components.bouncyClick
import ir.byalireza.pingly.ui.theme.PinglyColors

class ProfileViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state

    fun save(name: String, avatar: String, onSaved: () -> Unit) = action {
        container.account.updateProfile(name = name, avatar = avatar)
        onSaved()
    }
}

/** Name + avatar. Used once after sign-up and later from settings. */
@Composable
fun ProfileScreen(firstTime: Boolean, onDone: () -> Unit, onBack: (() -> Unit)? = null, vm: ProfileViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val busy by vm.busy.collectAsState()
    var name by rememberSaveable { mutableStateOf(couple.me?.displayName.orEmpty()) }
    var avatar by rememberSaveable { mutableStateOf(couple.me?.avatar ?: Avatar.Bunny.id) }

    PinglyBackground {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            if (!firstTime) TopBar("پروفایل من", onBack = onBack) else Spacer(Modifier.statusBarsPadding())
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (firstTime) 32.dp else 8.dp))
                AvatarBubble(avatar, size = 110.dp, ring = PinglyColors.PinkSoft)
                Spacer(Modifier.height(16.dp))
                if (firstTime) {
                    Text("خوش اومدی! 🎉", style = MaterialTheme.typography.headlineMedium, color = PinglyColors.Ink)
                    Text(
                        "اسمت و یه آواتار بامزه انتخاب کن؛ پارتنرت تو رو این‌طوری می‌بینه.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PinglyColors.InkSoft,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                }
                PinglyTextField(name, { name = it }, label = "اسمت چیه؟", placeholder = "مثلاً: زینب", maxLength = 32)
                Spacer(Modifier.height(20.dp))
                Text("آواتارت", style = MaterialTheme.typography.titleMedium, color = PinglyColors.Ink, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                AvatarGrid(selected = avatar, onSelect = { avatar = it })
                Spacer(Modifier.height(24.dp))
            }
            PrimaryButton(
                if (firstTime) "ادامه 💞" else "ذخیره",
                onClick = { vm.save(name, avatar, onDone) },
                loading = busy,
                enabled = name.isNotBlank(),
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun AvatarGrid(selected: String, onSelect: (String) -> Unit) {
    val rows = Avatar.entries.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { a ->
                    val isSelected = a.id == selected
                    val scale by animateFloatAsState(if (isSelected) 1.08f else 1f, label = "sel")
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(a.background)
                            .border(if (isSelected) 3.dp else 0.dp, PinglyColors.Pink, CircleShape)
                            .bouncyClick { onSelect(a.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(a.emoji, fontSize = 30.sp)
                    }
                }
            }
        }
    }
}
