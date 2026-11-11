package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chats.presentation.ConnectionDiagnosis
import io.github.eslamasabry.oc.feature.chats.presentation.DiagnosisCause
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The card's headline and its explanation followed by the "What to check" list. */
internal class DiagnosisWords(val title: String, val body: String)

/** Flutter `ConnectionFailure.diagnose` copy, in the same order: most likely check first, the retry note last. */
@Composable
internal fun diagnosisWords(diagnosis: ConnectionDiagnosis): DiagnosisWords {
    val tailnetCheck =
        if (diagnosis.tailnet) stringResource(Res.string.connectionFailureTailnetCheck) else null
    val retried =
        if (diagnosis.attempts >= ConnectionDiagnosis.ESCALATE_AFTER)
            stringResource(Res.string.e7ConnectionFailure1, diagnosis.attempts)
        else null
    val title: String
    val explanation: String
    val checks: List<String?>
    when (diagnosis.cause) {
        DiagnosisCause.OpenCode1 -> {
            title = stringResource(Res.string.openCode1UnsupportedTitle)
            explanation = stringResource(Res.string.openCode1Unsupported)
            checks = emptyList()
        }
        DiagnosisCause.TokenRequired -> {
            title = stringResource(Res.string.e7ConnectionFailure2)
            explanation = stringResource(Res.string.e7ConnectionFailure3)
            checks = listOf(stringResource(Res.string.e7ConnectionFailure4), retried)
        }
        DiagnosisCause.TokenRejected -> {
            title = stringResource(Res.string.e7ConnectionFailure5)
            explanation = stringResource(Res.string.e7ConnectionFailure6)
            checks = listOf(stringResource(Res.string.e7ConnectionFailure7), retried)
        }
        DiagnosisCause.CodexLocal -> {
            title = stringResource(Res.string.e7ConnectionFailure8)
            explanation = stringResource(Res.string.connectionFailureLocalCodexBody)
            checks =
                listOf(
                    stringResource(Res.string.e7ConnectionFailure12),
                    stringResource(Res.string.e7ConnectionFailure13),
                    retried,
                )
        }
        DiagnosisCause.CodexRemote -> {
            title = stringResource(Res.string.e7ConnectionFailure9)
            explanation = stringResource(Res.string.connectionFailureRemoteCodexBody)
            checks =
                listOf(
                    stringResource(Res.string.e7ConnectionFailure14),
                    stringResource(Res.string.e7ConnectionFailure15),
                    retried,
                )
        }
        DiagnosisCause.PasswordRejected -> {
            title = stringResource(Res.string.e7ConnectionFailure16)
            explanation = stringResource(Res.string.e7ConnectionFailure17)
            checks =
                listOf(
                    stringResource(Res.string.e7ConnectionFailure18),
                    stringResource(Res.string.e7ConnectionFailure19),
                    retried,
                )
        }
        DiagnosisCause.CertificateChanged -> {
            title = stringResource(Res.string.serverTrustCertificateChangedTitle)
            explanation = stringResource(Res.string.serverTrustCertificateChangedBody)
            checks = listOf(stringResource(Res.string.serverTrustCertificateRecheck))
        }
        DiagnosisCause.CertificateUntrusted -> {
            title = stringResource(Res.string.e7ConnectionFailure20)
            explanation = stringResource(Res.string.e7ConnectionFailure21)
            checks =
                listOf(
                    stringResource(Res.string.e7ConnectionFailure22),
                    stringResource(Res.string.e7ConnectionFailure23),
                    retried,
                )
        }
        DiagnosisCause.Loopback -> {
            title = stringResource(Res.string.e7ConnectionFailure24)
            explanation = stringResource(Res.string.connectionFailureLoopbackBody)
            checks =
                listOf(
                    stringResource(Res.string.e7ConnectionFailure27),
                    stringResource(Res.string.e7ConnectionFailure28),
                    retried,
                )
        }
        DiagnosisCause.TimedOut -> {
            title = stringResource(Res.string.e7ConnectionFailure29)
            explanation = stringResource(Res.string.connectionFailureTimedOutBody)
            checks =
                listOf(
                    tailnetCheck,
                    stringResource(Res.string.e7ConnectionFailure31),
                    stringResource(Res.string.e7ConnectionFailure32, diagnosis.port),
                    retried,
                )
        }
        DiagnosisCause.NothingAnswered -> {
            title = stringResource(Res.string.e7ConnectionFailure33)
            explanation = stringResource(Res.string.connectionFailureNothingAnsweredBody)
            checks =
                listOf(
                    tailnetCheck,
                    stringResource(Res.string.e7ConnectionFailure35),
                    stringResource(Res.string.e7ConnectionFailure36),
                    stringResource(Res.string.e7ConnectionFailure37),
                    retried,
                )
        }
        DiagnosisCause.ServerError -> {
            title = stringResource(Res.string.e7ConnectionFailure38)
            explanation = stringResource(Res.string.e7ConnectionFailure39)
            checks =
                listOf(
                    stringResource(Res.string.e7ConnectionFailure40),
                    stringResource(Res.string.e7ConnectionFailure41),
                    retried,
                )
        }
        DiagnosisCause.Unknown -> {
            title = stringResource(Res.string.e7ConnectionFailure42)
            explanation = stringResource(Res.string.connectionFailureUnknownBody)
            checks =
                listOf(
                    stringResource(
                        if (diagnosis.usesToken) Res.string.e7ConnectionFailure44
                        else Res.string.e7ConnectionFailure45
                    ),
                    retried,
                )
        }
    }
    val list = checks.filterNotNull()
    val body =
        if (list.isEmpty()) explanation
        else
            explanation +
                "\n\n" +
                stringResource(Res.string.e7SetupWhatToCheck) +
                list.joinToString(separator = "") { "\n• $it" }
    return DiagnosisWords(title, body)
}
