package dk.azp.cadence.data.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dk.azp.cadence.CadenceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Handles the "Mark done" action on a due-task notification. */
class MarkDoneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as CadenceApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.taskRepository.markDone(taskId, LocalDate.now())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"

        fun intent(context: Context, taskId: String): PendingIntent {
            val intent = Intent(context, MarkDoneReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
            return PendingIntent.getBroadcast(context, taskId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
