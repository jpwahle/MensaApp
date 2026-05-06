package com.lkaesberg.mensaapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.Canteen
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.containsFavorite
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
import com.lkaesberg.mensaapp.ui.components.OccupancyChip
import com.lkaesberg.mensaapp.ui.components.TimeSeparator
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

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
    // Date strip at the top: rolling next-6-days window starting today.
    // Includes weekends — the menu API serves Saturday plans for some
    // mensas, and excluding them (the old Mon–Fri behaviour) hid the
    // weekend slots entirely.
    val nextSix = remember(today) {
        (0 until 6).map { today.plus(it, DateTimeUnit.DAY) }
    }
    // Pager covers a wider window so the calendar picker can jump to any
    // day the scraper has data for (the long-mode scrape pulls ~30 days
    // ahead). Sort by epoch days so the resulting indexes map cleanly.
    val datesWithData: Set<LocalDate> = remember(mealsByDate.keys, today) {
        mealsByDate.keys.filter { it.toEpochDays() >= today.toEpochDays() }.toSet()
    }
    val allDates: List<LocalDate> = remember(nextSix, datesWithData) {
        (nextSix + datesWithData).distinct().sortedBy { it.toEpochDays() }
    }
    val initialPage = remember(allDates, today) {
        allDates.indexOf(today).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { allDates.size }
    val selectedDate = allDates.getOrElse(pagerState.currentPage) { today }

    val canteenInfo = remember(selectedCanteen) {
        selectedCanteen?.name?.let { CanteenStaticData.matchFor(it) }
    }
    val isCanteenPastClosing = selectedCanteen?.let { state.pastClosingTime(it) } ?: false

    // Once per session per canteen: if the canteen is already closed for today,
    // jump to tomorrow so the default view is the upcoming day. The user can
    // still swipe / tap back to today.
    var autoBumpedForCanteen by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedCanteen?.id, isCanteenPastClosing) {
        val canteenId = selectedCanteen?.id ?: return@LaunchedEffect
        if (autoBumpedForCanteen == canteenId) return@LaunchedEffect
        autoBumpedForCanteen = canteenId
        if (isCanteenPastClosing && pagerState.currentPage == allDates.indexOf(today)) {
            val tomorrow = today.plus(1, DateTimeUnit.DAY)
            val tomorrowEpoch = tomorrow.toEpochDays()
            val targetIdx = allDates.indexOfFirst { it.toEpochDays() >= tomorrowEpoch }
            if (targetIdx >= 0) pagerState.scrollToPage(targetIdx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.paper)
    ) {
        var showDatePicker by remember { mutableStateOf(false) }
        TopBar(
            canteenName = selectedCanteen?.name ?: "—",
            onMenuClick = onOpenMenu,
            onCanteenClick = onOpenCanteenPicker,
            onSearchClick = onOpenSearch,
            onNotificationClick = onOpenNotifications,
            onCalendarClick = { showDatePicker = true },
            unreadDot = (favoriteIds.isNotEmpty()),
        )
        StatusRow(
            state = state,
            canteen = selectedCanteen,
            mealCount = mealsByDate[selectedDate]?.size ?: 0,
        )
        Spacer(Modifier.height(8.dp))
        DateStrip(
            dates = nextSix,
            selected = selectedDate,
            today = today,
            mealsByDate = mealsByDate,
            favoriteIds = favoriteIds,
            onSelect = { d ->
                val idx = allDates.indexOf(d).coerceAtLeast(0)
                scope.launch { pagerState.animateScrollToPage(idx) }
            },
        )
        Spacer(Modifier.height(6.dp))
        SelectedDateHeader(date = selectedDate, today = today)
        if (showDatePicker) {
            DatePickerSheet(
                today = today,
                selected = selectedDate,
                datesWithData = datesWithData,
                tz = tz,
                onDismiss = { showDatePicker = false },
                onPick = { picked ->
                    showDatePicker = false
                    val idx = allDates.indexOf(picked)
                    if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        FilterChipsRow(
            selected = selectedFilters.value,
            onToggle = { kind ->
                selectedFilters.value = if (kind in selectedFilters.value)
                    selectedFilters.value - kind else selectedFilters.value + kind
            }
        )
        Spacer(Modifier.height(4.dp))

        // Subscribe so hours loading triggers recomposition of the page below.
        val canteenHoursMap by state.canteenHours.collectAsState()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIdx ->
            val pageDate = allDates.getOrElse(pageIdx) { today }
            val isBeforeOpening = selectedCanteen?.let { state.beforeOpeningToday(it) } ?: false
            val pageBuckets = remember(mealsByDate, pageDate, selectedFilters.value, selectedCanteen?.id) {
                // Note: we deliberately do *not* hide DB-deactivated rows on
                // today before opening any more. The scraper's deactivation
                // pass races with the upstream's morning publish, so the
                // entire day's menu can carry `deactivated_at` even though it
                // will be served. Filtering here would empty the screen
                // before lunch. The `keepTodayActive` flag below handles the
                // visual side: those rows render at full opacity until the
                // canteen actually closes.
                val all = mealsByDate[pageDate].orEmpty()
                    .filter { mealMatchesDietaryFilters(it, selectedFilters.value) }
                val hideAfternoon = shouldHideAfternoonMealsForCanteenOnDate(selectedCanteen, all)
                separateMealsByPeriod(all, hideAfternoon)
            }
            val lunchMeals = pageBuckets.lunch
            val afternoonMeals = pageBuckets.afternoon
            val activeMeals = (lunchMeals + afternoonMeals).distinctBy { it.id }
            // Only treat today as "closed" once the canteen has actually opened
            // and is now past closing — before opening, the menu is still
            // mutable upstream so dimming the cards would be misleading.
            val showAsClosed = pageDate == today && isCanteenPastClosing && !isBeforeOpening
            // While the canteen is open today, force every card to render
            // active even if its `deactivated_at` is set. The scraper's
            // deactivation pass can race with the upstream's morning publish,
            // leaving real menu items flagged stale until the next scrape —
            // dimming them mid-service is misleading. After close we restore
            // the dimmed-history behaviour.
            val keepTodayActive = pageDate == today && !isCanteenPastClosing

            if (activeMeals.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    EmptyState(
                        title = "Heute steht noch nichts auf dem Plan.",
                        subtitle = "Versuche es später noch einmal oder wähle einen anderen Tag.",
                    )
                }
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
                            FeedMealCard(
                                state = state,
                                md = md,
                                favoriteIds = favoriteIds,
                                userRole = userRole,
                                showAsClosed = showAsClosed,
                                forceActive = keepTodayActive,
                                onOpenMealDetail = onOpenMealDetail,
                            )
                        }
                    }
                    if (afternoonMeals.isNotEmpty()) {
                        item(key = "sep-afternoon") {
                            TimeSeparator(label = "NACHMITTAG · 14:30 – 16:30")
                        }
                        items(afternoonMeals, key = { "afternoon-${it.id}" }) { md ->
                            FeedMealCard(
                                state = state,
                                md = md,
                                favoriteIds = favoriteIds,
                                userRole = userRole,
                                showAsClosed = showAsClosed,
                                forceActive = keepTodayActive,
                                onOpenMealDetail = onOpenMealDetail,
                            )
                        }
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
    showAsClosed: Boolean,
    forceActive: Boolean = false,
    onOpenMealDetail: (MealDate) -> Unit,
) {
    val enriched = remember(md.id) { MealEnrichment.enrich(md) }
    val key = enriched.cleanTitle.ifBlank { md.meals?.title ?: md.mealId }
    val isFav = favoriteIds.containsFavorite(key) || favoriteIds.containsFavorite(md.meals?.title ?: "")
    val info = state.selectedInfo()
    val resolved = PriceResolver.forMealDate(md, info)
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
        forceDeactivated = showAsClosed,
        forceActive = forceActive,
    )
}

@Composable
private fun TopBar(
    canteenName: String,
    onMenuClick: () -> Unit,
    onCanteenClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onCalendarClick: () -> Unit,
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
                modifier = Modifier.size(40.dp).clickable { onCalendarClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = palette.forest, modifier = Modifier.size(20.dp))
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
    state: MealsAppState,
    canteen: Canteen?,
    mealCount: Int,
) {
    val palette = MensaTheme.palette
    val occupancyMap by state.occupancy.collectAsState()
    val isOpen = canteen?.let { state.openNow(it) } ?: false
    val closesAt = canteen?.let { state.closesAt(it) }
    val s = com.lkaesberg.mensaapp.i18n.LocalStrings.current
    val statusText = if (isOpen && closesAt != null) s.openUntil(closesAt) else s.closedLabel
    val statusColor = if (isOpen) palette.open else palette.closed
    val occupancy = canteen?.id?.let { occupancyMap[it] }
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
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
                Text(s.dishesCount(mealCount), color = palette.sub, fontSize = 12.sp)
            }
        }
        // Occupancy text gets its own line so the long German "Heute weniger
        // los als sonst" label doesn't wrap inside a constrained Row.
        if (canteen != null && isOpen && occupancy != null) {
            OccupancyChip(occupancy = occupancy, isOpen = true)
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
    val labels = com.lkaesberg.mensaapp.i18n.LocalStrings.current.weekdaysShort
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dates.forEach { d ->
            val label = labels[(d.dayOfWeek.isoDayNumber - 1).coerceAtLeast(0).coerceAtMost(6)]
            val hasFav = mealsByDate[d].orEmpty().any { md ->
                val key = MealEnrichment.enrich(md).cleanTitle.ifBlank { md.meals?.title ?: "" }
                favoriteIds.containsFavorite(key) || favoriteIds.containsFavorite(md.meals?.title ?: "")
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

@Composable
private fun SelectedDateHeader(date: LocalDate, today: LocalDate) {
    val palette = MensaTheme.palette
    val s = com.lkaesberg.mensaapp.i18n.LocalStrings.current
    val diff = (date.toEpochDays() - today.toEpochDays()).toInt()
    val weekday = s.weekdaysLong.getOrElse((date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)) { "" }
    val month = s.monthsShort.getOrElse((date.monthNumber - 1).coerceIn(0, 11)) { "" }
    val dayLine = "$weekday, ${date.dayOfMonth}. $month ${date.year}"
    val prefix = when (diff) {
        0 -> s.today
        1 -> s.tomorrow
        -1 -> s.yesterday
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (prefix != null) {
            Text(
                text = prefix.uppercase(),
                color = palette.forestDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
            Text("·", color = palette.sub.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        Text(
            text = dayLine,
            color = palette.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    today: LocalDate,
    selected: LocalDate,
    datesWithData: Set<LocalDate>,
    tz: TimeZone,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    // Convert "available data" dates to UTC midnight epoch millis — Material's
    // DatePicker compares by UTC midnight. We use the system TZ for display.
    val allowedMillis = remember(datesWithData) {
        datesWithData.map {
            it.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }.toSet()
    }
    val initialMillis = remember(selected) {
        selected.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    }
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in allowedMillis
            override fun isSelectableYear(year: Int): Boolean = true
        },
    )
    val s = com.lkaesberg.mensaapp.i18n.LocalStrings.current
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                val picked = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
                    .toLocalDateTime(TimeZone.UTC).date
                onPick(picked)
            }) { Text("OK") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(s.back) }
        },
    ) {
        androidx.compose.material3.DatePicker(state = state)
    }
}

