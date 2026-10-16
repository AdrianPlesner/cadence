package dk.azp.cadence.ui.tasks

import android.content.Context

/** How each group's task list is shown on this device. Local view settings, deliberately not synced. */
class TaskListPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("task_list", Context.MODE_PRIVATE)

    fun sort(groupId: String): TaskSort =
        prefs.getString(sortKey(groupId), null)?.let { name -> TaskSort.entries.firstOrNull { it.name == name } } ?: TaskSort.DEADLINE

    fun setSort(groupId: String, sort: TaskSort) {
        prefs.edit().putString(sortKey(groupId), sort.name).apply()
    }

    fun collapsedSections(groupId: String): Set<String> = prefs.getStringSet(collapsedKey(groupId), null)?.toSet() ?: emptySet()

    fun setCollapsedSections(groupId: String, sectionIds: Set<String>) {
        prefs.edit().putStringSet(collapsedKey(groupId), sectionIds).apply()
    }

    private fun sortKey(groupId: String) = "sort/$groupId"

    private fun collapsedKey(groupId: String) = "collapsed/$groupId"
}
