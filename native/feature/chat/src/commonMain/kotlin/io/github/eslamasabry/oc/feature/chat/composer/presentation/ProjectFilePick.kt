package io.github.eslamasabry.oc.feature.chat.composer.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * S28 story 12 (Flutter `KitProjectFileList`): the "File from the project" sheet's search. Empty words show the hint;
 * a newer search wins over an older one still on the wire; a failed search says so and keeps the words for Try again.
 * Folders are not offered (the search lists files); a pick inserts `@path` and stages the file like the `@` strip.
 */
public data class ProjectFilePickUi(
    val query: String = "",
    val paths: List<String> = emptyList(),
    val searching: Boolean = false,
    val failed: Boolean = false,
)

public class ProjectFilePick(private val search: suspend (String) -> Outcome<List<String>>) {
    private val mutable = MutableStateFlow(ProjectFilePickUi())
    public val state: StateFlow<ProjectFilePickUi> = mutable.asStateFlow()
    private var generation = 0L

    public suspend fun run(words: String) {
        val query = words.trim()
        val mine = ++generation
        if (query.isEmpty()) {
            mutable.value = ProjectFilePickUi()
            return
        }
        mutable.update { it.copy(query = query, searching = true, failed = false) }
        val found = search(query)
        if (mine != generation) return
        mutable.value =
            when (found) {
                is Outcome.Ok ->
                    ProjectFilePickUi(
                        query,
                        found.value.filter { it.isNotBlank() && !it.endsWith("/") }.distinct().take(MAX_SHOWN),
                    )
                is Outcome.Failed -> ProjectFilePickUi(query, failed = true)
            }
    }

    private companion object {
        const val MAX_SHOWN = 50
    }
}
