package dk.azp.cadence.ui.tasks

import dk.azp.cadence.data.db.CategoryEntity
import java.text.Collator

enum class TaskSort(val label: String) {
    DEADLINE("By deadline"),
    NAME("By name"),
    CATEGORY("Grouped by category"),
}

/** One line in the task list: a category section header, or a task inside a section or a flat list. */
sealed interface TaskListItem {

    /** Unique within a list. A task with several categories appears once per section, so its key includes the section. */
    val key: String

    /** [collapsible] is false while searching, when every section is shown open regardless of its stored state. */
    data class Section(
        val sectionId: String,
        val title: String,
        val taskCount: Int,
        val overdueCount: Int,
        val collapsed: Boolean,
        val collapsible: Boolean,
    ) : TaskListItem {
        override val key: String get() = "section/$sectionId"
    }

    data class Task(val sectionId: String?, val row: TaskRow) : TaskListItem {
        override val key: String get() = "task/${sectionId.orEmpty()}/${row.task.id}"
    }
}

/** Arranges already filtered tasks for display according to the chosen sort. */
object TaskListLayout {

    /** Section id of tasks without any category; categories use their own id. */
    const val NO_CATEGORY = "no-category"

    private val nameOrder: Comparator<TaskRow> =
        Comparator<TaskRow> { a, b -> Collator.getInstance().compare(a.task.name, b.task.name) }.then(TaskRow.urgencyOrder)

    /**
     * [categories] give the section order. With a [selectedCategory] only its section is shown. Sections in [collapsed]
     * show only their header, except while [searching], so a match is never hidden inside a collapsed section.
     */
    fun build(
        rows: List<TaskRow>,
        sort: TaskSort,
        categories: List<CategoryEntity>,
        selectedCategory: String?,
        collapsed: Set<String>,
        searching: Boolean,
    ): List<TaskListItem> = when (sort) {
        TaskSort.DEADLINE -> rows.sortedWith(TaskRow.urgencyOrder).map { TaskListItem.Task(null, it) }
        TaskSort.NAME -> rows.sortedWith(nameOrder).map { TaskListItem.Task(null, it) }
        TaskSort.CATEGORY -> categorySections(rows, categories, selectedCategory, collapsed, searching)
    }

    private fun categorySections(
        rows: List<TaskRow>,
        categories: List<CategoryEntity>,
        selectedCategory: String?,
        collapsed: Set<String>,
        searching: Boolean,
    ): List<TaskListItem> {
        val knownIds = categories.map { it.id }.toSet()
        val sections = categories
            .filter { selectedCategory == null || it.id == selectedCategory }
            .map { category -> Triple(category.id, category.name, rows.filter { category.id in it.task.categoryIdList() }) }
        val uncategorised = rows.filter { row -> row.task.categoryIdList().none { it in knownIds } }
        val withUncategorised = if (selectedCategory == null) sections + Triple(NO_CATEGORY, "No category", uncategorised) else sections
        return withUncategorised
            .filter { (_, _, sectionRows) -> sectionRows.isNotEmpty() }
            .flatMap { (sectionId, title, sectionRows) ->
                val isCollapsed = sectionId in collapsed && !searching
                val header = TaskListItem.Section(sectionId, title, sectionRows.size, sectionRows.count { it.isOverdue }, isCollapsed, collapsible = !searching)
                val tasks = if (isCollapsed) emptyList() else sectionRows.sortedWith(TaskRow.urgencyOrder).map { TaskListItem.Task(sectionId, it) }
                listOf(header) + tasks
            }
    }
}
