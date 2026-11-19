package ir.byalireza.pingly.ui.history

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.byalireza.pingly.core.Persian
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.ui.BaseViewModel
import ir.byalireza.pingly.ui.components.EmojiIconButton
import ir.byalireza.pingly.ui.components.EmptyState
import ir.byalireza.pingly.ui.components.LocalToaster
import ir.byalireza.pingly.ui.components.PinglyBackground
import ir.byalireza.pingly.ui.components.PinglyCard
import ir.byalireza.pingly.ui.components.StatusChip
import ir.byalireza.pingly.ui.components.TopBar
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
fun HistoryScreen(onBack: () -> Unit, onOpen: (PinglyEvent) -> Unit, vm: HistoryViewModel = viewModel()) {
    val couple by vm.couple.collectAsState()
    val history by vm.history.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val busy by vm.busy.collectAsState()
    val toaster = LocalToaster.current
    val listState = rememberLazyListState()
    LaunchedEffect(vm) { vm.toasts.collect(toaster::show) }
    LaunchedEffect(Unit) { vm.refresh() }

    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, hasMore) { if (nearEnd && hasMore && history.isNotEmpty()) vm.loadMore() }

    val groups = remember(history) { history.groupBy { Persian.dayLabel(it.createdAt) }.toList() }
    val sent = history.count { it.mine }
    val received = history.size - sent

    PinglyBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar("تاریخچه‌ی ما 🕘", onBack = onBack, subtitle = Persian.digits("$sent فرستادی · $received گرفتی")) {
                EmojiIconButton("🔄", onClick = vm::refresh)
            }
            if (history.isEmpty() && !busy) {
                EmptyState("هنوز چیزی اینجا نیست", "هر پینگ، صدا، عکس و پیامی که بین شما رد و بدل بشه اینجا می‌مونه 💞", Modifier.fillMaxWidth().padding(top = 60.dp))
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                groups.forEach { (day, events) ->
                    item(key = "h-$day") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            StatusChip(day, PinglyColors.LilacMist)
                        }
                    }
                    item(key = "g-$day") {
                        PinglyCard(padding = 8) {
                            events.forEach { event ->
                                EventRow(event, couple.partnerName, onClick = { onOpen(event) })
                                event.takeIf { it.type.name == "Question" }?.let { question ->
                                    history.firstOrNull { it.replyTo == question.id }?.answer?.let { answer ->
                                        Text(
                                            "↳ جواب: «$answer»",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PinglyColors.Pink,
                                            modifier = Modifier.padding(start = 62.dp, bottom = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
