package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the owner last asked the migration to do, so "Try again" repeats it. */
public enum class TermuxMigrationRequest { Check, Start, Resume }

/**
 * The one owner of the move, above every route (Flutter TermuxMigrationOwner): it makes the controller once, runs one
 * operation at a time, and stops the copy when the app leaves the foreground ([onAppLeftForeground]) because Android
 * gives the copy no background lifetime. The page, This phone's row and the Servers offer only read it and ask it to
 * act.
 *
 * It keeps no durable facts of its own: whether a move finished, which providers to sign in to again, and discarding a
 * saved copy are all asked of the controller's journal, so nothing here needs a profile-scoped key or a sweep entry.
 * Call it from one lane (the main thread). [scope] outlives the screens (the app scope).
 */
public class TermuxMigrationOwner(
    private val scope: CoroutineScope,
    private val create: suspend () -> TermuxMigrationController,
) {
    private var made: TermuxMigrationController? = null
    private var inFlight: Job? = null
    private var disposed = false
    private val listeners = ArrayList<() -> Unit>()
    private val providerNameMemory = HashMap<String, List<String>>()
    private val controllerListener: () -> Unit = { changed() }

    /** The source profile the last operation ran for. */
    public var source: String? = null
        private set

    /** The last operation asked for. */
    public var request: TermuxMigrationRequest? = null
        private set

    /** The items of the running or last copy, in the controller's own order (by name). */
    public var items: List<TermuxMigrationItem> = emptyList()
        private set

    /** The copy stopped because the app left the foreground. */
    public var stoppedByLeaving: Boolean = false
        private set

    public val controller: TermuxMigrationController? get() = made

    public val snapshot: TermuxMigrationSnapshot? get() = made?.snapshot

    /** An operation runs (true until it has settled, even after Stop). */
    public val busy: Boolean get() = inFlight != null

    /** A copy (not only a check) runs. */
    public val copying: Boolean get() = busy && request != TermuxMigrationRequest.Check

    public fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    public fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun changed() {
        if (!disposed) for (listener in listeners.toList()) listener()
    }

    /** The controller, made once; null when this phone cannot make one (private storage unreadable). Retried later. */
    public suspend fun obtain(): TermuxMigrationController? {
        made?.let { return it }
        return try {
            val controller = create()
            if (disposed) {
                controller.dispose()
                null
            } else {
                made = controller.also { it.addListener(controllerListener) }
                controller
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    public companion object {
        /** Items in the controller's own order. */
        public fun ordered(items: Iterable<TermuxMigrationItem>): List<TermuxMigrationItem> = items.sortedBy { it.name }
    }

    private fun run(sourceId: String, what: TermuxMigrationRequest, op: suspend (TermuxMigrationController) -> Unit): Job? {
        val c = made ?: return null
        inFlight?.let { return it }
        source = sourceId
        request = what
        if (what != TermuxMigrationRequest.Check) stoppedByLeaving = false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                op(c)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The controller publishes every failure as a fixed state.
            }
            // Busy ends only when the backend says it settled, which can come after the cancelled state.
            c.awaitSettled()
        }
        inFlight = job
        job.invokeOnCompletion {
            if (inFlight === job) inFlight = null
            changed()
        }
        changed()
        job.start()
        return job
    }

    /** A fresh look at Termux and the phone: sizes, free space for [selected], the in-app server. */
    public suspend fun check(sourceId: String, selected: Set<TermuxMigrationItem> = emptySet()) {
        run(sourceId, TermuxMigrationRequest.Check) { it.check(selected) }?.join()
    }

    /** Starts copying [selected] from [sourceId]. */
    public suspend fun start(sourceId: String, selected: Set<TermuxMigrationItem>) {
        if (busy) return
        items = ordered(selected)
        val chosen = items.toSet()
        run(sourceId, TermuxMigrationRequest.Start) { it.start(sourceId, chosen) }?.join()
    }

    /** Resumes the saved copy with its saved selection; with nothing saved, starts [fallback] again instead. */
    public suspend fun resume(sourceId: String, fallback: Set<TermuxMigrationItem> = emptySet()) {
        val c = made ?: return
        if (busy) return
        val saved = c.savedSelection(sourceId).valueOrNull()
        if (saved == null && fallback.isNotEmpty()) return start(sourceId, fallback)
        if (saved != null) items = ordered(saved)
        run(sourceId, TermuxMigrationRequest.Resume) { it.resume(sourceId) }?.join()
    }

    /** The saved selection of an unfinished (or finished) copy; null when none was saved or it cannot be read. */
    public suspend fun savedSelection(sourceId: String): Set<TermuxMigrationItem>? = made?.savedSelection(sourceId)?.valueOrNull()

    /** Stops the running operation at once; [leaving] says the app left the foreground. Await [settled] after. */
    public suspend fun cancel(leaving: Boolean = false) {
        val c = made ?: return
        if (!busy) return
        if (leaving) stoppedByLeaving = true
        c.cancel()
        changed()
    }

    /** Completes when no operation runs. */
    public suspend fun settled() {
        inFlight?.join()
    }

    /** The app left the foreground (ON_STOP): a running copy stops, and Resume picks up from here. */
    public fun onAppLeftForeground() {
        if (copying) scope.launch { cancel(leaving = true) }
    }

    /** The finished move from [profileId] (the journal's record), or null. Refused when the record cannot be read. */
    public suspend fun completedJob(profileId: String): MigrationAnswer<TermuxMigrationCompletedJob?> =
        made?.completedJob(profileId) ?: MigrationAnswer.Ok(null)

    /** The AI providers named in the imported settings copy of [profileId], offline. Empty means unknown. Memory only. */
    public suspend fun providerNames(profileId: String): List<String> {
        val c = made ?: return providerNameMemory[profileId] ?: emptyList()
        return c.providerNames(profileId).also { providerNameMemory[profileId] = it }
    }

    /** The reviewed space for [selected] from the last look, or null when there was none or it is refused. */
    public fun reviewSpace(selected: Set<TermuxMigrationItem>): TermuxMigrationSpace? = made?.reviewSpace(selected)?.valueOrNull()

    /**
     * Throws away the saved copy of [sourceId] (only temporary transfer files go; what was imported and Termux stay).
     * Stops and waits for a running copy first and holds the owner's guard through the removal. Null when it could not
     * be removed (it stays and can be retried).
     */
    public suspend fun discard(sourceId: String): TermuxMigrationDiscardResult? {
        val c = made ?: return null
        if (busy) {
            cancel()
            settled()
        }
        inFlight?.join()
        source = sourceId
        var result: TermuxMigrationDiscardResult? = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            result = c.discardSavedCopy(sourceId).valueOrNull()
            c.awaitSettled()
        }
        inFlight = job
        job.invokeOnCompletion {
            if (inFlight === job) inFlight = null
            changed()
        }
        changed()
        job.start()
        job.join()
        if (result == TermuxMigrationDiscardResult.Discarded || result == TermuxMigrationDiscardResult.NothingSaved) {
            source = null
            request = null
            items = emptyList()
            stoppedByLeaving = false
            providerNameMemory.remove(sourceId)
            changed()
        }
        return result
    }

    /** Disposes the controller once its running operation settled. */
    public fun dispose() {
        disposed = true
        val c = made ?: return
        made = null
        c.removeListener(controllerListener)
        val running = inFlight
        if (running == null) {
            c.dispose()
        } else {
            scope.launch {
                c.cancel()
                running.join()
                c.dispose()
            }
        }
    }
}
