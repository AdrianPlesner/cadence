package dk.azp.cadence.ui.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.GroupEntity
import dk.azp.cadence.data.repo.GroupRepository
import dk.azp.cadence.data.repo.TaskEdit
import dk.azp.cadence.data.repo.TaskRepository
import dk.azp.cadence.ui.Formatting
import dk.azp.cadence.ui.appViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class GroupTasksViewModel(
    private val groupId: String,
    groupRepository: GroupRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val selectedCategoryFlow = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = selectedCategoryFlow

    /** Re-emits every minute so "days since" rolls over at midnight without user interaction. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }

    val group: StateFlow<GroupEntity?> =
        groupRepository.observeGroup(groupId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> =
        taskRepository.observeCategories(groupId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<TaskRow>> = combine(
        taskRepository.observeTasks(groupId),
        categories,
        selectedCategoryFlow,
        today,
    ) { tasks, categoryList, selected, date ->
        val names = categoryList.associate { it.id to it.name }
        tasks
            .filter { selected == null || selected in it.task.categoryIdList() }
            .map { TaskRow.from(it, names, date) }
            .sortedWith(TaskRow.urgencyOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(categoryId: String?) {
        selectedCategoryFlow.value = categoryId
    }

    fun markDone(taskId: String, date: LocalDate) {
        viewModelScope.launch { taskRepository.markDone(taskId, date) }
    }

    fun addTask(edit: TaskEdit) {
        viewModelScope.launch { taskRepository.addTask(groupId, edit) }
    }

    suspend fun addCategory(name: String): String = taskRepository.addCategory(groupId, name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTasksScreen(groupId: String, onBack: () -> Unit, onOpenTask: (String) -> Unit, onOpenOverview: () -> Unit) {
    val viewModel = appViewModel(key = "tasks-$groupId") { GroupTasksViewModel(groupId, it.groupRepository, it.taskRepository) }
    val group by viewModel.group.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var pickingDateFor by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = onOpenOverview) { Icon(Icons.Default.Groups, contentDescription = "Group overview") } },
            )
        },
        floatingActionButton = {
            if (group?.kicked == false) {
                FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "Add task") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (categories.isNotEmpty()) {
                CategoryFilterRow(categories, selectedCategory, onSelect = viewModel::selectCategory)
            }
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No tasks here yet.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.task.id }) { row ->
                        TaskListRow(
                            row = row,
                            onClick = { onOpenTask(row.task.id) },
                            onDoneToday = { viewModel.markDone(row.task.id, LocalDate.now()) },
                            onDoneOnDate = { pickingDateFor = row.task.id },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        TaskEditorDialog(
            title = "New task",
            initial = null,
            categories = categories,
            onCreateCategory = viewModel::addCategory,
            onDismiss = { adding = false },
            onSave = { edit -> adding = false; viewModel.addTask(edit) },
        )
    }
    pickingDateFor?.let { taskId ->
        DoneDatePickerDialog(
            onDismiss = { pickingDateFor = null },
            onPick = { date -> pickingDateFor = null; viewModel.markDone(taskId, date) },
        )
    }
}

@Composable
private fun CategoryFilterRow(categories: List<CategoryEntity>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") }) }
        items(categories, key = { it.id }) { category ->
            FilterChip(selected = selected == category.id, onClick = { onSelect(category.id) }, label = { Text(category.name) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskListRow(row: TaskRow, onClick: () -> Unit, onDoneToday: () -> Unit, onDoneOnDate: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val overdueOutline = if (row.isOverdue) {
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp).border(2.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    Box {
        ListItem(
            modifier = overdueOutline.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
            headlineContent = { Text(row.task.name) },
            supportingContent = {
                Column {
                    Text(row.daysSince?.let { "Done " + Formatting.daysAgo(it) } ?: "Not done yet")
                    if (row.categoryNames.isNotEmpty()) {
                        Text(row.categoryNames.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DueLabel(row)
                    IconButton(onClick = onDoneToday) { Icon(Icons.Default.Check, contentDescription = "Mark done today") }
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Done today") }, onClick = { menuOpen = false; onDoneToday() })
            DropdownMenuItem(text = { Text("Done on another date…") }, onClick = { menuOpen = false; onDoneOnDate() })
        }
    }
}

@Composable
fun DueLabel(row: TaskRow) {
    val cadence = row.task.cadenceDays ?: return
    val daysUntilDue = row.daysUntilDue
    val text = if (daysUntilDue == null) "Every $cadence days" else Formatting.dueIn(daysUntilDue)
    val color: Color = when {
        daysUntilDue == null -> MaterialTheme.colorScheme.onSurfaceVariant
        daysUntilDue < 0 -> MaterialTheme.colorScheme.error
        daysUntilDue == 0L -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val style = if (row.isOverdue) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium
    Text(text, color = color, style = style)
}
