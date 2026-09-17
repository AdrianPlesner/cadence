package dk.azp.cadence

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.azp.cadence.ui.CadenceNavHost
import dk.azp.cadence.ui.LocalAppContainer
import dk.azp.cadence.ui.TaskDetailRoute
import dk.azp.cadence.ui.theme.CadenceTheme

class MainActivity : ComponentActivity() {

    /** Task a notification asked to open; consumed by the navigation host. */
    private var requestedTask by mutableStateOf<TaskDetailRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTask = intent.requestedTask()
        val container = (application as CadenceApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                CadenceTheme {
                    CadenceNavHost(requestedTask = requestedTask, onRequestedTaskShown = ::clearRequestedTask)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTask = intent.requestedTask()
    }

    /** Also strips the extras so a later recreation of the activity does not jump to the task again. */
    private fun clearRequestedTask() {
        requestedTask = null
        intent.removeExtra(EXTRA_GROUP_ID)
        intent.removeExtra(EXTRA_TASK_ID)
    }

    private fun Intent.requestedTask(): TaskDetailRoute? {
        val groupId = getStringExtra(EXTRA_GROUP_ID)
        val taskId = getStringExtra(EXTRA_TASK_ID)
        return if (groupId != null && taskId != null) TaskDetailRoute(groupId, taskId) else null
    }

    companion object {
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_TASK_ID = "task_id"
    }
}
