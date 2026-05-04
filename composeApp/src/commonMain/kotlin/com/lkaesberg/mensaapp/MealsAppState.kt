package com.lkaesberg.mensaapp

import com.lkaesberg.mensaapp.data.CanteenInfo
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.Locale
import com.lkaesberg.mensaapp.data.UserRole
import com.lkaesberg.mensaapp.notifications.NotificationScheduler
import com.russhwolf.settings.Settings
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Shared, conversation-scoped state for the redesign — meals/canteens/prices
 * caches that screens read via [StateFlow]s. Lives for the lifetime of the
 * Compose tree (not a true ViewModel; KMP-friendly plain class).
 */
class MealsAppState(
    val settings: Settings,
    val favoritesManager: FavoritesManager,
    val notificationScheduler: NotificationScheduler,
) {
    val repository = MealsRepository(SupabaseProvider.client().postgrest)

    private val _canteens = MutableStateFlow<List<Canteen>>(emptyList())
    val canteens: StateFlow<List<Canteen>> = _canteens.asStateFlow()

    private val _selectedCanteen = MutableStateFlow<Canteen?>(null)
    val selectedCanteen: StateFlow<Canteen?> = _selectedCanteen.asStateFlow()

    private val _mealsByDate = MutableStateFlow<Map<LocalDate, List<MealDate>>>(emptyMap())
    val mealsByDate: StateFlow<Map<LocalDate, List<MealDate>>> = _mealsByDate.asStateFlow()

    private val _canteenPrices = MutableStateFlow<List<CanteenPrice>>(emptyList())
    val canteenPrices: StateFlow<List<CanteenPrice>> = _canteenPrices.asStateFlow()

    private val _history = MutableStateFlow<List<MealDate>>(emptyList())
    val history: StateFlow<List<MealDate>> = _history.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _userRole = MutableStateFlow(UserRole.fromKey(settings.getStringOrNull(UserRole.SETTINGS_KEY)))
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    fun setUserRole(role: UserRole) {
        _userRole.value = role
        settings.putString(UserRole.SETTINGS_KEY, role.key)
    }

    private val _locale = MutableStateFlow(Locale.fromTag(settings.getStringOrNull(LOCALE_KEY)))
    val locale: StateFlow<Locale> = _locale.asStateFlow()

    fun setLocale(locale: Locale) {
        _locale.value = locale
        settings.putString(LOCALE_KEY, locale.tag)
    }

    /**
     * Weekly hours pattern keyed by canteen id (Map<canteenId, List<CanteenHours>>).
     * Loaded once at launch via [refreshHoursAndOccupancy].
     */
    private val _canteenHours = MutableStateFlow<Map<String, List<CanteenHours>>>(emptyMap())
    val canteenHours: StateFlow<Map<String, List<CanteenHours>>> = _canteenHours.asStateFlow()

    /** Latest occupancy snapshot per canteen id. */
    private val _occupancy = MutableStateFlow<Map<String, CanteenOccupancy>>(emptyMap())
    val occupancy: StateFlow<Map<String, CanteenOccupancy>> = _occupancy.asStateFlow()

    fun refreshHoursAndOccupancy(scope: CoroutineScope) {
        scope.launch {
            _canteenHours.value = repository.getCanteenHours().groupBy { it.canteenId }
        }
        scope.launch {
            _occupancy.value = repository.getOccupancyLatest().associateBy { it.canteenId }
        }
    }

    /** Canteen IDs the user has hidden from picker / upcoming-favourite lists. */
    private val _disabledCanteenIds = MutableStateFlow(loadDisabledCanteens())
    val disabledCanteenIds: StateFlow<Set<String>> = _disabledCanteenIds.asStateFlow()

    private fun loadDisabledCanteens(): Set<String> {
        val raw = settings.getStringOrNull(DISABLED_CANTEENS_KEY)?.takeIf { it.isNotBlank() }
            ?: return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setCanteenEnabled(canteenId: String, enabled: Boolean) {
        val updated = if (enabled) _disabledCanteenIds.value - canteenId
            else _disabledCanteenIds.value + canteenId
        _disabledCanteenIds.value = updated
        settings.putString(DISABLED_CANTEENS_KEY, updated.joinToString(","))
    }

    private companion object {
        const val DISABLED_CANTEENS_KEY = "disabled_canteens"
        const val LOCALE_KEY = "locale"
    }

    /**
     * (canteen, date) → list of meals served — fetched lazily across all
     * canteens so the FavoritesScreen can surface "next occurrence at any
     * mensa". Loaded by [loadUpcomingAcrossCanteens].
     */
    private val _upcomingAcrossCanteens =
        MutableStateFlow<Map<Pair<Canteen, LocalDate>, List<MealDate>>>(emptyMap())
    val upcomingAcrossCanteens: StateFlow<Map<Pair<Canteen, LocalDate>, List<MealDate>>> =
        _upcomingAcrossCanteens.asStateFlow()

    fun selectedInfo(): CanteenInfo? = selectedCanteen.value?.let { CanteenStaticData.matchFor(it.name) }

    fun loadCanteens(scope: CoroutineScope) {
        scope.launch {
            // Pull all canteens, then drop ones the static metadata doesn't
            // recognise (e.g. cafés synced via mensa-hours-sync that we don't
            // surface in the picker). This keeps Nordmensa-style ghosts off
            // the screen if the row lingers in DB.
            val list = repository.getCanteens().filter { CanteenStaticData.matchFor(it.name) != null }
            _canteens.value = list
            if (_selectedCanteen.value == null) {
                val rememberedSlug = settings.getStringOrNull("selected_canteen_slug")
                val match = list.firstOrNull {
                    rememberedSlug != null && CanteenStaticData.matchFor(it.name)?.slug == rememberedSlug
                } ?: list.firstOrNull { CanteenStaticData.matchFor(it.name)?.slug == "zentral" }
                  ?: list.firstOrNull()
                if (match != null) selectCanteen(scope, match)
            }
        }
        refreshHoursAndOccupancy(scope)
    }

    fun selectCanteen(scope: CoroutineScope, canteen: Canteen) {
        _selectedCanteen.value = canteen
        CanteenStaticData.matchFor(canteen.name)?.let {
            settings.putString("selected_canteen_slug", it.slug)
        }
        loadMealsForSelected(scope)
        loadPricesForSelected(scope)
    }

    fun loadMealsForSelected(scope: CoroutineScope) {
        val canteen = _selectedCanteen.value ?: return
        scope.launch {
            _refreshing.value = true
            _mealsByDate.value = repository.getMealsForCanteen(canteen.id)
            _refreshing.value = false
        }
    }

    fun loadPricesForSelected(scope: CoroutineScope) {
        val canteen = _selectedCanteen.value ?: return
        scope.launch {
            _canteenPrices.value = repository.getCanteenPrices(canteen.id)
        }
    }

    fun loadHistoryForSelected(scope: CoroutineScope, sinceDays: Int = 90) {
        val canteen = _selectedCanteen.value ?: return
        scope.launch {
            _history.value = repository.getMealsHistory(canteen.id, sinceDays)
        }
    }

    fun refreshAll(scope: CoroutineScope) {
        loadCanteens(scope)
        loadMealsForSelected(scope)
        loadPricesForSelected(scope)
    }

    // ─── canteen status (DB hours first, fall back to CanteenStaticData) ───

    private fun nowDt(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private fun parseDbTime(s: String?): LocalTime? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(":")
        return runCatching {
            LocalTime(parts[0].toInt(), parts[1].toInt())
        }.getOrNull()
    }

    private fun dbHoursForToday(canteen: Canteen, dow: DayOfWeek): Pair<LocalTime, LocalTime>? {
        val rows = _canteenHours.value[canteen.id] ?: return null
        val row = rows.firstOrNull { it.dayOfWeek == dow.isoDayNumber } ?: return null
        val open = parseDbTime(row.openTime) ?: return null
        val close = parseDbTime(row.closeTime) ?: return null
        return open to close
    }

    fun openNow(canteen: Canteen): Boolean {
        val now = nowDt()
        dbHoursForToday(canteen, now.date.dayOfWeek)?.let { (open, close) ->
            return now.time >= open && now.time < close
        }
        // Static fallback: e.g. before mensa-hours-sync has run.
        return CanteenStaticData.matchFor(canteen.name)
            ?.let { CanteenStaticData.openNow(it, now) } ?: false
    }

    fun pastClosingTime(canteen: Canteen): Boolean {
        val now = nowDt()
        dbHoursForToday(canteen, now.date.dayOfWeek)?.let { (_, close) ->
            return now.time >= close
        }
        return CanteenStaticData.matchFor(canteen.name)
            ?.let { CanteenStaticData.pastClosingTime(it, now) } ?: true
    }

    fun closesAt(canteen: Canteen): String? {
        val now = nowDt()
        dbHoursForToday(canteen, now.date.dayOfWeek)?.let { (_, close) ->
            return "${close.hour.toString().padStart(2, '0')}:${close.minute.toString().padStart(2, '0')}"
        }
        return CanteenStaticData.matchFor(canteen.name)
            ?.let { CanteenStaticData.closesAt(it, now) }
    }

    /**
     * Fetch the upcoming few days for *every* canteen so the FavoritesScreen
     * can show "next: heute · Zentralmensa" even when the user is browsing a
     * different mensa, and so the notification preview matches the worker's
     * cross-canteen scan.
     */
    fun loadUpcomingAcrossCanteens(scope: CoroutineScope) {
        scope.launch {
            val list = canteens.value.ifEmpty { repository.getCanteens().also { _canteens.value = it } }
            val combined = mutableMapOf<Pair<Canteen, LocalDate>, List<MealDate>>()
            for (canteen in list) {
                val perDate = repository.getMealsForCanteen(canteen.id)
                for ((date, meals) in perDate) {
                    combined[canteen to date] = meals
                }
            }
            _upcomingAcrossCanteens.value = combined
        }
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) getString(key, "") else null
