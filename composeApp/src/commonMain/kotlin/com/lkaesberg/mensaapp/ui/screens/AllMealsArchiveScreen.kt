package com.lkaesberg.mensaapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.components.DietPip
import com.lkaesberg.mensaapp.ui.components.Plate
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun AllMealsArchiveScreen(
    state: MealsAppState,
    onBack: () -> Unit,
) {
    val palette = MensaTheme.palette
    val scope = rememberCoroutineScope()
    val history by state.history.collectAsState()
    val canteen by state.selectedCanteen.collectAsState()
    var query by remember { mutableStateOf("") }

    // Fire on first composition AND on canteen change. The stable Unit key
    // also forces a reload every time we re-enter the archive after a
    // screen change — `state.history` is shared with FavoritesScreen and
    // DishStatsScreen which use a 90-day window, and we want the archive
    // to always start with the full 365-day data set.
    LaunchedEffect(canteen?.id, Unit) {
        if (canteen != null) state.loadHistoryForSelected(scope, sinceDays = 365)
    }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    // Defensive past-only filter at the screen level too. The repo already
    // filters `served_on < today`, but if state.history is stale from another
    // screen with a wider window, we'd otherwise count today/future
    // occurrences in this aggregation.
    val pastHistory = remember(history, today) {
        history.mapNotNull { md ->
            val date = runCatching { LocalDate.parse(md.servedOn) }.getOrNull() ?: return@mapNotNull null
            if (date.toEpochDays() < today.toEpochDays()) md to date else null
        }
    }
    val byTitle = remember(pastHistory) {
        pastHistory.groupBy { (md, _) -> md.meals?.cleanTitle ?: md.meals?.title.orEmpty() }
            .filter { it.key.isNotBlank() }
            .map { (title, pairs) ->
                val mostRecent = pairs.maxByOrNull { (_, d) -> d.toEpochDays() }
                val (md, mostRecentDate) = mostRecent ?: pairs.first()
                ArchiveItem(
                    title = title,
                    icons = md.meals?.icons.orEmpty(),
                    times = pairs.size,
                    lastDate = mostRecentDate,
                    md = md,
                )
            }
    }

    val filtered = remember(byTitle, query) {
        if (query.isBlank()) byTitle
        else byTitle.filter { it.title.contains(query, ignoreCase = true) }
    }
    val grouped: List<Pair<String, List<ArchiveItem>>> = remember(filtered) {
        filtered.groupBy { it.title.firstOrNull()?.uppercase() ?: "?" }
            .toList()
            .sortedBy { it.first }
    }

    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(36.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = palette.ink, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Alle Gerichte", color = palette.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                Text("${byTitle.size} jemals serviert · ${canteen?.name.orEmpty()}", color = palette.sub, fontSize = 11.sp)
            }
        }
        // Search field
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(palette.surface)
                .border(1.dp, palette.hair, RoundedCornerShape(100.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = palette.sub, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(palette.forest),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Im Archiv suchen…", color = palette.sub.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(modifier = Modifier.clickable { query = "" }) {
                    Icon(Icons.Filled.Close, null, tint = palette.sub, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (query.isNotBlank()) {
            Text(
                text = "${filtered.size} TREFFER · IM GESAMTEN ARCHIV",
                color = palette.sub,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty() && query.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🔍", fontSize = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text("Nichts gefunden", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("„$query\" wurde nie auf dem Plan gesehen.", color = palette.sub, fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                grouped.forEach { (letter, groupItems) ->
                    item(key = "head-$letter") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.forest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(letter, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(modifier = Modifier.weight(1f).height(1.dp).background(palette.hair))
                            Text("${groupItems.size}", color = palette.sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    groupItems.forEach { entry: ArchiveItem ->
                        item(key = entry.title) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surface)
                                    .border(1.dp, palette.hair, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Plate(meal = entry.md.meals, size = 44.dp, radius = 8.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.title, color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    val ago = entry.lastDate?.let { relativeAgo(today, it) } ?: "—"
                                    val agoColor = if (ago == "heute") palette.open else palette.sub
                                    Text(
                                        text = "${entry.times}× serviert · zuletzt $ago",
                                        color = palette.sub,
                                        fontSize = 10.sp,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    entry.icons.forEach { kind -> DietPip(kind = kind, size = 11.dp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ArchiveItem(
    val title: String,
    val icons: List<String>,
    val times: Int,
    val lastDate: LocalDate?,
    val md: com.lkaesberg.mensaapp.MealDate,
)

internal fun relativeAgo(today: LocalDate, target: LocalDate): String {
    val diff = (today.toEpochDays() - target.toEpochDays()).toInt()
    return when {
        diff < 0 -> "demnächst"  // future — shouldn't happen for past-only history but safe
        diff == 0 -> "heute"
        diff == 1 -> "gestern"
        diff < 7 -> "vor $diff Tagen"
        diff < 14 -> "vor 1 Woche"
        diff < 30 -> "vor ${diff / 7} Wochen"
        diff < 60 -> "vor 1 Monat"
        else -> "vor ${diff / 30} Monaten"
    }
}
