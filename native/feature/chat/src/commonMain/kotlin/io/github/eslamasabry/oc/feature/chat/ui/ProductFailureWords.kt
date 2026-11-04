package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.connection.ProductErrorLine
import io.github.eslamasabry.oc.engine.connection.productErrorText
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The visible send failure uses only the engine's closed category; protocol prose stays in Details.
 */
@Composable
internal fun rememberProductFailureWords(): (Failure) -> String {
    val words =
        ProductErrorLine.entries.associateWith { line ->
            stringResource(
                when (line) {
                    ProductErrorLine.Unreachable -> Res.string.e7SharedOpenCodeUnreachableTryAgain
                    ProductErrorLine.TimedOut -> Res.string.productErrorTimedOut
                    ProductErrorLine.Certificate -> Res.string.productErrorCertificate
                    ProductErrorLine.SignIn -> Res.string.productErrorSignIn
                    ProductErrorLine.NotFound -> Res.string.productErrorNotFound
                    ProductErrorLine.Conflict -> Res.string.productErrorConflict
                    ProductErrorLine.Busy -> Res.string.productErrorBusy
                    ProductErrorLine.Server -> Res.string.serverFailureServer
                    ProductErrorLine.Rejected -> Res.string.productErrorRejected
                    ProductErrorLine.UnreadableReply -> Res.string.productErrorUnreadableReply
                    ProductErrorLine.StagedRevert -> Res.string.productErrorStagedRevert
                    ProductErrorLine.Storage -> Res.string.productErrorStorage
                    ProductErrorLine.Secret -> Res.string.serverFailureSecret
                    ProductErrorLine.Device -> Res.string.productErrorDevice
                    ProductErrorLine.Termux -> Res.string.productErrorTermux
                    ProductErrorLine.Computer -> Res.string.productErrorComputer
                    ProductErrorLine.ProjectGit -> Res.string.productErrorProjectGit
                    ProductErrorLine.Unknown -> Res.string.productErrorUnknown
                }
            )
        }
    return remember(words) { { failure -> words.getValue(productErrorText(failure).line) } }
}
