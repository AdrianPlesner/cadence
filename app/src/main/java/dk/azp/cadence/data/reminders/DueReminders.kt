package dk.azp.cadence.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dk.azp.cadence.MainActivity
import dk.azp.cadence.R
import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.db.ReminderTask
import dk.azp.cadence.ui.Formatting
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Posts one notification per task whose cadence countdown has reached zero and that has reminders enabled. A task keeps a
 * single notification, and tasks that are no longer due lose theirs.
 */
class DueReminders(private val context: Context, private val db: CadenceDatabase) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** Refreshes run one at a time, so a slow one can't re-post a reminder that a later one just removed. */
    private val refreshLock = Mutex()

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Due tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A task's repeat interval has run out"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Reminds about the due tasks among [taskIds] and in [groupIds], or about every due task when [taskIds] is null,
     * which is the daily morning run. Reminders of other tasks are left alone, except that tasks no longer due always
     * lose theirs, which never makes a sound. Dismissed reminders therefore only return for a task that was itself
     * edited, or after this device is readmitted to its group.
     */
    suspend fun refresh(taskIds: Set<String>?, groupIds: Set<String> = emptySet()) = refreshLock.withLock {
        val today = LocalDate.now()
        val due = db.taskDao().remindable().filter { isDue(it, today) }
        val dueIds = due.map { it.task.id }.toSet()
        notificationManager.activeNotifications
            .filter { it.tag != null && it.tag !in dueIds }
            .forEach { notificationManager.cancel(it.tag, NOTIFICATION_ID) }
        if (canPost()) {
            due.filter { taskIds == null || it.task.id in taskIds || it.task.groupId in groupIds }.forEach { post(it, today) }
        }
    }

    private fun isDue(reminder: ReminderTask, today: LocalDate): Boolean {
        val cadence = reminder.task.cadenceDays ?: return false
        val lastDone = reminder.lastDone ?: return false
        return cadence - ChronoUnit.DAYS.between(LocalDate.ofEpochDay(lastDone), today) <= 0
    }

    /** Replaces any shown reminder for the task, so the new one alerts even if the old one is still on screen. */
    private fun post(reminder: ReminderTask, today: LocalDate) {
        val task = reminder.task
        val daysLeft = task.cadenceDays!! - ChronoUnit.DAYS.between(LocalDate.ofEpochDay(reminder.lastDone!!), today)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.name)
            .setContentText(Formatting.dueIn(daysLeft) + " · " + reminder.groupName)
            .setContentIntent(openTaskIntent(task.groupId, task.id))
            .addAction(0, "Mark done", MarkDoneReceiver.intent(context, task.id))
            .setAutoCancel(true)
            .build()
        notificationManager.cancel(task.id, NOTIFICATION_ID)
        notificationManager.notify(task.id, NOTIFICATION_ID, notification)
    }

    private fun openTaskIntent(groupId: String, taskId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_GROUP_ID, groupId)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, taskId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "due_tasks"
        const val NOTIFICATION_ID = 1
    }
}
