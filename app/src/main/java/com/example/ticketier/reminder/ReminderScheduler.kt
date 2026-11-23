package com.example.ticketier.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.ticketier.data.Event
import com.example.ticketier.data.Ticket
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * CONCEPT: we never run the job ourselves. We hand WorkManager a WorkRequest that says
 * "run ReminderWorker after this delay". WorkManager stores it on the phone and runs it
 * later, even if the app is closed or the phone restarts.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "event_reminders"
    const val KEY_TITLE = "title"
    const val KEY_TEXT = "text"
    const val KEY_ID = "notification_id"

    private val MONTHS = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    )

    /** Notifications must belong to a "channel" (the user can mute it in Settings). */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Event reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Reminders before events you have tickets for" }
        manager.createNotificationChannel(channel)
    }

    /**
     * Call this after a purchase. Reminds the user 24 hours before the event
     * (or 1 hour before, if the event is less than a day away).
     * Returns true if a reminder was scheduled.
     */
    fun scheduleForPurchase(context: Context, event: Event): Boolean {
        val start = eventStartMillis(event) ?: return false
        val now = System.currentTimeMillis()
        val dayBefore = start - TimeUnit.HOURS.toMillis(24)
        val hourBefore = start - TimeUnit.HOURS.toMillis(1)

        val reminderAt = when {
            dayBefore > now -> dayBefore
            hourBefore > now -> hourBefore
            else -> return false // event already started or about to
        }

        val data = workDataOf(
            KEY_TITLE to "${event.artist} is coming up",
            KEY_TEXT to "${event.day} ${event.month} at ${event.time}, ${event.venue}, ${event.location}",
            KEY_ID to event.id
        )

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(reminderAt - now, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("event-reminder")
            .build()

        // "Unique" work: buying twice for the same event does not create two reminders.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-event-${event.id}",
            ExistingWorkPolicy.KEEP,
            request
        )
        return true
    }

    /** DEMO: shows the reminder 1 minute from now, so you can show it working in class. */
    fun scheduleTest(context: Context, ticket: Ticket) {
        val data = workDataOf(
            KEY_TITLE to "Reminder: ${ticket.artist}",
            KEY_TEXT to "${ticket.day} ${ticket.month} · ${ticket.time} at ${ticket.venue}",
            KEY_ID to ticket.serial.hashCode()
        )
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .setInputData(data)
            .addTag("test-reminder")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "test-reminder-${ticket.serial}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // -----------------------------------------------------------------------
    // Events only store day ("14"), month ("NOV") and time ("7:00 PM"), no year.
    // We assume the NEXT time that date comes around. (Later, a real date column
    // in the database makes this exact.)
    // -----------------------------------------------------------------------
    internal fun eventStartMillis(event: Event): Long? {
        return try {
            val day = event.day.trim().toInt()
            val month = MONTHS.indexOf(event.month.trim().take(3).uppercase(Locale.US)) + 1
            if (month == 0) return null
            val time = parseTime(event.time) ?: return null

            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            var start = ZonedDateTime.of(LocalDate.of(now.year, month, day), time, zone)
            if (!start.isAfter(now)) start = start.plusYears(1)
            start.toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(text: String): LocalTime? {
        val cleaned = text.trim().uppercase(Locale.US)
        val patterns = listOf("h:mm a", "hh:mm a", "H:mm", "HH:mm")
        for (pattern in patterns) {
            try {
                return LocalTime.parse(cleaned, DateTimeFormatter.ofPattern(pattern, Locale.US))
            } catch (_: Exception) {
                // try the next pattern
            }
        }
        return null
    }
}
