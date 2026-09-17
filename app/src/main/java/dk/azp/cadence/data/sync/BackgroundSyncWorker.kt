package dk.azp.cadence.data.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dk.azp.cadence.CadenceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Periodic background sync. Each run opens a short window in which this device is reachable and contacts every peer
 * it has seen, so a device left in a drawer still picks up changes from a device whose app is open.
 */
class BackgroundSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val inForeground = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (!inForeground) {
            val outcome = (applicationContext as CadenceApp).container.syncManager.runBackgroundWindow(WINDOW)
            Log.i(TAG, "Background sync: $outcome")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        private const val WORK_NAME = "background-sync"
        private val WINDOW = 25.seconds
        private val INTERVAL: Duration = Duration.ofMinutes(15)

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(INTERVAL)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
