/*
 * TrialReminder.kt
 * MacroDime
 *
 * The promise on the paywall's timeline: a notification two days before a free
 * trial turns into a charge. Blinkist found that promising this reminder, and
 * keeping it, raised trial starts and cut complaints, because the fear it
 * answers ("I'll forget and get charged") is what stops people starting.
 *
 * Local only. An alarm on this phone posts the notification, so it needs no
 * network, sends nothing anywhere, and changes nothing in the privacy answers.
 * When it is due comes from EntitlementPolicy.reminderAt in :core, where it is
 * tested; this file only schedules and posts.
 */
package com.lungelo.macrodime.billing

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.lungelo.macrodime.MainActivity
import com.lungelo.macrodime.R
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.EntitlementPolicy
import com.lungelo.macrodime.domain.Store

object TrialReminder {

    const val CHANNEL_ID = "trial-reminder"
    private const val NOTIFICATION_ID = 4101
    private const val PREFS = "macrodime_reminder"
    private const val KEY_AT = "at"
    private const val KEY_ENABLED = "enabled"

    /** Whether the person wants the reminder. On unless they turned it off on the paywall. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }

    /** Whether Android will show it: always below Android 13, and after the permission from 13 on. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Schedules the reminder for [entitlement], or cancels it when none is due
     * (no trial, a cancelled one, or the reminder switched off). Called each
     * time the store's answer changes, so a cancellation in Google Play takes
     * the reminder away by itself.
     */
    fun sync(context: Context, entitlement: Entitlement, now: Long = System.currentTimeMillis()) {
        val at = if (isEnabled(context)) EntitlementPolicy.reminderAt(entitlement, now) else null
        schedule(context, at)
    }

    private fun schedule(context: Context, at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        if (at == null) {
            alarms.cancel(pending)
        } else {
            // Inexact on purpose: within the hour is fine for a two-day warning,
            // and an exact alarm would need a permission of its own.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        prefs(context).edit { putLong(KEY_AT, at ?: 0) }
    }

    /** After a restart the alarm is gone; put it back if it is still ahead. */
    internal fun restore(context: Context) {
        val at = prefs(context).getLong(KEY_AT, 0)
        if (at > System.currentTimeMillis()) schedule(context, at)
    }

    internal fun post(context: Context) {
        prefs(context).edit { putLong(KEY_AT, 0) }
        // Checked here, beside the post, so the check and the call cannot drift apart.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = EntitlementPolicy.reminderTitle(EntitlementPolicy.REMINDER_DAYS)
        val text = EntitlementPolicy.reminderDetail(Entitlement(isPro = true), null, Store.GooglePlay)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the post. The
            // card on Today still says the same thing.
        }
    }

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Free trial reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One reminder two days before a free trial ends."
            },
        )
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, TrialReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Forgets the reminder, for Delete All My Data. */
    fun forget(context: Context) {
        schedule(context, null)
        prefs(context).edit { clear() }
    }
}

/** Posts the reminder when its alarm fires, and puts the alarm back after a restart. */
class TrialReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) TrialReminder.restore(context) else TrialReminder.post(context)
    }
}
