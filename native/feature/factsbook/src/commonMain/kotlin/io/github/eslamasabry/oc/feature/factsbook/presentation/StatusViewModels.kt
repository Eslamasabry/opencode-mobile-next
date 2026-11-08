package io.github.eslamasabry.oc.feature.factsbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.factsbook.ControlVerification
import io.github.eslamasabry.oc.engine.factsbook.FactsBookCapabilities
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLink
import io.github.eslamasabry.oc.engine.factsbook.FactsBookSettings
import io.github.eslamasabry.oc.engine.factsbook.FbResult
import io.github.eslamasabry.oc.engine.factsbook.NodeStatus
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

private val unreachableErr = FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))

// ---- Status -------------------------------------------------------------------------------------------------------

public data class StatusUi(
    val status: NodeStatus? = null,
    /** Null: not checked (a node that will not show its history, or no pin). Never read as verified. */
    val control: ControlVerification? = null,
    val error: FactsBookError? = null,
    val loading: Boolean = true,
    val canApprove: Boolean = false,
    val link: FactsBookLink? = null,
    val capabilities: FactsBookCapabilities = FactsBookCapabilities.B5,
    val confirmDisconnect: Boolean = false,
    val nowMs: Long = 0,
    /** Memory on this phone can be reached from here. */
    val phoneNode: Boolean = false,
)

public interface StatusActions {
    public fun back()

    public fun reload()

    public fun setUpApproval()

    public fun askDisconnect()

    public fun disconnect()

    public fun cancelDisconnect()

    public fun openPhoneNode()
}

/**
 * Status (spec 60 screen 29, F: status_screen.dart): one row per module with its state as a word, never one green
 * "connected"; the signed history checked against the fingerprint pinned at pairing; Disconnect, which asks first.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class StatusViewModel(private val access: FactsBookAccess, private val nav: AppNavigator, private val clock: Clock) :
    ViewModel(), StatusActions {
    private val mutable = MutableStateFlow(StatusUi())
    public val state: StateFlow<StatusUi> = mutable.asStateFlow()

    init {
        reload()
    }

    override fun back(): Unit = nav.back()

    override fun reload() {
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val engine = access.engine
            engine.open(access.activeProfileId.value)
            val r = engine.node?.status() ?: unreachableErr
            val s = engine.state.value
            mutable.update {
                it.copy(
                    status = (r as? FbResult.Ok)?.value ?: it.status,
                    error = (r as? FbResult.Err)?.error,
                    canApprove = s.canApprove,
                    link = s.link,
                    capabilities = s.capabilities,
                    nowMs = clock.now().toEpochMilliseconds(),
                    phoneNode = access.phoneNode.supported,
                )
            }
            val control = if (r is FbResult.Ok) (engine.verifyControl() as? FbResult.Ok)?.value else null
            mutable.update { it.copy(control = control, loading = false) }
        }
    }

    override fun setUpApproval(): Unit = nav.open(FactsBookRoute.Connect)

    override fun askDisconnect(): Unit = mutable.update { it.copy(confirmDisconnect = true) }

    override fun cancelDisconnect(): Unit = mutable.update { it.copy(confirmDisconnect = false) }

    override fun openPhoneNode(): Unit = nav.open(FactsBookRoute.PhoneNode)

    override fun disconnect() {
        mutable.update { it.copy(confirmDisconnect = false) }
        viewModelScope.launch {
            access.engine.disconnect()
            nav.land()
        }
    }
}

// ---- Settings -----------------------------------------------------------------------------------------------------

public data class SettingsUi(
    val settings: FactsBookSettings? = null,
    val error: FactsBookError? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** A change that did not go through. */
    val failure: FactsBookError? = null,
    val canApprove: Boolean = false,
    val showTriggers: Boolean = false,
    /** This phone's IANA time zone. */
    val phoneZone: String? = null,
)

public interface SettingsActions {
    public fun back()

    public fun reload()

    public fun setUpApproval()

    public fun setBriefCap(cap: Long)

    public fun usePhoneZone()

    public fun openAreas()

    public fun openTriggers()

    public fun dismiss()
}

/**
 * Memory settings (F: settings_screen.dart): the brief's cap (changed with this phone's approval), its time and quiet
 * hours, how long the locked folder stays open, the home time zone (switched to this phone's with approval), the
 * spending limit, and the ways into area sharing and triggers.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class SettingsViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), SettingsActions {
    private val mutable = MutableStateFlow(SettingsUi(phoneZone = TimeZone.currentSystemDefault().id.takeIf { "/" in it }))
    public val state: StateFlow<SettingsUi> = mutable.asStateFlow()

    init {
        reload()
    }

    override fun back(): Unit = nav.back()

    override fun reload() {
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            val r = access.engine.node?.settings() ?: unreachableErr
            val s = access.engine.state.value
            mutable.update {
                it.copy(
                    settings = (r as? FbResult.Ok)?.value ?: it.settings,
                    error = (r as? FbResult.Err)?.error,
                    loading = false,
                    canApprove = s.canApprove,
                    showTriggers = s.capabilities.supports(FactsBookCapabilities.TRIGGERS),
                )
            }
        }
    }

    override fun setUpApproval(): Unit = nav.open(FactsBookRoute.Connect)

    override fun setBriefCap(cap: Long) {
        val now = mutable.value
        val settings = now.settings ?: return
        if (!now.canApprove || now.saving || cap == settings.brief.capPerDay) return
        save { node -> node.setBriefCap(settings, cap) }
    }

    override fun usePhoneZone() {
        val now = mutable.value
        val settings = now.settings ?: return
        val zone = now.phoneZone ?: return
        if (!now.canApprove || now.saving || zone == settings.vaultZone) return
        save { node -> node.setHomeZone(settings, zone) }
    }

    private fun save(call: suspend (io.github.eslamasabry.oc.engine.factsbook.FactsBookGateway) -> FbResult<*>) {
        mutable.update { it.copy(saving = true, failure = null) }
        viewModelScope.launch {
            val r = access.engine.node?.let { call(it) } ?: unreachableErr
            mutable.update { it.copy(saving = false, failure = (r as? FbResult.Err)?.error) }
            // A change that went through, or one based on an old copy, reads the settings again.
            if (r is FbResult.Ok || (r as? FbResult.Err)?.error?.stale == true) reload()
        }
    }

    override fun openAreas(): Unit = nav.open(FactsBookRoute.Areas)

    override fun openTriggers(): Unit = nav.open(FactsBookRoute.Triggers)

    override fun dismiss(): Unit = mutable.update { it.copy(failure = null) }
}
