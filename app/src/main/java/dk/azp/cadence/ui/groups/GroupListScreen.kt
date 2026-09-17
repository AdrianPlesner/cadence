package dk.azp.cadence.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dk.azp.cadence.data.db.GroupSummary
import dk.azp.cadence.data.repo.GroupRepository
import dk.azp.cadence.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupListViewModel(private val groupRepository: GroupRepository) : ViewModel() {

    val groups: StateFlow<List<GroupSummary>> =
        groupRepository.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createGroup(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(groupRepository.createGroup(name)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(onOpenGroup: (String) -> Unit, onJoinGroup: () -> Unit) {
    val viewModel = appViewModel { GroupListViewModel(it.groupRepository) }
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cadence") }) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Add, contentDescription = "Add group") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("New group") }, onClick = { menuOpen = false; creating = true })
                    DropdownMenuItem(text = { Text("Join group") }, onClick = { menuOpen = false; onJoinGroup() })
                }
            }
        },
    ) { padding ->
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No groups yet. Create one to start tracking tasks, or join a group by scanning its QR code.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(groups, key = { it.group.id }) { summary -> GroupRow(summary, onClick = { onOpenGroup(summary.group.id) }) }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New group",
            label = "Group name",
            onDismiss = { creating = false },
            onConfirm = { name -> creating = false; viewModel.createGroup(name, onOpenGroup) },
        )
    }
}

@Composable
private fun GroupRow(summary: GroupSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(summary.group.name) },
        supportingContent = { Text("${summary.taskCount} tasks · ${summary.deviceCount} devices") },
        trailingContent = {
            if (summary.group.kicked) {
                Text("Removed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}

/** A one-field dialog used wherever a single name is entered or edited. */
@Composable
fun NameDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, initial: String = "") {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
