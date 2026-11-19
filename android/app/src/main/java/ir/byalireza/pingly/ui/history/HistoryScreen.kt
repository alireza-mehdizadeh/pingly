package ir.byalireza.pingly.ui.history

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.ChipItem
import ir.byalireza.pingly.ui.components.CircleIconButton
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.FilterChips
import ir.byalireza.pingly.ui.components.LargeHeader
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.RowDivider
import ir.byalireza.pingly.ui.home.EventRow
import ir.byalireza.pingly.ui.theme.PinglyColors
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : BaseViewModel(app) {
    val couple = container.account.state
    val history = container.events.history
    val hasMore = container.events.hasMore

    fun refresh() = action { container.events.refresh() }

    fun loadMore() {
        viewModelScope.launch { runCatching { container.events.loadMore() } }
    }
}

/** Every ping, note, sound and answer between the two of you, grouped by day. */
@Composable
fun HistoryScreen(bottomInset: Dp, onOpen: (PinglyEvent) -> Unit, vm: HistoryViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val history by vm.history.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    LaunchedEffect(Unit) { vm.refresh() }

    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, hasMore) { if (nearEnd && hasMore && history.isNotEmpty()) vm.loadMore() }

    val shown = remember(history, filter) {
        when (filter) {
            1 -> history.filter { !it.mine }
            2 -> history.filter { it.mine }
            else -> history
        }
    }
    val groups = remember(shown) { shown.groupBy { Persian.dayLabel(it.createdAt) }.toList() }

    Column(Modifier.fillMaxSize().background(PinglyColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            LargeHeader("تاریخچه", subtitle = "همه‌ی لحظه‌های تو و ${couple.partnerName}") {
                CircleIconButton(Icons.Rounded.Refresh, "به‌روزرسانی", vm::refresh)
            }
            FilterChips(
                items = listOf(
                    ChipItem("همه", history.size),
                    ChipItem("دریافتی", history.count { !it.mine }),
                    ChipItem("فرستاده", history.count { it.mine }),
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp + bottomInset),
        ) {
            if (shown.isEmpty() && !busy) {
                item {
                    EmptyState(
                        Icons.Rounded.History, "هنوز چیزی اینجا نیست",
                        "هر پینگ، صدا، عکس و پیامی که بین شما رد و بدل بشه اینجا می‌مونه.",
                    )
                }
            }
            groups.forEach { (day, events) ->
                item(key = "h-$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = PinglyColors.InkFaint, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(day, style = MaterialTheme.typography.labelMedium, color = PinglyColors.InkSoft)
                    }
                }
                item(key = "g-$day") {
                    PinglyCard(padding = 6.dp) {
                        events.forEachIndexed { index, event ->
                            if (index > 0) RowDivider()
                            EventRow(event, couple.partnerName, onClick = { onOpen(event) })
                            if (event.type == EventType.Question) {
                                history.firstOrNull { it.replyTo == event.id }?.answer?.let { answer ->
                                    Row(Modifier.padding(start = 66.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, tint = PinglyColors.Lilac, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("«$answer»", style = MaterialTheme.typography.bodySmall, color = PinglyColors.Lilac)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
