package ir.byalireza.pingly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.ui.theme.PinglyColors

enum class MainTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("home", "خانه", Icons.Outlined.Home, Icons.Rounded.Home),
    History("history", "تاریخچه", Icons.Outlined.History, Icons.Rounded.History),
    Gallery("gallery", "گالری", Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary),
    Settings("settings", "تنظیمات", Icons.Outlined.Settings, Icons.Rounded.Settings);

    companion object {
        fun fromRoute(route: String?): MainTab? = entries.firstOrNull { it.route == route }
    }
}

/** Height the bar occupies above the navigation bar, for content padding. */
val BottomBarHeight = 76.dp

/**
 * White tab bar with a raised navy heart in the middle — tapping it pings the partner from
 * anywhere. `pingKey` changes after every successful ping to play a little bounce.
 */
@Composable
fun PinglyBottomBar(current: MainTab?, onSelect: (MainTab) -> Unit, onPing: () -> Unit, pingEnabled: Boolean, pingKey: Int) {
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(pingKey) {
        if (pingKey > 0) {
            bounce.snapTo(1.25f)
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = PinglyColors.Ink.copy(alpha = 0.2f))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(PinglyColors.Surface)
                .navigationBarsPadding()
                .height(BottomBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tabs = MainTab.entries
            TabItem(tabs[0], current == tabs[0], onSelect, Modifier.weight(1f))
            TabItem(tabs[1], current == tabs[1], onSelect, Modifier.weight(1f))
            Spacer(Modifier.width(76.dp))
            TabItem(tabs[2], current == tabs[2], onSelect, Modifier.weight(1f))
            TabItem(tabs[3], current == tabs[3], onSelect, Modifier.weight(1f))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(66.dp)
                .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
                .shadow(14.dp, CircleShape, spotColor = PinglyColors.Pink)
                .clip(CircleShape)
                .background(if (pingEnabled) PinglyColors.Ink else PinglyColors.InkFaint)
                .border(4.dp, PinglyColors.Surface, CircleShape)
                .bouncyClick(enabled = pingEnabled, onClick = onPing),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, contentDescription = "پینگ", tint = PinglyColors.PinkSoft, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TabItem(tab: MainTab, selected: Boolean, onSelect: (MainTab) -> Unit, modifier: Modifier) {
    val color by animateColorAsState(if (selected) PinglyColors.Ink else PinglyColors.InkFaint, label = "tab")
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).bouncyClick { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(width = if (selected) 16.dp else 0.dp, height = 3.dp).clip(CircleShape).background(if (selected) PinglyColors.Pink else Color.Transparent))
    }
}
