package dk.azp.cadence.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.CompletionEntity
import dk.azp.cadence.data.db.TaskEntity
import dk.azp.cadence.data.db.TaskWithLastDone
import dk.azp.cadence.data.repo.TaskEdit
import dk.azp.cadence.data.repo.TaskRepository
import dk.azp.cadence.ui.Formatting
import dk.azp.cadence.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TaskDetailViewModel(
    private val groupId: String,
    private val taskId: String,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val task: StateFlow<TaskEntity?> =
        taskRepository.observeTask(taskId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> =
        taskRepository.observeCategories(groupId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completions: StateFlow<List<CompletionEntity>> =
        taskRepository.observeCompletions(taskId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val row: StateFlow<TaskRow?> = combine(task, categories, completions) { taskEntity, categoryList, history ->
        taskEntity?.let {
            val names = categoryList.associate { category -> category.id to category.name }
            TaskRow.from(TaskWithLastDone(it, history.maxOfOrNull(CompletionEntity::doneDate)), names, LocalDate.now())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markDone(date: LocalDate) {
        viewModelScope.launch { taskRepository.markDone(taskId, date) }
    }

    fun deleteCompletion(completionId: String) {
        viewModelScope.launch { taskRepository.deleteCompletion(completionId) }
    }

    fun update(edit: TaskEdit) {
        viewModelScope.launch { taskRepository.updateTask(taskId, edit) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            onDeleted()
        }
    }

    suspend fun addCategory(name: String): String = taskRepository.addCategory(groupId, name)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(groupId: String, taskId: String, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "task-$taskId") { TaskDetailViewModel(groupId, taskId, it.taskRepository) }
    val task by viewModel.task.collectAsStateWithLifecycle()
    val row by viewModel.row.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val completions by viewModel.completions.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit task") }
                    IconButton(onClick = { confirmingDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete task") }
                },
            )
        },
    ) { padding ->
        val current = row
        if (current != null) {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                item { SummaryCard(current) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.markDone(LocalDate.now()) }, modifier = Modifier.weight(1f)) { Text("Done today") }
                        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) { Text("Done on date…") }
                    }
                }
                item {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (completions.isEmpty()) {
                    item { Text("Not done yet.", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(completions, key = { it.id }) { completion ->
                    ListItem(
                        headlineContent = { Text(Formatting.date(LocalDate.ofEpochDay(completion.doneDate))) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteCompletion(completion.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove entry")
                            }
                        },
                    )
                }
            }
        }
    }

    if (editing) {
        task?.let { existing ->
            TaskEditorDialog(
                title = "Edit task",
                initial = existing,
                categories = categories,
                onCreateCategory = viewModel::addCategory,
                onDismiss = { editing = false },
                onSave = { edit -> editing = false; viewModel.update(edit) },
            )
        }
    }
    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete task?",
            text = "The task and its history are removed on every device in the group.",
            confirmLabel = "Delete",
            onDismiss = { confirmingDelete = false },
            onConfirm = { confirmingDelete = false; viewModel.delete(onBack) },
        )
    }
    if (pickingDate) {
        DoneDatePickerDialog(onDismiss = { pickingDate = false }, onPick = { date -> pickingDate = false; viewModel.markDone(date) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(row: TaskRow) {
    val outline = if (row.isOverdue) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null
    Card(Modifier.fillMaxWidth().padding(16.dp), border = outline) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val lastDone = row.lastDone
            val daysSince = row.daysSince
            if (lastDone != null && daysSince != null) {
                Text("Last done ${Formatting.date(lastDone)}", style = MaterialTheme.typography.titleMedium)
                Text(Formatting.daysAgo(daysSince).replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Not done yet", style = MaterialTheme.typography.titleMedium)
            }
            row.task.cadenceDays?.let { cadence ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repeats every $cadence days", style = MaterialTheme.typography.bodyMedium)
                    Text("·")
                    DueLabel(row)
                }
                Text(
                    if (row.task.notifyWhenDue) "Reminder when due is on" else "Reminder when due is off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.categoryNames.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.categoryNames.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }
    }
}
