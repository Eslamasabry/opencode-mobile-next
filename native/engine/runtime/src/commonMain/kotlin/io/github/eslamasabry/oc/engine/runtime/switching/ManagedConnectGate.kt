package io.github.eslamasabry.oc.engine.runtime.switching

import io.github.eslamasabry.oc.core.common.Outcome

/*
 * Port of the managed-runtime parts of `_connectProfile` and `_redetectFlavor` (lib/state/connection/connect.dart):
 * a managed local profile whose saved data still belongs to OpenCode 1 must be moved before any transport runs. A
 * legacy reply from the managed address marks the profile durably and is never retried as a legacy runtime. The
 * password is never touched by any of this.
 */

/** What one probe of the address answered (Dart `ServerProbeResult`, only the facts this gate reads). */
public data class ProbeVerdict(val ok: Boolean, val unsupportedLegacy: Boolean = false)

/** The OpenCode 2 transport one connect builds. */
public interface ConnectTransport {
    /** True when health answered healthy. */
    public suspend fun healthy(): Boolean

    public fun close()
}

/** Saves the profile row (Dart `ProfileStore.upsert`); Failed when storage refused. */
public fun interface ProfileWriter {
    public suspend fun upsert(profile: LocalProfile): Outcome<Unit>
}

public sealed interface ConnectVerdict {
    public data class Connected(val transport: ConnectTransport) : ConnectVerdict

    /**
     * `ConnectionController.managedRuntimeMismatchMessage`: the managed phone server needs the move to OpenCode 2
     * first; no transport is kept. [markSaved] is false when the durable mark could not be written.
     */
    public data class MigrationRequired(val profile: LocalProfile, val markSaved: Boolean = true) : ConnectVerdict

    /** The connect failed for another reason (no transport kept). */
    public data object Failed : ConnectVerdict
}

public class ManagedConnectGate(private val writer: ProfileWriter) {
    /** Legacy managed data must be imported before any transport can run (Dart `managedOpenCode2MigrationRequired`). */
    public fun migrationRequired(profile: LocalProfile?): Boolean =
        profile != null &&
            (profile.requiresOpenCode2Migration || profile.openCode2HistoryPending) &&
            ManagedLocalServer.isManagedLocal(profile)

    /**
     * One connect: a persisted legacy mark blocks before [buildTransport] is ever called. Otherwise one transport is
     * built; when its health fails, the address is probed once ([redetect]) and a legacy reply from a managed address
     * marks the profile and ends the connect, never retrying as a legacy runtime.
     */
    public suspend fun connect(
        profile: LocalProfile,
        buildTransport: () -> ConnectTransport,
        probe: suspend () -> ProbeVerdict,
        redetect: Boolean = true,
    ): ConnectVerdict {
        if (profile.requiresOpenCode2Migration && ManagedLocalServer.isManagedLocal(profile)) {
            return ConnectVerdict.MigrationRequired(profile)
        }
        val transport = buildTransport()
        if (transport.healthy()) return ConnectVerdict.Connected(transport)
        transport.close()
        if (!redetect) return ConnectVerdict.Failed
        val answer = probe()
        if (answer.unsupportedLegacy) {
            if (!ManagedLocalServer.isManagedLocal(profile)) return ConnectVerdict.Failed
            val marked = profile.copy(requiresOpenCode2Migration = true)
            val saved = writer.upsert(marked) is Outcome.Ok
            return ConnectVerdict.MigrationRequired(marked, markSaved = saved)
        }
        return ConnectVerdict.Failed
    }
}
