package com.lkaesberg.mensaapp.notifications

/**
 * Schedules + cancels favorite-on-the-plan reminders.
 * Android implementation uses WorkManager + NotificationManagerCompat.
 * iOS/wasmJs are no-op stubs (real iOS push needs APNs + a server, out of scope).
 */
expect class NotificationScheduler() {
    /**
     * Persist the user's preferences and re-arm the periodic check.
     * [hourOfDay] is the local-time hour (0–23) when the daily check fires.
     */
    fun setEnabled(enabled: Boolean, leadDays: Int, hourOfDay: Int)

    /** Run an immediate one-shot favorite check (used after toggling settings). */
    fun runCheckNow()

    /** Cancel all scheduled work. */
    fun cancelAll()

    /**
     * Post a sample notification right now so the user can confirm push works.
     * Bypasses the favourites check so it works even with no favourites set.
     * Returns true if the notification was actually posted, false if it was
     * skipped (no runtime permission, unsupported platform, or no context).
     */
    fun sendTestNotification(): Boolean

    /**
     * Whether the platform reports notifications as permitted right now.
     * Used by the onboarding screen to decide whether to ask.
     */
    fun isPermitted(): Boolean

    /**
     * Whether this platform actually delivers notifications. iOS / wasmJs are
     * no-op stubs and return false, letting the UI hide notification controls.
     */
    fun isSupported(): Boolean
}
