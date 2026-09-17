package dk.azp.cadence.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dk.azp.cadence.data.repo.GroupRepository
import dk.azp.cadence.data.sync.Invite
import dk.azp.cadence.data.sync.SyncManager
import dk.azp.cadence.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class JoinGroupViewModel(
    private val groupRepository: GroupRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val errorFlow = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = errorFlow

    fun join(code: String, onJoined: (String) -> Unit) {
        val invite = Invite.parse(code)
        if (invite == null) {
            errorFlow.value = "That is not a Cadence invite code."
        } else {
            viewModelScope.launch {
                val group = groupRepository.joinGroup(invite)
                syncManager.syncWithInviteHost(group, invite)
                onJoined(group.id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(onBack: () -> Unit, onJoined: (String) -> Unit) {
    val viewModel = appViewModel { JoinGroupViewModel(it.groupRepository, it.syncManager) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    var pasted by rememberSaveable { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.join(it, onJoined) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join group") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Open the group on a device that is already a member, go to its overview and scan the QR code shown there.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = { scanner.launch(scanOptions()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("  Scan QR code")
            }
            Text("or paste an invite code", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("Invite code") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedButton(onClick = { viewModel.join(pasted, onJoined) }, enabled = pasted.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Join")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun scanOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt("Scan the group's QR code")
    .setBeepEnabled(false)
    .setOrientationLocked(false)
