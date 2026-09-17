package dk.azp.cadence

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dk.azp.cadence.data.ble.BleWake
import dk.azp.cadence.data.reminders.DueReminderWorker
import dk.azp.cadence.data.sync.BackgroundSyncWorker
import dk.azp.cadence.data.sync.SyncManager

class CadenceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.dueReminders.ensureChannel()
        DueReminderWorker.schedule(this)
        BackgroundSyncWorker.schedule(this)
        BleWake.register(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.syncManager.acquire(SyncManager.HOLDER_FOREGROUND)

            override fun onStop(owner: LifecycleOwner) = container.syncManager.release(SyncManager.HOLDER_FOREGROUND)
        })
    }
}
