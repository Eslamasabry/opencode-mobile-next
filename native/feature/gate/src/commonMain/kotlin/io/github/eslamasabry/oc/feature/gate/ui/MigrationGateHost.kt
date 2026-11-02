package io.github.eslamasabry.oc.feature.gate.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.engine.migration.MigrationEngine
import io.github.eslamasabry.oc.feature.gate.EngineGateActions
import io.github.eslamasabry.oc.feature.gate.GatePlatform
import io.github.eslamasabry.oc.feature.gate.GatePresenter
import io.github.eslamasabry.oc.feature.gate.gateInputOf
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The update gate over the app (P12-MIGRATION). Until the migration gate opens it draws Opening, "Updating your app…"
 * or a failure screen with every way out as a row; then [content] (the app) with the one-time re-enter sheet and the
 * deferred-features notice. The caller provides the kit platform and theme (`:androidApp` MigrationWiring's
 * `MigrationRoot`, around `App(...)`).
 */
@Composable
public fun MigrationGateHost(engine: MigrationEngine, gatePlatform: GatePlatform, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val presenter =
        remember(engine, gatePlatform) {
            val input =
                engine.view.map(::gateInputOf).stateIn(scope, SharingStarted.Eagerly, gateInputOf(engine.view.value))
            GatePresenter(input, EngineGateActions(engine, gatePlatform), scope)
        }
    // One host for the gate's own confirmation sheets (Restore, Start fresh) on the failure screen.
    KitSheetHost(Modifier.fillMaxSize()) { UpdateGate(presenter, content) }
}
