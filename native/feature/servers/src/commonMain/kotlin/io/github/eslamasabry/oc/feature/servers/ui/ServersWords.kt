package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.servers.presentation.AddressProblem
import io.github.eslamasabry.oc.feature.servers.presentation.AddressVerdict
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionDiagnosis
import io.github.eslamasabry.oc.feature.servers.presentation.FailureAction
import io.github.eslamasabry.oc.feature.servers.presentation.FailureCheck
import io.github.eslamasabry.oc.feature.servers.presentation.FailureKind
import io.github.eslamasabry.oc.feature.servers.presentation.PairingRejection
import io.github.eslamasabry.oc.feature.servers.presentation.ServerStatus
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerChecking
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.connectionFailureLocalCodexBody
import io.github.eslamasabry.oc.ui.words.res.connectionFailureLoopbackBody
import io.github.eslamasabry.oc.ui.words.res.connectionFailureNothingAnsweredBody
import io.github.eslamasabry.oc.ui.words.res.connectionFailureRemoteCodexBody
import io.github.eslamasabry.oc.ui.words.res.connectionFailureTailnetCheck
import io.github.eslamasabry.oc.ui.words.res.connectionFailureTimedOutBody
import io.github.eslamasabry.oc.ui.words.res.connectionFailureUnknownBody
import io.github.eslamasabry.oc.ui.words.res.connectorCardConnecting
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure1
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure12
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure13
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure14
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure15
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure16
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure17
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure18
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure19
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure2
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure20
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure21
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure22
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure23
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure24
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure26
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure27
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure28
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure29
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure3
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure31
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure32
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure33
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure35
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure36
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure37
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure38
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure39
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure4
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure40
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure41
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure42
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure44
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure45
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure5
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure6
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure7
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure8
import io.github.eslamasabry.oc.ui.words.res.e7ConnectionFailure9
import io.github.eslamasabry.oc.ui.words.res.e7SetupCompleteUrl
import io.github.eslamasabry.oc.ui.words.res.e7SetupDns
import io.github.eslamasabry.oc.ui.words.res.e7SetupEditServer
import io.github.eslamasabry.oc.ui.words.res.e7SetupEnterUrl
import io.github.eslamasabry.oc.ui.words.res.e7SetupNotOpenCode
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairAddressLong
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairAddressMissing
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairAddressType
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairAddressUnusable
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairInvalid
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairLong
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairNoUrls
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairNone
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairPasswordMissing
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairPasswordRejected
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairPasswordType
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairShape
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairTestFailed
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairTooMany
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairUrlsType
import io.github.eslamasabry.oc.ui.words.res.e7SetupPairUsernameType
import io.github.eslamasabry.oc.ui.words.res.e7SetupServerStarting
import io.github.eslamasabry.oc.ui.words.res.e7SetupTimeout
import io.github.eslamasabry.oc.ui.words.res.e7SetupUnhealthy
import io.github.eslamasabry.oc.ui.words.res.e7SetupUpdatePassword
import io.github.eslamasabry.oc.ui.words.res.e7SetupUrlCredentials
import io.github.eslamasabry.oc.ui.words.res.e7SharedHTTPIsNotEncryptedOtherDevicesOn
import io.github.eslamasabry.oc.ui.words.res.inAppServerNotRespondingBody
import io.github.eslamasabry.oc.ui.words.res.inAppServerNotRespondingTitle
import io.github.eslamasabry.oc.ui.words.res.inAppServerStartFailedBody
import io.github.eslamasabry.oc.ui.words.res.inAppServerStartFailedTitle
import io.github.eslamasabry.oc.ui.words.res.inAppServerStoppedBody
import io.github.eslamasabry.oc.ui.words.res.inAppServerStoppedTitle
import io.github.eslamasabry.oc.ui.words.res.openCode1Unsupported
import io.github.eslamasabry.oc.ui.words.res.openCode1UnsupportedTitle
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStartOpenCode
import io.github.eslamasabry.oc.ui.words.res.phoneServerRowNotAnswering
import io.github.eslamasabry.oc.ui.words.res.phoneServerStoppedBody
import io.github.eslamasabry.oc.ui.words.res.phoneServerStoppedTitle
import io.github.eslamasabry.oc.ui.words.res.serverRowConnected
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateChangedBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateChangedTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateRecheck
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateValidityError
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpUnconfirmed
import io.github.eslamasabry.oc.ui.words.res.settingsV3NotConnected
import io.github.eslamasabry.oc.ui.words.res.updateConnectionToken
import org.jetbrains.compose.resources.stringResource

/*
 * Enum → words. Every string is an existing `:ui:words` key (ported Flutter copy); none is authored here. Keys that
 * read a little off for their new use are listed as word requests in docs/native/units/S2-SERVERS.md.
 */

@Composable
internal fun FailureKind.title(): String =
    when (this) {
        FailureKind.OpenCode1 -> stringResource(Res.string.openCode1UnsupportedTitle)
        FailureKind.InAppStopped -> stringResource(Res.string.inAppServerStoppedTitle)
        FailureKind.InAppNotResponding -> stringResource(Res.string.inAppServerNotRespondingTitle)
        FailureKind.InAppStartFailed -> stringResource(Res.string.inAppServerStartFailedTitle)
        FailureKind.TokenRequired -> stringResource(Res.string.e7ConnectionFailure2)
        FailureKind.TokenRejected -> stringResource(Res.string.e7ConnectionFailure5)
        FailureKind.CodexListenerUnavailable -> stringResource(Res.string.e7ConnectionFailure8)
        FailureKind.CodexEndpointUnreachable -> stringResource(Res.string.e7ConnectionFailure9)
        FailureKind.PasswordRejected -> stringResource(Res.string.e7ConnectionFailure16)
        FailureKind.CertificateChanged -> stringResource(Res.string.serverTrustCertificateChangedTitle)
        FailureKind.CertificateNotTrusted, FailureKind.CertificateExpired -> stringResource(Res.string.e7ConnectionFailure20)
        FailureKind.HttpUnconfirmed -> stringResource(Res.string.serverTrustHttpTitle)
        FailureKind.PhoneServerStopped -> stringResource(Res.string.phoneServerStoppedTitle)
        FailureKind.NothingAnsweredOnPhone -> stringResource(Res.string.e7ConnectionFailure24)
        FailureKind.TimedOut -> stringResource(Res.string.e7ConnectionFailure29)
        FailureKind.NotReachable -> stringResource(Res.string.e7ConnectionFailure33)
        FailureKind.ServerError -> stringResource(Res.string.e7ConnectionFailure38)
        FailureKind.Unknown -> stringResource(Res.string.e7ConnectionFailure42)
    }

@Composable
internal fun FailureKind.body(): String =
    when (this) {
        FailureKind.OpenCode1 -> stringResource(Res.string.openCode1Unsupported)
        FailureKind.InAppStopped -> stringResource(Res.string.inAppServerStoppedBody)
        FailureKind.InAppNotResponding -> stringResource(Res.string.inAppServerNotRespondingBody)
        FailureKind.InAppStartFailed -> stringResource(Res.string.inAppServerStartFailedBody)
        FailureKind.TokenRequired -> stringResource(Res.string.e7ConnectionFailure3)
        FailureKind.TokenRejected -> stringResource(Res.string.e7ConnectionFailure6)
        FailureKind.CodexListenerUnavailable -> stringResource(Res.string.connectionFailureLocalCodexBody)
        FailureKind.CodexEndpointUnreachable -> stringResource(Res.string.connectionFailureRemoteCodexBody)
        FailureKind.PasswordRejected -> stringResource(Res.string.e7ConnectionFailure17)
        FailureKind.CertificateChanged -> stringResource(Res.string.serverTrustCertificateChangedBody)
        FailureKind.CertificateNotTrusted -> stringResource(Res.string.e7ConnectionFailure21)
        FailureKind.CertificateExpired -> stringResource(Res.string.serverTrustCertificateValidityError)
        FailureKind.HttpUnconfirmed -> stringResource(Res.string.serverTrustHttpUnconfirmed)
        FailureKind.PhoneServerStopped -> stringResource(Res.string.phoneServerStoppedBody)
        FailureKind.NothingAnsweredOnPhone -> stringResource(Res.string.connectionFailureLoopbackBody)
        FailureKind.TimedOut -> stringResource(Res.string.connectionFailureTimedOutBody)
        FailureKind.NotReachable -> stringResource(Res.string.connectionFailureNothingAnsweredBody)
        FailureKind.ServerError -> stringResource(Res.string.e7ConnectionFailure39)
        FailureKind.Unknown -> stringResource(Res.string.connectionFailureUnknownBody)
    }

@Composable
internal fun FailureCheck.words(): String =
    when (this) {
        FailureCheck.UpgradeCommand -> ConnectionDiagnosis.UPGRADE_COMMAND
        FailureCheck.EnterCodexToken -> stringResource(Res.string.e7ConnectionFailure4)
        FailureCheck.EnterCurrentCodexToken -> stringResource(Res.string.e7ConnectionFailure7)
        FailureCheck.StartCodexListener -> stringResource(Res.string.e7ConnectionFailure12)
        FailureCheck.CodexTunnel -> stringResource(Res.string.e7ConnectionFailure13)
        FailureCheck.CodexWss -> stringResource(Res.string.e7ConnectionFailure14)
        FailureCheck.CodexReachable -> stringResource(Res.string.e7ConnectionFailure15)
        FailureCheck.PairAgainForPassword -> stringResource(Res.string.e7ConnectionFailure18)
        FailureCheck.PasswordSetByHand -> stringResource(Res.string.e7ConnectionFailure19)
        FailureCheck.RecheckCertificate -> stringResource(Res.string.serverTrustCertificateRecheck)
        FailureCheck.TrustedCertificate -> stringResource(Res.string.e7ConnectionFailure22)
        FailureCheck.SelfSignedFingerprint -> stringResource(Res.string.e7ConnectionFailure23)
        FailureCheck.TermuxRunning -> stringResource(Res.string.e7ConnectionFailure26)
        FailureCheck.TunnelConnected -> stringResource(Res.string.e7ConnectionFailure27)
        FailureCheck.OtherComputer -> stringResource(Res.string.e7ConnectionFailure28)
        FailureCheck.TailscaleOn -> stringResource(Res.string.connectionFailureTailnetCheck)
        FailureCheck.SameNetworkOrVpnTimeout -> stringResource(Res.string.e7ConnectionFailure31)
        is FailureCheck.Firewall -> stringResource(Res.string.e7ConnectionFailure32, port)
        FailureCheck.ServeRunning -> stringResource(Res.string.e7ConnectionFailure35)
        FailureCheck.SameNetworkOrVpn -> stringResource(Res.string.e7ConnectionFailure36)
        FailureCheck.AddressChanged -> stringResource(Res.string.e7ConnectionFailure37)
        FailureCheck.RestartServe -> stringResource(Res.string.e7ConnectionFailure40)
        FailureCheck.VersionSupported -> stringResource(Res.string.e7ConnectionFailure41)
        FailureCheck.AgentServerRunning -> stringResource(Res.string.e7ConnectionFailure44)
        FailureCheck.ServeRunningRightAddress -> stringResource(Res.string.e7ConnectionFailure45)
        is FailureCheck.TriedTimes -> stringResource(Res.string.e7ConnectionFailure1, attempts)
    }

@Composable
internal fun FailureAction.label(): String =
    when (this) {
        FailureAction.Retry -> stringResource(Res.string.commonRetry)
        FailureAction.StartPhoneServer -> stringResource(Res.string.phoneServerCardStartOpenCode)
        FailureAction.UpdatePassword -> stringResource(Res.string.e7SetupUpdatePassword)
        FailureAction.UpdateToken -> stringResource(Res.string.updateConnectionToken)
        FailureAction.ReviewCertificate -> stringResource(Res.string.serverTrustCertificateRecheck)
        FailureAction.ChangeServer, FailureAction.EditServer -> stringResource(Res.string.e7SetupEditServer)
    }

@Composable
internal fun ServerStatus.words(): String =
    when (this) {
        ServerStatus.Connected -> stringResource(Res.string.serverRowConnected)
        ServerStatus.Connecting -> stringResource(Res.string.connectorCardConnecting)
        ServerStatus.NotConnected -> stringResource(Res.string.settingsV3NotConnected)
        ServerStatus.Failed -> stringResource(Res.string.phoneServerRowNotAnswering)
    }

@Composable
internal fun PairingRejection.words(): String =
    when (this) {
        PairingRejection.Empty -> stringResource(Res.string.e7SetupPairNone)
        PairingRejection.TooLong -> stringResource(Res.string.e7SetupPairLong)
        PairingRejection.NotJson -> stringResource(Res.string.e7SetupPairInvalid)
        PairingRejection.NotAnObject -> stringResource(Res.string.e7SetupPairShape)
        PairingRejection.NoUrls -> stringResource(Res.string.e7SetupPairNoUrls)
        PairingRejection.UrlsNotAList -> stringResource(Res.string.e7SetupPairUrlsType)
        PairingRejection.TooManyUrls -> stringResource(Res.string.e7SetupPairTooMany)
        PairingRejection.UrlNotText -> stringResource(Res.string.e7SetupPairAddressType)
        PairingRejection.UrlTooLong -> stringResource(Res.string.e7SetupPairAddressLong)
        PairingRejection.NoAddress -> stringResource(Res.string.e7SetupPairAddressMissing)
        PairingRejection.UsernameNotText -> stringResource(Res.string.e7SetupPairUsernameType)
        PairingRejection.NoPassword -> stringResource(Res.string.e7SetupPairPasswordMissing)
        PairingRejection.PasswordNotText -> stringResource(Res.string.e7SetupPairPasswordType)
    }

/** One plain reason per pairing address; the set is fixed, so no probe text can reach here (B-SEC-108). */
@Composable
internal fun AddressVerdict.words(): String =
    when (this) {
        AddressVerdict.Answered -> stringResource(Res.string.serverRowConnected)
        AddressVerdict.Refused -> stringResource(Res.string.connectionFailureNothingAnsweredBody)
        AddressVerdict.TimedOut -> stringResource(Res.string.e7SetupTimeout)
        AddressVerdict.HostNotFound -> stringResource(Res.string.e7SetupDns)
        AddressVerdict.CertificateRejected -> stringResource(Res.string.e7ConnectionFailure20)
        AddressVerdict.PasswordRejected -> stringResource(Res.string.e7SetupPairPasswordRejected)
        AddressVerdict.Unhealthy -> stringResource(Res.string.e7SetupUnhealthy)
        AddressVerdict.Starting -> stringResource(Res.string.e7SetupServerStarting)
        AddressVerdict.NotOpenCode -> stringResource(Res.string.e7SetupNotOpenCode)
        AddressVerdict.ProbeFailed -> stringResource(Res.string.e7SetupPairTestFailed)
        AddressVerdict.InvalidAddress -> stringResource(Res.string.e7SetupPairAddressUnusable)
        AddressVerdict.CredentialsInAddress -> stringResource(Res.string.e7SetupUrlCredentials)
        AddressVerdict.NeedsCleartextConsent -> stringResource(Res.string.e7SharedHTTPIsNotEncryptedOtherDevicesOn)
    }

@Composable
internal fun AddressProblem.words(): String =
    when (this) {
        AddressProblem.Empty -> stringResource(Res.string.e7SetupEnterUrl)
        AddressProblem.Invalid -> stringResource(Res.string.e7SetupCompleteUrl)
        AddressProblem.CredentialsInAddress -> stringResource(Res.string.e7SetupUrlCredentials)
        AddressProblem.PinNeedsHttps -> stringResource(Res.string.e7SharedHTTPIsNotEncryptedOtherDevicesOn)
    }

@Composable
internal fun checkingWords(): String = stringResource(Res.string.addServerChecking)
