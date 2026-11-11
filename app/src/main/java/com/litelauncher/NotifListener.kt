package com.litelauncher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Tracks which apps have active (non-ongoing) notifications, for the dots on icons. */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() = update()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = update()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = update()

    private fun update() {
        val counts = HashMap<String, Int>()
        try {
            val list: Array<StatusBarNotification>? = activeNotifications
            if (list != null) {
                for (n in list) {
                    if (n.isOngoing) continue
                    counts[n.packageName] = (counts[n.packageName] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            // Listener not ready or permission revoked: show no dots.
        }
        NotifState.set(counts)
    }
}

object NotifState {
    @Volatile
    var counts: Map<String, Int> = emptyMap()
        private set

    /** Called on the main thread whenever the set of notifications changes. */
    var listener: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    fun set(c: Map<String, Int>) {
        counts = c
        main.post { listener?.invoke() }
    }

    fun has(pkg: String?): Boolean = pkg != null && (counts[pkg] ?: 0) > 0

    fun isEnabled(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(ctx.packageName)
    }
}
