package io.github.eslamasabry.oc.engine.composer.drafts

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.PromptAttachment
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One open chat's autosave (plan 09 §10.1, §10.8): a pause in typing of [delay] (600 ms) saves;
 * [flush] saves now (app pause, leaving, after a send). [settled] tells Back whether the words are
 * on disk; [failure] holds the last refused save so leaving can tell the person (B-STATE-102).
 * Voice dictation never calls [changed] with partial words: only words committed to the field
 * count.
 */
public class DraftAutosave(
    private val scope: CoroutineScope,
    private val drafts: Drafts,
    private val profileId: String,
    private val sessionId: String,
    private val delay: Duration = Drafts.AUTOSAVE_DELAY,
) {
    private class Pending(
        val text: String,
        val attachments: List<PromptAttachment>?,
        val location: DraftLocation?,
        val revision: Long? = null,
    )

    private var pending: Pending? = null
    private var job: Job? = null
    private val saves = Mutex()
    private val _settled = MutableStateFlow(true)
    private val _failure = MutableStateFlow<Failure?>(null)

    public val settled: StateFlow<Boolean> = _settled.asStateFlow()
    public val failure: StateFlow<Failure?> = _failure.asStateFlow()

    internal var onAcknowledged: ((Long, String, Outcome<Unit>) -> Unit)? = null
    internal var canWriteRevision: ((Long) -> Boolean)? = null

    /**
     * The accepted file snapshot a revision save keeps: read at save time, inside the writer
     * fence, so a text autosave never reverts or drops a file edit that already committed.
     */
    internal var currentAttachments: (() -> List<PromptAttachment>)? = null

    internal fun changedRevision(text: String, revision: Long) {
        changedPending(Pending(text, null, null, revision))
    }

    internal fun cancelPending() {
        job?.cancel()
        job = null
        pending = null
    }

    internal suspend fun <T> exclusive(block: suspend () -> T): T = saves.withLock { block() }

    /** The words (and, when they changed, the files) now in the composer. */
    public fun changed(
        text: String,
        attachments: List<PromptAttachment>? = null,
        location: DraftLocation? = null,
    ) {
        val keep = pending?.attachments
        changedPending(Pending(text, attachments?.toList() ?: keep, location ?: pending?.location))
    }

    private fun changedPending(next: Pending) {
        pending = next
        _settled.value = false
        job?.cancel()
        job = scope.launch {
            delay(delay)
            // Only the debounce timer is replaceable; an admitted save must not cancel itself or a
            // predecessor.
            job = null
            savePending()
        }
    }

    /** Saves what is pending now. Ok when nothing was pending. */
    public suspend fun flush(): Outcome<Unit> {
        job?.cancel()
        job = null
        return savePending()
    }

    private suspend fun savePending(): Outcome<Unit> = saves.withLock {
        val next = pending ?: return@withLock Unit.ok()
        // Keep this identity pending through suspension and cancellation; only acknowledgement may
        // remove it.
        val result =
            attempt({ Failure.StorageRefused() }) {
                    if (next.revision != null)
                        drafts.saveRevision(
                            profileId,
                            sessionId,
                            next.text,
                            currentAttachments?.invoke() ?: emptyList(),
                        ) {
                            canWriteRevision?.invoke(next.revision) != false
                        }
                    else
                        drafts.save(
                            profileId,
                            sessionId,
                            next.text,
                            next.attachments,
                            next.location,
                        )
                }
                .flatMap { it }
        next.revision?.let { onAcknowledged?.invoke(it, next.text, result) }
        if (pending === next) {
            if (result is Outcome.Ok) pending = null
            _settled.value = result is Outcome.Ok
            _failure.value = (result as? Outcome.Failed)?.failure
        }
        result
    }
}
