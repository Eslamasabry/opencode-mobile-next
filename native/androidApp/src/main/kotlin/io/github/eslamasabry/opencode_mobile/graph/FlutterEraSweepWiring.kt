package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.migration.android.AndroidMigration
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraCopy
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraCopyKind
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraRead
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraValue
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.UndecodableKind
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.FlutterEraSweep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.engine.profiles.RetainedCopies
import io.github.eslamasabry.oc.engine.profiles.RetainedCopy
import io.github.eslamasabry.oc.engine.profiles.RetainedCopyKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * E00-S08: forgetting a server also removes it from the Flutter-era copies the update keeps (the live
 * flutter_secure_storage file, the S0 backup and any Start fresh copy). `:android:migration` edits the files and
 * `:engine:profiles`' [FlutterEraSweep] decides what goes; this file only connects the two (neither may depend on the
 * other). On the preview package there are no such files and the sweep removes nothing.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class FlutterEraSweepCleaner(application: Application, dispatchers: AppDispatchers) : ProfileResourceCleaner {
    private val io = dispatchers.io
    private val sweep =
        FlutterEraSweep(
            RetainedCopies {
                val copies = AndroidMigration.flutterEraCopies(application) ?: return@RetainedCopies emptyList<RetainedCopy>().ok()
                attempt({ Failure.StorageRefused() }) { withContext(io) { copies.list() }.map { AndroidRetainedCopy(it, io) } }
            },
        )

    override val owner: String = sweep.owner
    override val order: Int = sweep.order

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> = sweep.clear(profileId, options)
}

private class AndroidRetainedCopy(private val copy: FlutterEraCopy, private val io: CoroutineDispatcher) : RetainedCopy {
    override val label: String = copy.label
    override val kind: RetainedCopyKind =
        when (copy.kind) {
            FlutterEraCopyKind.Prefs -> RetainedCopyKind.Prefs
            FlutterEraCopyKind.SecureStorage -> RetainedCopyKind.SecureStorage
        }

    override suspend fun read(): Outcome<Map<String, PrefValue>?> {
        val read = attempt({ Failure.StorageRefused() }) { withContext(io) { copy.read() } }
        return when (read) {
            is Outcome.Failed -> read
            is Outcome.Ok ->
                when (val r = read.value) {
                    FlutterEraRead.Missing -> null.ok()
                    // Unknown is never empty: the deletion stops here and keeps the server, so it can be retried.
                    FlutterEraRead.Unreadable -> Failure.StorageRefused().failed()
                    is FlutterEraRead.Entries -> r.entries.mapValues { toPref(it.value) }.ok()
                }
        }
    }

    override suspend fun rewrite(entries: Map<String, PrefValue>): Outcome<Unit> {
        val wanted = entries.mapValues { toEra(it.value) }
        val written = attempt({ Failure.StorageRefused() }) { withContext(io) { copy.rewrite(wanted) } }
        return when (written) {
            is Outcome.Failed -> written
            is Outcome.Ok -> if (written.value) Unit.ok() else Failure.StorageRefused().failed()
        }
    }

    private fun toPref(value: FlutterEraValue): PrefValue =
        when (value) {
            is FlutterEraValue.Bool -> PrefValue.Bool(value.value)
            is FlutterEraValue.Int64 -> PrefValue.Int64(value.value)
            is FlutterEraValue.Dbl -> PrefValue.Dbl(value.value)
            is FlutterEraValue.Str -> PrefValue.Str(value.value)
            is FlutterEraValue.StrList -> PrefValue.StrList(value.value)
            FlutterEraValue.Opaque -> OPAQUE
        }

    private fun toEra(value: PrefValue): FlutterEraValue =
        when (value) {
            is PrefValue.Bool -> FlutterEraValue.Bool(value.value)
            is PrefValue.Int64 -> FlutterEraValue.Int64(value.value)
            is PrefValue.Dbl -> FlutterEraValue.Dbl(value.value)
            is PrefValue.Str -> FlutterEraValue.Str(value.value)
            is PrefValue.StrList -> FlutterEraValue.StrList(value.value)
            is PrefValue.Undecodable -> FlutterEraValue.Opaque
        }

    private companion object {
        /** Ciphertext and values Dart could not read: carried through the edit unchanged, never rewritten. */
        val OPAQUE: PrefValue = PrefValue.Undecodable(UndecodableKind.UnknownType)
    }
}
