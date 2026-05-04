package com.lkaesberg.mensaapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lkaesberg.mensaapp.FavoritesManager
import com.lkaesberg.mensaapp.MealsRepository
import com.lkaesberg.mensaapp.SupabaseProvider
import com.lkaesberg.mensaapp.containsFavorite
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.util.concurrent.TimeUnit

/**
 * Android implementation backed by WorkManager + NotificationManagerCompat.
 *
 * Setup: [AndroidNotificationContext.attach] must be called from the
 * Application/MainActivity before the first NotificationScheduler is used.
 * Compose Multiplatform's no-arg expect constructor means we can't pass the
 * Context through the type system, so we cache it process-wide.
 */
actual class NotificationScheduler actual constructor() {

    actual fun setEnabled(enabled: Boolean, leadDays: Int, hourOfDay: Int) {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return
        ensureChannel(ctx)
        if (enabled) schedulePeriodic(ctx, leadDays, hourOfDay.coerceIn(0, 23))
        else cancelAllOn(ctx)
    }

    actual fun runCheckNow() {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return
        ensureChannel(ctx)
        val leadDays = SharedPreferencesSettings(
            ctx.getSharedPreferences(com.lkaesberg.mensaapp.APP_PREFS_NAME, Context.MODE_PRIVATE)
        ).getInt("notif_lead_days", 3)
        val req = OneTimeWorkRequestBuilder<FavoriteCheckWorker>()
            .setInputData(workDataOf(KEY_LEAD_DAYS to leadDays))
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }

    actual fun cancelAll() {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return
        cancelAllOn(ctx)
    }

    actual fun sendTestNotification(): Boolean {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return false
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("★ Test-Benachrichtigung")
            .setContentText("So sieht eine Favoriten-Erinnerung aus")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(TEST_NOTIFICATION_ID, notif)
        return true
    }

    private fun cancelAllOn(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
    }

    actual fun isPermitted(): Boolean {
        val ctx = AndroidNotificationContext.contextOrNull() ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
    }

    private fun schedulePeriodic(ctx: Context, leadDays: Int, hourOfDay: Int) {
        val initialDelay = millisUntilNextAt(hourOfDay)
        val req = PeriodicWorkRequestBuilder<FavoriteCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_LEAD_DAYS to leadDays))
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    private fun millisUntilNextAt(hourOfDay: Int): Long {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        val candidate = today.atStartOfDayIn(tz)
            .plus(hourOfDay * 60, DateTimeUnit.MINUTE, tz)
        val target = if (candidate > now) candidate
                     else today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
                         .plus(hourOfDay * 60, DateTimeUnit.MINUTE, tz)
        return target.toEpochMilliseconds() - now.toEpochMilliseconds()
    }

    companion object {
        const val CHANNEL_ID = "favorites_channel"
        const val WORK_NAME = "mensa-favorite-check"
        const val KEY_LEAD_DAYS = "lead_days"
        const val TEST_NOTIFICATION_ID = 1_999

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Favoriten",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "Erinnerungen, wenn ein Lieblingsgericht auf dem Plan steht"
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }
    }
}

/** Process-wide context holder. Initialized from the Activity. */
object AndroidNotificationContext {
    @Volatile private var ctx: Context? = null
    fun attach(context: Context) { ctx = context.applicationContext }
    fun contextOrNull(): Context? = ctx
    fun requireContext(): Context =
        ctx ?: error("AndroidNotificationContext.attach() not called — wire it from MainActivity.onCreate")
}

class FavoriteCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val leadDays = inputData.getInt(NotificationScheduler.KEY_LEAD_DAYS, 3)
            val ctx = applicationContext

            val sharedSettings = SharedPreferencesSettings(
                ctx.getSharedPreferences(com.lkaesberg.mensaapp.APP_PREFS_NAME, Context.MODE_PRIVATE)
            )
            val favorites = FavoritesManager(sharedSettings).favorites.value
            if (favorites.isEmpty()) return@withContext Result.success()

            // "Nur Stamm-Mensa" toggle restricts the search to the selected canteen.
            // Off (default) → fan out across every canteen.
            val onlyHomeCanteen = sharedSettings.getBoolean("notif_only_home", false)
            val activeSlug = sharedSettings.getStringOrNull("selected_canteen_slug")
            val disabledCanteenIds = sharedSettings.getStringOrNull("disabled_canteens")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()

            val repo = MealsRepository(SupabaseProvider.client().postgrest)
            val allCanteens = repo.getCanteens().filter { it.id !in disabledCanteenIds }
            val canteens = if (onlyHomeCanteen) {
                allCanteens.filter { CanteenStaticData.matchFor(it.name)?.slug == activeSlug }
            } else {
                allCanteens
            }
            if (canteens.isEmpty()) return@withContext Result.success()

            val tz = TimeZone.currentSystemDefault()
            val today: LocalDate = Clock.System.todayIn(tz)
            val window = (0..leadDays).map { today.plus(it, DateTimeUnit.DAY) }

            data class Match(val canteen: com.lkaesberg.mensaapp.Canteen, val date: LocalDate, val md: com.lkaesberg.mensaapp.MealDate)
            val matches = mutableListOf<Match>()
            for (canteen in canteens) {
                val mealsByDate = repo.getMealsForCanteen(canteen.id)
                for (d in window) {
                    mealsByDate[d].orEmpty().forEach { md ->
                        val title = md.meals?.cleanTitle ?: md.meals?.title ?: ""
                        val titleLegacy = md.meals?.title ?: ""
                        if (favorites.containsFavorite(title) || favorites.containsFavorite(titleLegacy)) {
                            matches.add(Match(canteen, d, md))
                        }
                    }
                }
            }

            // Same dish at multiple canteens on the same day → keep all so the user
            // can pick the closest one. Notifications are grouped by clean title so
            // the system collapses them into a single line per dish.
            matches.forEachIndexed { i, match ->
                val enriched = MealEnrichment.enrich(match.md.meals)
                val whenLabel = relativeDayLabel(today, match.date)
                val groupKey = "fav-${enriched.cleanTitle.ifBlank { match.md.mealId }}"
                NotificationCompat.Builder(ctx, NotificationScheduler.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.star_on)
                    .setContentTitle("★ ${enriched.cleanTitle} $whenLabel")
                    .setContentText("${match.canteen.name}${enriched.sides.firstOrNull()?.let { " · $it" } ?: ""}")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setGroup(groupKey)
                    .build().also { notification ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) return@also
                        NotificationManagerCompat.from(ctx).notify(2_000 + i, notification)
                    }
            }

            Result.success()
        } catch (t: Throwable) {
            println("FavoriteCheckWorker failed: ${t.message}")
            Result.retry()
        }
    }

    private fun relativeDayLabel(today: LocalDate, target: LocalDate): String {
        val diff = (target.toEpochDays() - today.toEpochDays()).toInt()
        return when (diff) {
            0 -> "heute"
            1 -> "morgen"
            else -> {
                val weekday = when (target.dayOfWeek.isoDayNumber) {
                    1 -> "Mo"
                    2 -> "Di"
                    3 -> "Mi"
                    4 -> "Do"
                    5 -> "Fr"
                    6 -> "Sa"
                    else -> "So"
                }
                weekday
            }
        }
    }
}

private fun Settings.getStringOrNull(key: String): String? =
    if (hasKey(key)) getString(key, "") else null
