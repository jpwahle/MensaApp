package com.lkaesberg.mensaapp.notifications

/**
 * Web/Wasm no-op stub. Browser notifications would require Notification API +
 * Service Worker + push backend, which is out of scope. The UI for settings
 * still renders so users can review the design.
 */
actual class NotificationScheduler actual constructor() {
    actual fun setEnabled(enabled: Boolean, leadDays: Int, hourOfDay: Int) {
        println("[wasm] NotificationScheduler.setEnabled($enabled, lead=$leadDays, hour=$hourOfDay) — no-op")
    }
    actual fun runCheckNow() { /* no-op */ }
    actual fun cancelAll() { /* no-op */ }
    actual fun sendTestNotification(): Boolean {
        println("[wasm] NotificationScheduler.sendTestNotification() — no-op")
        return false
    }
    actual fun isPermitted(): Boolean = false
    actual fun isSupported(): Boolean = false
}
