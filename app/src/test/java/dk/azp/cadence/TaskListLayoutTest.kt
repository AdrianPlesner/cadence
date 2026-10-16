package dk.azp.cadence

import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.TaskEntity
import dk.azp.cadence.ui.tasks.TaskListItem
import dk.azp.cadence.ui.tasks.TaskListLayout
import dk.azp.cadence.ui.tasks.TaskRow
import dk.azp.cadence.ui.tasks.TaskSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListLayoutTest {

    private val garden = CategoryEntity("garden", "group", "Garden", "", false)
    private val kitchen = CategoryEntity("kitchen", "group", "Kitchen", "", false)
    private val categories = listOf(garden, kitchen)

    private fun row(name: String, daysUntilDue: Long?, vararg categoryIds: String) = TaskRow(
        task = TaskEntity(name, "group", name, null, categoryIds.joinToString(","), false, null, "", "", false),
        categoryNames = emptyList(),
        lastDone = null,
        daysSince = if (daysUntilDue == null) null else 0L,
        daysUntilDue = daysUntilDue,
    )

    private val herbs = row("Water herbs", -2, "garden", "kitchen")
    private val lawn = row("Mow lawn", 3, "garden")
    private val fridge = row("Clean fridge", 1, "kitchen")
    private val bins = row("Bins", 5)
    private val all = listOf(lawn, bins, herbs, fridge)

    private fun build(sort: TaskSort, selected: String? = null, collapsed: Set<String> = emptySet(), searching: Boolean = false) =
        TaskListLayout.build(all, sort, categories, selected, collapsed, searching)

    /** Renders a layout as "[Section]" and task names, for readable assertions. */
    private fun describe(items: List<TaskListItem>): List<String> = items.map {
        when (it) {
            is TaskListItem.Section -> "[${it.title} ${it.taskCount}${if (it.collapsed) " collapsed" else ""}]"
            is TaskListItem.Task -> it.row.task.name
        }
    }

    @Test
    fun deadlineSortPutsOverdueFirstThenSoonest() {
        assertEquals(listOf("Water herbs", "Clean fridge", "Mow lawn", "Bins"), describe(build(TaskSort.DEADLINE)))
    }

    @Test
    fun nameSortIsAlphabetical() {
        assertEquals(listOf("Bins", "Clean fridge", "Mow lawn", "Water herbs"), describe(build(TaskSort.NAME)))
    }

    @Test
    fun groupingShowsTasksInEveryCategoryAndUncategorisedLast() {
        assertEquals(
            listOf("[Garden 2]", "Water herbs", "Mow lawn", "[Kitchen 2]", "Water herbs", "Clean fridge", "[No category 1]", "Bins"),
            describe(build(TaskSort.CATEGORY)),
        )
    }

    @Test
    fun keysStayUniqueWhenATaskAppearsInSeveralSections() {
        val keys = build(TaskSort.CATEGORY).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun collapsedSectionKeepsItsHeaderAndCount() {
        assertEquals(
            listOf("[Garden 2 collapsed]", "[Kitchen 2]", "Water herbs", "Clean fridge", "[No category 1]", "Bins"),
            describe(build(TaskSort.CATEGORY, collapsed = setOf("garden"))),
        )
    }

    @Test
    fun searchingExpandsCollapsedSectionsAndLocksThem() {
        val items = build(TaskSort.CATEGORY, collapsed = setOf("garden"), searching = true)
        assertTrue(describe(items).contains("Mow lawn"))
        assertTrue(items.filterIsInstance<TaskListItem.Section>().none { it.collapsible })
    }

    @Test
    fun selectedCategoryShowsOnlyItsSection() {
        val inGarden = listOf(herbs, lawn)
        assertEquals(
            listOf("[Garden 2]", "Water herbs", "Mow lawn"),
            describe(TaskListLayout.build(inGarden, TaskSort.CATEGORY, categories, "garden", emptySet(), false)),
        )
    }

    @Test
    fun emptySectionsAreLeftOut() {
        assertEquals(listOf("[Garden 1]", "Mow lawn"), describe(TaskListLayout.build(listOf(lawn), TaskSort.CATEGORY, categories, null, emptySet(), false)))
    }

    @Test
    fun sectionCountsOverdueTasks() {
        val gardenHeader = build(TaskSort.CATEGORY).first() as TaskListItem.Section
        assertEquals(1, gardenHeader.overdueCount)
    }

    @Test
    fun taskWithOnlyDeletedCategoriesCountsAsUncategorised() {
        val orphan = row("Orphan", null, "deleted-category")
        assertEquals(listOf("[No category 1]", "Orphan"), describe(TaskListLayout.build(listOf(orphan), TaskSort.CATEGORY, categories, null, emptySet(), false)))
    }
}
