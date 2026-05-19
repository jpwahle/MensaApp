package com.lkaesberg.mensaapp.notifications

/**
 * iOS no-op stub. Real iOS push requires APNs + a server-side scheduler, which
 * is out of scope for this pass. The settings UI still works — the scheduler
 * simply remembers the preference and reports notifications as not permitted.
 */
actual class NotificationScheduler actual constructor() {
    actual fun setEnabled(enabled: Boolean, leadDays: Int, hourOfDay: Int) {
        println("[ios] NotificationScheduler.setEnabled($enabled, lead=$leadDays, hour=$hourOfDay) — no-op")
    }
    actual fun runCheckNow() { /* no-op */ }
    actual fun cancelAll() { /* no-op */ }
    actual fun sendTestNotification(): Boolean {
        println("[ios] NotificationScheduler.sendTestNotification() — no-op")
        return false
    }
    actual fun isPermitted(): Boolean = false
    actual fun isSupported(): Boolean = false
}
