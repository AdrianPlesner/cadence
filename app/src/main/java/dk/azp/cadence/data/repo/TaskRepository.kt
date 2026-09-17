package dk.azp.cadence.data.repo

import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.CompletionEntity
import dk.azp.cadence.data.db.TaskEntity
import dk.azp.cadence.data.db.TaskWithLastDone
import dk.azp.cadence.data.sync.CategoryPayload
import dk.azp.cadence.data.sync.ChangeEngine
import dk.azp.cadence.data.sync.CompletionPayload
import dk.azp.cadence.data.sync.EntityType
import dk.azp.cadence.data.sync.TaskPayload
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/** The user-editable part of a task. */
data class TaskEdit(val name: String, val cadenceDays: Int?, val categoryIds: List<String>, val notifyWhenDue: Boolean) {

    fun toPayload(): TaskPayload = TaskPayload(name, cadenceDays, categoryIds, notifyWhenDue = notifyWhenDue && cadenceDays != null)
}

class TaskRepository(
    private val db: CadenceDatabase,
    private val engine: ChangeEngine,
) {

    fun observeTasks(groupId: String): Flow<List<TaskWithLastDone>> = db.taskDao().observeForGroup(groupId)

    fun observeTask(taskId: String): Flow<TaskEntity?> = db.taskDao().observe(taskId)

    fun observeCategories(groupId: String): Flow<List<CategoryEntity>> = db.categoryDao().observeForGroup(groupId)

    fun observeCompletions(taskId: String): Flow<List<CompletionEntity>> = db.completionDao().observeForTask(taskId)

    suspend fun addTask(groupId: String, edit: TaskEdit): String {
        val taskId = UUID.randomUUID().toString()
        engine.record(groupId, EntityType.TASK, taskId, edit.toPayload())
        return taskId
    }

    suspend fun updateTask(taskId: String, edit: TaskEdit) {
        val task = db.taskDao().get(taskId) ?: return
        engine.record(task.groupId, EntityType.TASK, taskId, edit.toPayload())
    }

    suspend fun deleteTask(taskId: String) {
        val task = db.taskDao().get(taskId) ?: return
        val payload = TaskPayload(task.name, task.cadenceDays, task.categoryIdList(), task.notifyWhenDue, deleted = true)
        engine.record(task.groupId, EntityType.TASK, taskId, payload)
    }

    suspend fun addCategory(groupId: String, name: String): String {
        val categoryId = UUID.randomUUID().toString()
        engine.record(groupId, EntityType.CATEGORY, categoryId, CategoryPayload(name))
        return categoryId
    }

    suspend fun renameCategory(categoryId: String, name: String) {
        val category = db.categoryDao().get(categoryId) ?: return
        engine.record(category.groupId, EntityType.CATEGORY, categoryId, CategoryPayload(name))
    }

    suspend fun deleteCategory(categoryId: String) {
        val category = db.categoryDao().get(categoryId) ?: return
        engine.record(category.groupId, EntityType.CATEGORY, categoryId, CategoryPayload(category.name, deleted = true))
    }

    suspend fun markDone(taskId: String, date: LocalDate) {
        val task = db.taskDao().get(taskId) ?: return
        val completionId = UUID.randomUUID().toString()
        engine.record(task.groupId, EntityType.COMPLETION, completionId, CompletionPayload(taskId, date.toEpochDay()))
    }

    suspend fun deleteCompletion(completionId: String) {
        val completion = db.completionDao().get(completionId) ?: return
        engine.record(completion.groupId, EntityType.COMPLETION, completionId, CompletionPayload(completion.taskId, completion.doneDate, deleted = true))
    }
}
