package dk.azp.cadence.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.TaskEntity
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Create or edit a task: name, optional cadence in days and any number of categories, including brand new ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(
    title: String,
    initial: TaskEntity?,
    categories: List<CategoryEntity>,
    onCreateCategory: suspend (String) -> String,
    onDismiss: () -> Unit,
    onSave: (name: String, cadenceDays: Int?, categoryIds: List<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var cadence by rememberSaveable { mutableStateOf(initial?.cadenceDays?.toString() ?: "") }
    var selected by rememberSaveable { mutableStateOf(initial?.categoryIdList() ?: emptyList()) }
    var newCategory by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val cadenceValid = cadence.isBlank() || (cadence.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Task name") }, singleLine = true)
                OutlinedTextField(
                    value = cadence,
                    onValueChange = { cadence = it.filter(Char::isDigit) },
                    label = { Text("Repeat every (days)") },
                    supportingText = { Text("Leave empty for no schedule") },
                    singleLine = true,
                    isError = !cadenceValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (categories.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = category.id in selected,
                                onClick = { selected = if (category.id in selected) selected - category.id else selected + category.id },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("New category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = newCategory.isNotBlank(),
                        onClick = {
                            val categoryName = newCategory.trim()
                            newCategory = ""
                            scope.launch { selected = selected + onCreateCategory(categoryName) }
                        },
                    ) { Icon(Icons.Default.Add, contentDescription = "Add category") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && cadenceValid,
                onClick = { onSave(name.trim(), cadence.toIntOrNull(), selected) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks the day a task was done. Future days cannot be selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoneDatePickerDialog(onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val todayMillis = today.toEpochDay() * MILLIS_PER_DAY
    val notInFuture = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = todayMillis, selectableDates = notInFuture)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) } },
            ) { Text("Mark done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MILLIS_PER_DAY = 86_400_000L
