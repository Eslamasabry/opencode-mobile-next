package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ConfigurationPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Only masked configuration crosses the gateway. This port never writes, copies or shares it. */
public interface ConfigurationInspection {
    public val available: StateFlow<Boolean>
    /**
     * Changes when the selected server, its connection, location or inspection capability changes.
     */
    public val revision: StateFlow<Long>

    public suspend fun read(): Outcome<ConfigurationPreview>
}

public sealed interface ServerConfigurationUi {
    public data object Loading : ServerConfigurationUi

    public data object Unavailable : ServerConfigurationUi

    /**
     * [files] are the configuration files the server read, in its priority order; [folders] are
     * where it looked. [text] is the masked source, shown only under Details.
     */
    public data class Ready(
        val text: String,
        val files: List<String> = emptyList(),
        val folders: List<String> = emptyList(),
    ) : ServerConfigurationUi
}

/**
 * S4 (sweep-b 2026-11-11): the page shows which configuration files exist, never the source as the
 * page. OpenCode 2's `/config` answers one `directory` entry for each folder it searched and a
 * `document` entry only for a real file (w4-git-gate); only `type` and `path` are read here, so a
 * document's contents (its `info`) can never reach the list.
 */
public object ConfigurationSources {
    public fun ready(text: String): ServerConfigurationUi.Ready {
        val sources =
            runCatching {
                    (Json.parseToJsonElement(text) as? JsonObject)?.get("sources") as? JsonArray
                }
                .getOrNull()
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
        fun paths(type: String) =
            sources
                .filter { (it["type"] as? JsonPrimitive)?.contentOrNull == type }
                .mapNotNull { (it["path"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                .distinct()
        return ServerConfigurationUi.Ready(text, paths("document"), paths("directory"))
    }
}

public fun interface ServerConfigurationActions {
    public fun refresh()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ServerConfigurationViewModel(host: SettingsHost) :
    ViewModel(), ServerConfigurationActions {
    private val source = host.configuration
    private val mutable = MutableStateFlow<ServerConfigurationUi>(ServerConfigurationUi.Loading)
    public val state: StateFlow<ServerConfigurationUi> = mutable.asStateFlow()
    private var read: Job? = null
    private var generation = 0L

    init {
        val inspection = source
        if (inspection == null) mutable.value = ServerConfigurationUi.Unavailable
        else viewModelScope.launch { inspection.revision.collect { refresh() } }
    }

    override fun refresh() {
        val inspection = source ?: return
        val ticket = ++generation
        read?.cancel()
        mutable.value = ServerConfigurationUi.Loading
        read = viewModelScope.launch {
            val result = inspection.read()
            if (ticket != generation) return@launch
            mutable.value =
                when (result) {
                    is Outcome.Ok -> ConfigurationSources.ready(result.value.text)
                    is Outcome.Failed -> ServerConfigurationUi.Unavailable
                }
        }
    }
}
