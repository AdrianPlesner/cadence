package dk.azp.cadence.data.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dk.azp.cadence.CadenceApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Runs once every morning, posts reminders for everything that became due and schedules the next morning. */
class DueReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        (applicationContext as CadenceApp).container.dueReminders.refresh(alertAgain = true)
        schedule(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "due-reminders"
        private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val now = LocalDateTime.now()
            var next = LocalDate.now().atTime(REMINDER_TIME)
            if (!next.isAfter(now)) {
                next = next.plusDays(1)
            }
            val request = OneTimeWorkRequestBuilder<DueReminderWorker>()
                .setInitialDelay(Duration.between(now, next))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
