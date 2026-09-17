package dk.azp.cadence

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dk.azp.cadence.data.reminders.DueReminderWorker

class CadenceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.dueReminders.ensureChannel()
        DueReminderWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.syncManager.start()

            override fun onStop(owner: LifecycleOwner) = container.syncManager.stop()
        })
    }
}
