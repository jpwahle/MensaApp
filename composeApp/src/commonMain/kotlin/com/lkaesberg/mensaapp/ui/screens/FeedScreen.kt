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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.mealMatchesDietaryFilters
import com.lkaesberg.mensaapp.separateMealsByPeriod
import com.lkaesberg.mensaapp.shouldHideAfternoonMealsForCanteenOnDate
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.PriceResolver
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.components.DateChip
import com.lkaesberg.mensaapp.ui.components.EmptyState
import com.lkaesberg.mensaapp.ui.components.FilterChipsRow
import com.lkaesberg.mensaapp.ui.components.MealCard
import com.lkaesberg.mensaapp.ui.components.TimeSeparator
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

@Composable
fun FeedScreen(
    state: MealsAppState,
    onOpenCanteenPicker: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenMealDetail: (MealDate) -> Unit,
) {
    val palette = MensaTheme.palette
    val scope = rememberCoroutineScope()
    val canteens by state.canteens.collectAsState()
    val selectedCanteen by state.selectedCanteen.collectAsState()
    val mealsByDate by state.mealsByDate.collectAsState()
    val favoriteIds by state.favoritesManager.favorites.collectAsState()
    val userRole by state.userRole.collectAsState()
    val selectedFilters = remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) { state.loadCanteens(scope) }

    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember(Clock.System.todayIn(tz)) { Clock.System.todayIn(tz) }
    val weekDates = remember(today) {
        // Anchor at Monday of the current week, expose 5 weekdays.
        val mondayOffset = (today.dayOfWeek.isoDayNumber - 1).coerceAtLeast(0)
        val monday = today.minus(mondayOffset, DateTimeUnit.DAY)
        (0 until 5).map { monday.plus(it, DateTimeUnit.DAY) }
    }
    var selectedDate by remember { mutableStateOf(weekDates.firstOrNull { it >= today } ?: today) }

    val canteenInfo = remember(selectedCanteen) {
        selectedCanteen?.name?.let { CanteenStaticData.matchFor(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.paper)
    ) {
        TopBar(
            canteenName = selectedCanteen?.name ?: "—",
            onMenuClick = onOpenMenu,
            onCanteenClick = onOpenCanteenPicker,
            onSearchClick = onOpenSearch,
            onNotificationClick = onOpenNotifications,
            unreadDot = (favoriteIds.isNotEmpty()),
        )
        StatusRow(
            canteenInfo = canteenInfo,
            mealCount = mealsByDate[selectedDate]?.size ?: 0,
        )
        Spacer(Modifier.height(8.dp))
        DateStrip(
            dates = weekDates,
            selected = selectedDate,
            today = today,
            mealsByDate = mealsByDate,
            favoriteIds = favoriteIds,
            onSelect = { selectedDate = it },
        )
        Spacer(Modifier.height(8.dp))
        FilterChipsRow(
            selected = selectedFilters.value,
            onToggle = { kind ->
                selectedFilters.value = if (kind in selectedFilters.value)
                    selectedFilters.value - kind else selectedFilters.value + kind
            }
        )
        Spacer(Modifier.height(4.dp))

        val buckets = remember(mealsByDate, selectedDate, selectedFilters.value, selectedCanteen?.id) {
            val all = mealsByDate[selectedDate].orEmpty()
                .filter { mealMatchesDietaryFilters(it, selectedFilters.value) }
            val hideAfternoon = shouldHideAfternoonMealsForCanteenOnDate(selectedCanteen, all)
            separateMealsByPeriod(all, hideAfternoon)
        }
        val lunchMeals = buckets.lunch
        val afternoonMeals = buckets.afternoon
        val activeMeals = remember(buckets) { (lunchMeals + afternoonMeals).distinctBy { it.id } }

        if (activeMeals.isEmpty()) {
            EmptyState(
                title = "Heute steht noch nichts auf dem Plan.",
                subtitle = "Versuche es später noch einmal oder wähle einen anderen Tag.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (lunchMeals.isNotEmpty()) {
                    item(key = "sep-lunch") {
                        TimeSeparator(label = "MITTAG · 11:30 – 14:30")
                    }
                    items(lunchMeals, key = { "lunch-${it.id}" }) { md ->
                        FeedMealCard(state, md, favoriteIds, userRole, onOpenMealDetail)
                    }
                }
                if (afternoonMeals.isNotEmpty()) {
                    item(key = "sep-afternoon") {
                        TimeSeparator(label = "NACHMITTAG · 14:30 – 16:30")
                    }
                    items(afternoonMeals, key = { "afternoon-${it.id}" }) { md ->
                        FeedMealCard(state, md, favoriteIds, userRole, onOpenMealDetail)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedMealCard(
    state: MealsAppState,
    md: MealDate,
    favoriteIds: Set<String>,
    userRole: com.lkaesberg.mensaapp.data.UserRole,
    onOpenMealDetail: (MealDate) -> Unit,
) {
    val enriched = remember(md.id) { MealEnrichment.enrich(md) }
    val key = enriched.cleanTitle.ifBlank { md.meals?.title ?: md.mealId }
    val isFav = key in favoriteIds || (md.meals?.title ?: "") in favoriteIds
    val info = state.selectedInfo()
    val resolved = PriceResolver.resolve(
        mealCategory = md.category,
        dbPrices = state.canteenPrices.value,
        fallbacks = info?.fallbackPrices.orEmpty(),
    )
    val priceText = resolved?.textFor(userRole)?.takeIf { it.isNotBlank() }
    MealCard(
        mealDate = md,
        isFavorite = isFav,
        onClick = { onOpenMealDetail(md) },
        onFavoriteToggle = {
            // Toggle by clean title so the same dish stays starred across visits.
            state.favoritesManager.toggleFavorite(key)
        },
        priceText = priceText,
        favoriteHint = null,
        enriched = enriched,
    )
}

@Composable
private fun TopBar(
    canteenName: String,
    onMenuClick: () -> Unit,
    onCanteenClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    unreadDot: Boolean,
) {
    val palette = MensaTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.moss)
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Menu, null, tint = palette.forestDark, modifier = Modifier.size(20.dp))
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(palette.moss)
                .clickable { onCanteenClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = canteenName,
                color = palette.forestDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = palette.forest, modifier = Modifier.size(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clickable { onSearchClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, null, tint = palette.forest, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier.size(40.dp).clickable { onNotificationClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Notifications, null, tint = palette.forest, modifier = Modifier.size(20.dp))
                if (unreadDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp, top = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(palette.amber)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    canteenInfo: com.lkaesberg.mensaapp.data.CanteenInfo?,
    mealCount: Int,
) {
    val palette = MensaTheme.palette
    val isOpen = canteenInfo?.let { CanteenStaticData.openNow(it) } ?: false
    val closesAt = canteenInfo?.let { CanteenStaticData.closesAt(it) }
    val statusText = if (isOpen && closesAt != null) "Geöffnet · bis $closesAt" else "Geschlossen"
    val statusColor = if (isOpen) palette.open else palette.closed
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor),
        )
        Text(
            text = statusText,
            color = palette.sub,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (mealCount > 0) {
            Text("·", color = palette.sub.copy(alpha = 0.5f), fontSize = 12.sp)
            Text("$mealCount Gerichte", color = palette.sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DateStrip(
    dates: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    mealsByDate: Map<LocalDate, List<MealDate>>,
    favoriteIds: Set<String>,
    onSelect: (LocalDate) -> Unit,
) {
    val labels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dates.forEach { d ->
            val label = labels[(d.dayOfWeek.isoDayNumber - 1).coerceAtLeast(0).coerceAtMost(6)]
            val hasFav = mealsByDate[d].orEmpty().any { md ->
                val key = MealEnrichment.enrich(md).cleanTitle.ifBlank { md.meals?.title ?: "" }
                key in favoriteIds || (md.meals?.title ?: "") in favoriteIds
            }
            DateChip(
                label = label,
                day = d.dayOfMonth,
                isToday = d == today,
                isSelected = d == selected,
                hasFavorite = hasFav,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(d) },
            )
        }
    }
}
