package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The scanner page's words state: the last refused QR, or the problem with pasted text. */
public data class PairingScannerUi(val rejected: Boolean = false, val problem: PairingProblem? = null)

public interface PairingScannerActions {
    public fun close()

    /** A scanned QR the scanner accepted; handled exactly like a pasted code (B-PROTO-069). */
    public fun scanned(text: String)

    /** A QR that is not a pairing code. */
    public fun rejected()

    /** "Paste instead": the clipboard's text (null = empty). */
    public fun pasted(text: String?)
}

/**
 * The pairing scanner (plan 07 E03). The code goes to [PairingInbox] — memory only — and the page closes back to the
 * server form, which fills itself from the inbox. Nothing here keeps or prints the decoded text.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PairingScannerViewModel(private val inbox: PairingInbox, private val nav: AppNavigator) :
    ViewModel(), PairingScannerActions {
    private val mutable = MutableStateFlow(PairingScannerUi())
    public val state: StateFlow<PairingScannerUi> = mutable.asStateFlow()

    /** Pure; called on the scanner's thread for every decoded QR. */
    public fun accepts(text: String): Boolean = PairingText.read(text) !is PairingRead.Problem

    override fun close(): Unit = nav.back()

    override fun scanned(text: String) {
        deliver(PairingText.read(text))
    }

    override fun rejected(): Unit = mutable.update { it.copy(rejected = true) }

    override fun pasted(text: String?) {
        deliver(PairingText.read(text.orEmpty()))
    }

    private fun deliver(read: PairingRead) {
        val fill = PairingFill.of(read)
        if (fill == null) {
            mutable.update { it.copy(problem = (read as? PairingRead.Problem)?.problem ?: PairingProblem.NotJson) }
            return
        }
        inbox.put(fill)
        nav.back()
    }
}
