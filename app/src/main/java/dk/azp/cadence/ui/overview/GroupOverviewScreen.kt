package dk.azp.cadence.ui.overview

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.ble.BleWake
import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.DeviceWithSync
import dk.azp.cadence.data.db.GroupEntity
import dk.azp.cadence.data.repo.GroupRepository
import dk.azp.cadence.data.repo.TaskRepository
import dk.azp.cadence.data.sync.SyncManager
import dk.azp.cadence.ui.Formatting
import dk.azp.cadence.ui.appViewModel
import dk.azp.cadence.ui.groups.NameDialog
import dk.azp.cadence.ui.tasks.ConfirmDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupOverviewViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val taskRepository: TaskRepository,
    private val syncManager: SyncManager,
    private val identity: DeviceIdentity,
) : ViewModel() {

    val ownDeviceId: String get() = identity.deviceId

    val group: StateFlow<GroupEntity?> =
        groupRepository.observeGroup(groupId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members: StateFlow<List<DeviceWithSync>> =
        groupRepository.observeMembers(groupId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        taskRepository.observeCategories(groupId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncStatus: StateFlow<SyncManager.Status> = syncManager.status

    val listenPort: StateFlow<Int> = syncManager.listenPort

    fun inviteCode(group: GroupEntity, port: Int): String = groupRepository.inviteFor(group, syncManager.localAddress(), port).encode()

    fun localAddress(): String? = syncManager.localAddress()

    fun syncNow() = syncManager.syncNow()

    fun bluetoothPermissionsGranted(context: android.content.Context) {
        BleWake.register(context)
        syncManager.refreshBeacon()
    }

    fun renameGroup(name: String) {
        viewModelScope.launch { groupRepository.renameGroup(groupId, name) }
    }

    fun renameThisDevice(name: String) {
        viewModelScope.launch { groupRepository.renameThisDevice(name) }
    }

    fun kick(deviceId: String) {
        viewModelScope.launch { groupRepository.kickDevice(groupId, deviceId) }
    }

    fun leave(onLeft: () -> Unit) {
        viewModelScope.launch {
            groupRepository.leaveGroup(groupId)
            onLeft()
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch { taskRepository.addCategory(groupId, name) }
    }

    fun renameCategory(categoryId: String, name: String) {
        viewModelScope.launch { taskRepository.renameCategory(categoryId, name) }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch { taskRepository.deleteCategory(categoryId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupOverviewScreen(groupId: String, onBack: () -> Unit, onLeft: () -> Unit) {
    val viewModel = appViewModel(key = "overview-$groupId") {
        GroupOverviewViewModel(groupId, it.groupRepository, it.taskRepository, it.syncManager, it.identity)
    }
    val group by viewModel.group.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val listenPort by viewModel.listenPort.collectAsStateWithLifecycle()
    var renamingGroup by rememberSaveable { mutableStateOf(false) }
    var renamingDevice by rememberSaveable { mutableStateOf(false) }
    var kicking by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (group?.kicked == false) {
                        IconButton(onClick = { renamingGroup = true }) { Icon(Icons.Default.Edit, contentDescription = "Rename group") }
                    }
                },
            )
        },
    ) { padding ->
        val current = group ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (current.kicked) {
                item { KickedBanner() }
            }
            item { SectionTitle("Sync") }
            item { SyncSection(syncStatus, onSyncNow = viewModel::syncNow) }
            item { BluetoothWakeRow(onGranted = viewModel::bluetoothPermissionsGranted) }
            item { SectionTitle("Devices") }
            items(members, key = { it.device.id }) { member ->
                val isThisDevice = member.device.id == viewModel.ownDeviceId
                DeviceRow(
                    member = member,
                    isThisDevice = isThisDevice,
                    canKick = !current.kicked && !isThisDevice,
                    onRename = { renamingDevice = true },
                    onKick = { kicking = member.device.id },
                )
            }
            if (!current.kicked) {
                item { SectionTitle("Invite a device") }
                item { InviteSection(code = viewModel.inviteCode(current, listenPort), address = viewModel.localAddress(), port = listenPort) }
            }
            item { SectionTitle("Categories") }
            items(categories, key = { it.id }) { category ->
                ListItem(
                    modifier = Modifier.clickable(enabled = !current.kicked) { renamingCategory = category.id },
                    headlineContent = { Text(category.name) },
                    trailingContent = {
                        if (!current.kicked) {
                            IconButton(onClick = { viewModel.deleteCategory(category.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete category")
                            }
                        }
                    },
                )
            }
            if (!current.kicked) {
                item { AddCategoryRow(onAdd = viewModel::addCategory) }
            }
            item {
                TextButton(onClick = { leaving = true }, modifier = Modifier.padding(16.dp)) {
                    Text("Leave group on this device", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (renamingGroup) {
        NameDialog(
            title = "Rename group",
            label = "Group name",
            initial = group?.name ?: "",
            onDismiss = { renamingGroup = false },
            onConfirm = { renamingGroup = false; viewModel.renameGroup(it) },
        )
    }
    if (renamingDevice) {
        NameDialog(
            title = "Rename this device",
            label = "Device name",
            initial = members.firstOrNull { it.device.id == viewModel.ownDeviceId }?.device?.name ?: "",
            onDismiss = { renamingDevice = false },
            onConfirm = { renamingDevice = false; viewModel.renameThisDevice(it) },
        )
    }
    kicking?.let { deviceId ->
        val name = members.firstOrNull { it.device.id == deviceId }?.device?.name ?: "this device"
        ConfirmDialog(
            title = "Remove $name?",
            text = "The device stops syncing with the group. It keeps the data it already has until it leaves the group itself.",
            confirmLabel = "Remove",
            onDismiss = { kicking = null },
            onConfirm = { kicking = null; viewModel.kick(deviceId) },
        )
    }
    renamingCategory?.let { categoryId ->
        NameDialog(
            title = "Rename category",
            label = "Category name",
            initial = categories.firstOrNull { it.id == categoryId }?.name ?: "",
            onDismiss = { renamingCategory = null },
            onConfirm = { renamingCategory = null; viewModel.renameCategory(categoryId, it) },
        )
    }
    if (leaving) {
        ConfirmDialog(
            title = "Leave group?",
            text = "All tasks of this group are deleted from this device. Other devices keep their copy.",
            confirmLabel = "Leave",
            onDismiss = { leaving = false },
            onConfirm = { leaving = false; viewModel.leave(onLeft) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun KickedBanner() {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            "This device was removed from the group. Its data is read-only; scan a new invite to rejoin.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun SyncSection(status: SyncManager.Status, onSyncNow: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSyncNow, enabled = status.running) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Text("  Sync now")
            }
            if (status.activeSyncs > 0) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
        Text(
            status.lastMessage ?: if (status.running) "Looking for other devices on this network." else "In the background the app syncs about every 15 minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Offers the Bluetooth permissions that let nearby devices wake each other for a sync. Hidden once granted. */
@Composable
private fun BluetoothWakeRow(onGranted: (android.content.Context) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BleWake.hasPermissions(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
        if (granted) {
            onGranted(context)
        }
    }
    if (BleWake.isSupported(context) && !granted) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "With Bluetooth allowed, devices near each other wake up and sync even when the app is closed on both.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { launcher.launch(BleWake.requiredPermissions.toTypedArray()) }) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Text("  Allow Bluetooth")
            }
        }
    }
}

@Composable
private fun DeviceRow(member: DeviceWithSync, isThisDevice: Boolean, canKick: Boolean, onRename: () -> Unit, onKick: () -> Unit) {
    ListItem(
        modifier = if (isThisDevice) Modifier.clickable(onClick = onRename) else Modifier,
        headlineContent = { Text(if (isThisDevice) member.device.name + " (this device)" else member.device.name) },
        supportingContent = {
            when {
                isThisDevice -> Text("Tap to rename")
                member.lastError != null -> Text("Last sync failed: ${member.lastError}", color = MaterialTheme.colorScheme.error)
                else -> Text("Last synced " + Formatting.relativeTime(member.lastSyncedAt))
            }
        },
        trailingContent = {
            if (canKick) {
                IconButton(onClick = onKick) { Icon(Icons.Default.PersonRemove, contentDescription = "Remove device") }
            }
        },
    )
}

@Composable
private fun InviteSection(code: String, address: String?, port: Int) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(8.dp)) { QrCode(code, Modifier.size(240.dp)) }
        Text(
            if (address != null && port > 0) "Reachable at $address:$port" else "Not on a network right now; the new device will sync when both are online.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Cadence invite", code))) } }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Text("  Copy invite code")
        }
    }
}

@Composable
private fun AddCategoryRow(onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New category") }, singleLine = true, modifier = Modifier.weight(1f))
        IconButton(enabled = name.isNotBlank(), onClick = { onAdd(name.trim()); name = "" }) {
            Icon(Icons.Default.Add, contentDescription = "Add category")
        }
    }
}
