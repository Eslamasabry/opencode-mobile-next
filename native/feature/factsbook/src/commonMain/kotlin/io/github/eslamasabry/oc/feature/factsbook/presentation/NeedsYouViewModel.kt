package io.github.eslamasabry.oc.feature.factsbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveState
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.FbResult
import io.github.eslamasabry.oc.engine.factsbook.ReviewDecision
import io.github.eslamasabry.oc.engine.factsbook.ReviewItem
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a decision came to, worded by the screen (F: memory_care_screen.dart `_notice`). */
public enum class CareNotice { Approved, Rejected, Skipped, Stale }

public data class NeedsYouUi(
    val review: ImmutableList<ReviewItem> = persistentListOf(),
    val notSaved: Int = 0,
    /** This phone signs decisions with its owner key; false until approval is set up. */
    val canApprove: Boolean = false,
    /** The cards whose answer is on its way. */
    val working: ImmutableSet<String> = persistentSetOf(),
    val notice: CareNotice? = null,
    /** A decision that did not go through (not stale): worded with Details. */
    val failure: FactsBookError? = null,
    val load: FactsBookLoad = FactsBookLoad.Idle,
    val error: FactsBookError? = null,
)

public interface NeedsYouActions {
    public fun back()

    public fun refresh()

    public fun openNotSaved()

    public fun setUpApproval()

    public fun approve(item: ReviewItem)

    public fun reject(item: ReviewItem)

    public fun skip(item: ReviewItem)

    /** Approve with the person's own wording of the proposed text. */
    public fun approveEdited(item: ReviewItem, text: String)

    public fun dismiss()
}

/**
 * Needs you: one page for changes to approve and notes not saved yet (F: memory_care_screen.dart). A decision is signed
 * by this phone's owner key after the screen lock, bound to the exact proposal; one that changed since fails as stale
 * and the page shows the fresh list. Nothing is applied by looking.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class NeedsYouViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), NeedsYouActions {
    private data class Local(
        val working: Set<String> = emptySet(),
        val notice: CareNotice? = null,
        val failure: FactsBookError? = null,
    )

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<NeedsYouUi> =
        combine(access.engine.state, local) { s, own ->
                NeedsYouUi(
                    review = s.review.toImmutableList(),
                    notSaved = s.pending.count { it.state != CouldNotSaveState.Discarded },
                    canApprove = s.canApprove,
                    working = own.working.toImmutableSet(),
                    notice = own.notice,
                    failure = own.failure,
                    load = s.load,
                    error = s.error,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NeedsYouUi())

    init {
        refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            access.engine.refresh()
        }
    }

    override fun openNotSaved(): Unit = nav.open(FactsBookRoute.NotSaved)

    override fun setUpApproval(): Unit = nav.open(FactsBookRoute.Connect)

    override fun approve(item: ReviewItem): Unit = decide(item, ReviewDecision.Approve)

    override fun reject(item: ReviewItem): Unit = decide(item, ReviewDecision.Reject)

    override fun skip(item: ReviewItem): Unit = decide(item, ReviewDecision.Skip)

    override fun approveEdited(item: ReviewItem, text: String) {
        val edited = text.trim()
        if (edited.isEmpty()) return
        decide(item, ReviewDecision.Edit, JsonObject(item.proposal + ("text" to JsonPrimitive(edited))))
    }

    private fun decide(item: ReviewItem, decision: ReviewDecision, edited: JsonObject? = null) {
        if (item.id in local.value.working || !state.value.canApprove) return
        local.update { it.copy(working = it.working + item.id, notice = null, failure = null) }
        viewModelScope.launch {
            val r = access.engine.decide(item, decision, edited)
            local.update {
                val done = it.copy(working = it.working - item.id)
                when (r) {
                    is FbResult.Ok ->
                        done.copy(
                            notice =
                                when (decision) {
                                    ReviewDecision.Approve, ReviewDecision.Edit -> CareNotice.Approved
                                    ReviewDecision.Reject -> CareNotice.Rejected
                                    ReviewDecision.Skip -> CareNotice.Skipped
                                }
                        )
                    is FbResult.Err -> if (r.error.stale) done.copy(notice = CareNotice.Stale) else done.copy(failure = r.error)
                }
            }
        }
    }

    override fun dismiss(): Unit = local.update { it.copy(notice = null, failure = null) }
}
