package com.lkaesberg.mensaapp

import com.lkaesberg.mensaapp.data.CanteenInfo
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.UserRole
import com.lkaesberg.mensaapp.notifications.NotificationScheduler
import com.russhwolf.settings.Settings
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

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
            val list = repository.getCanteens()
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
