package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.diagnostics.ServiceLifetime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The app supplies real lifetime observations, without command, credential or log text. */
public interface LinuxLifetimesSource {
    public suspend fun read(): Outcome<List<ServiceLifetime>>
}

public data class LinuxLifetimesUi(
    val records: List<ServiceLifetime> = emptyList(),
    val loading: Boolean = false,
    val failure: Failure? = null,
    val read: Boolean = false,
)

/** A visible/manual read only. No polling or unbounded background lifetime is introduced. */
public class LinuxLifetimesModel(
    private val source: LinuxLifetimesSource,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(LinuxLifetimesUi())
    public val state: StateFlow<LinuxLifetimesUi> = mutable.asStateFlow()

    public fun refresh() {
        if (mutable.value.loading) return
        mutable.update { it.copy(loading = true, failure = null) }
        scope.launch {
            when (val result = source.read()) {
                is Outcome.Failed ->
                    mutable.update { it.copy(loading = false, failure = result.failure) }
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            records = result.value,
                            loading = false,
                            failure = null,
                            read = true,
                        )
                    }
            }
        }
    }
}
