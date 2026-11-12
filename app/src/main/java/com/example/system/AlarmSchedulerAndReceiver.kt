package com.example.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.util.Calendar

object FocusNotificationChannels {
    const val CHANNEL_ALARMS = "focuspulse_alarms"
    const val CHANNEL_REMINDERS = "focuspulse_reminders"
    const val CHANNEL_INVENTORY = "focuspulse_inventory"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL_ALARMS,
                        "Sleep & Wake Alarms",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Bedtime wind-down and circadian wake-up alarms"
                        enableVibration(true)
                    },
                    NotificationChannel(
                        CHANNEL_REMINDERS,
                        "Notebook & Inactivity Nudges",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Gentle inactivity reminders and notebook due alerts"
                    },
                    NotificationChannel(
                        CHANNEL_INVENTORY,
                        "Inventory & Auto-Reorder Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Low-stock alerts and automated purchase reorder confirmations"
                    }
                )
            )
        }
    }
}

class SleepAlarmScheduler(private val context: Context) {

    fun scheduleBedtimeAndWake(
        bedHour: Int,
        bedMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
        inactivityHours: Int
    ) {
        FocusNotificationChannels.ensureChannels(context)
        scheduleDailyAlarm(
            requestCode = REQ_BEDTIME,
            hour = bedHour,
            minute = bedMinute,
            alarmType = TYPE_BEDTIME,
            title = "Bedtime Shield Engaging",
            subtitle = "Time to wind down. Distracting apps are locking until ${String.format("%02d:%02d", wakeHour, wakeMinute)}."
        )
        scheduleDailyAlarm(
            requestCode = REQ_WAKE,
            hour = wakeHour,
            minute = wakeMinute,
            alarmType = TYPE_WAKE,
            title = "Optimal Wake-Up Window",
            subtitle = "Good morning! Your sleep lockout has lifted."
        )
        scheduleInactivityCheck(inactivityHours)
    }

    fun scheduleNoteReminder(noteId: String, title: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        FocusNotificationChannels.ensureChannels(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SleepAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TYPE, TYPE_NOTE_REMINDER)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SUBTITLE, "Scheduled reminder from your THE MASTER MIND notebook")
        }
        val pi = PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleSafeExact(am, triggerAtMillis, pi)
    }

    fun scheduleInactivityCheck(hours: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + hours.coerceIn(1, 48) * 3_600_000L
        val intent = Intent(context, SleepAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TYPE, TYPE_INACTIVITY)
            putExtra(EXTRA_TITLE, "Ready to log your day?")
            putExtra(EXTRA_SUBTITLE, "You've been inactive for ${hours}h. Record your progress or ask your AI Buddy for an activity idea.")
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_INACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun triggerImmediateTestAlarm(type: String, title: String, subtitle: String) {
        FocusNotificationChannels.ensureChannels(context)
        val alarmIntent = Intent(context, SleepAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SUBTITLE, subtitle)
        }
        context.startActivity(alarmIntent)
    }

    fun sendLowStockNotification(itemName: String, remainingQty: Long, autoOrderedQty: Long?) {
        FocusNotificationChannels.ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val contentIntent = PendingIntent.getActivity(
            context,
            itemName.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val msg = if (autoOrderedQty != null) {
            "Only $remainingQty left. Auto-reorder of $autoOrderedQty units queued."
        } else {
            "Only $remainingQty left in stock. Review supplier reorder."
        }
        val notif = NotificationCompat.Builder(context, FocusNotificationChannels.CHANNEL_INVENTORY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Low Stock: $itemName")
            .setContentText(msg)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(itemName.hashCode(), notif)
        } catch (_: SecurityException) {
            // Notification permission not granted yet
        }
    }

    private fun scheduleDailyAlarm(
        requestCode: Int,
        hour: Int,
        minute: Int,
        alarmType: String,
        title: String,
        subtitle: String
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SleepAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TYPE, alarmType)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SUBTITLE, subtitle)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleSafeExact(am, cal.timeInMillis, pi)
    }

    private fun scheduleSafeExact(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.example.focuspulse.ALARM_TRIGGER"
        const val EXTRA_TYPE = "alarm_type"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_SUBTITLE = "alarm_subtitle"

        const val TYPE_BEDTIME = "bedtime"
        const val TYPE_WAKE = "wake"
        const val TYPE_INACTIVITY = "inactivity"
        const val TYPE_NOTE_REMINDER = "note_reminder"

        private const val REQ_BEDTIME = 1001
        private const val REQ_WAKE = 1002
        private const val REQ_INACTIVITY = 1003
    }
}

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val guardPrefs = FocusGuardPreferences(context)
        val savedSchedule = guardPrefs.getScheduleConfig()
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SleepAlarmScheduler(context).scheduleBedtimeAndWake(
                bedHour = savedSchedule.bedHour,
                bedMinute = savedSchedule.bedMinute,
                wakeHour = savedSchedule.wakeHour,
                wakeMinute = savedSchedule.wakeMinute,
                inactivityHours = savedSchedule.inactivityHours
            )
            return
        }
        FocusNotificationChannels.ensureChannels(context)
        val type = intent.getStringExtra(SleepAlarmScheduler.EXTRA_TYPE) ?: SleepAlarmScheduler.TYPE_BEDTIME
        val title = intent.getStringExtra(SleepAlarmScheduler.EXTRA_TITLE) ?: "THE MASTER MIND Reminder"
        val subtitle = intent.getStringExtra(SleepAlarmScheduler.EXTRA_SUBTITLE) ?: "Check your daily plan."

        // Reschedule daily bedtime/wake alarms for tomorrow
        if (type == SleepAlarmScheduler.TYPE_BEDTIME || type == SleepAlarmScheduler.TYPE_WAKE) {
            SleepAlarmScheduler(context).scheduleBedtimeAndWake(
                bedHour = savedSchedule.bedHour,
                bedMinute = savedSchedule.bedMinute,
                wakeHour = savedSchedule.wakeHour,
                wakeMinute = savedSchedule.wakeMinute,
                inactivityHours = savedSchedule.inactivityHours
            )
        }

        val (_, useFullScreenForInactivity) = guardPrefs.getInactivityConfig()

        val launchFullScreen = when (type) {
            SleepAlarmScheduler.TYPE_WAKE, SleepAlarmScheduler.TYPE_BEDTIME -> true
            SleepAlarmScheduler.TYPE_INACTIVITY -> useFullScreenForInactivity
            else -> false
        }

        val fullScreenIntent = Intent(context, SleepAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(SleepAlarmScheduler.EXTRA_TYPE, type)
            putExtra(SleepAlarmScheduler.EXTRA_TITLE, title)
            putExtra(SleepAlarmScheduler.EXTRA_SUBTITLE, subtitle)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            type.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (launchFullScreen) {
            FocusNotificationChannels.CHANNEL_ALARMS
        } else {
            FocusNotificationChannels.CHANNEL_REMINDERS
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setPriority(
                if (launchFullScreen) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(fullScreenPi)

        if (launchFullScreen) {
            builder.setFullScreenIntent(fullScreenPi, true)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(type.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }
}
