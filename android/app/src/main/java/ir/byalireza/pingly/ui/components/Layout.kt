package ir.byalireza.pingly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.byalireza.pingly.ui.theme.PinglyColors

/**
 * Standard screen frame: system-bar insets, an optional centered top bar, scrollable content
 * with 20dp side margins, and an optional pinned bottom area (usually the primary button).
 */
@Composable
fun PinglyScreen(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    bottomInset: Dp = 0.dp,
    background: Color = PinglyColors.Background,
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (title != null) TopBar(title = title, onBack = onBack, subtitle = subtitle, actions = actions)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp),
        ) {
            content()
            Spacer(Modifier.height(16.dp + bottomInset))
        }
        if (bottom != null) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { bottom() }
    }
}

/** Centered title with a round back button at the start and optional actions at the end. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(if (subtitle != null) 60.dp else 52.dp),
    ) {
        if (onBack != null) {
            CircleIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack, "بازگشت", onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = PinglyColors.InkSoft,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Big start-aligned title for the main tabs ("Home", "History"…). */
@Composable
fun LargeHeader(title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.padding(start = 12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = PinglyColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PinglyColors.InkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

private val CardShape = RoundedCornerShape(24.dp)

/** White card with a hairline border — the basic container. */
@Composable
fun PinglyCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    color: Color = PinglyColors.Surface,
    bordered: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color)
            .then(if (bordered) Modifier.border(1.dp, PinglyColors.Border, CardShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Section title with an optional "see all"-style action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = PinglyColors.Ink, modifier = Modifier.weight(1f))
        if (action != null) LinkButton(action, onAction, icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight)
    }
}

@Composable
fun RowDivider(startInset: Dp = 66.dp) {
    HorizontalDivider(Modifier.padding(start = startInset), thickness = 1.dp, color = PinglyColors.Border)
}

/** Small caption above a field or group. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = PinglyColors.Ink, modifier = modifier.padding(start = 4.dp, bottom = 8.dp))
}
