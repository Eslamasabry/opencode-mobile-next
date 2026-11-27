package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Which sentence the state line under the version says. The UI turns it into words with [UpdatesUi]'s arguments. */
public enum class UpdatesLine {
    /** "Not checked yet" */
    NeverChecked,

    /** "Checking is off. Use Check now when you want to look." */
    CheckingOff,

    /** "Checking…" */
    Checking,

    /** "Up to date · checked just now" */
    CheckedJustNow,

    /** "Up to date · checked N min ago" ([UpdatesUi.agoCount]) */
    CheckedMinutesAgo,

    /** "Up to date · checked Nh ago" */
    CheckedHoursAgo,

    /** "Up to date · checked Nd ago" */
    CheckedDaysAgo,

    /** "2.2.0 is ready · 39 MB" ([UpdatesUi.releaseVersion], [UpdatesUi.sizeMb]) */
    Available,

    /** "Version 2.2.0 is ready" (size not known yet) */
    AvailableNoSize,

    /** "2.2.0 couldn't be installed" */
    NotInstalled,

    /** "Downloading version 2.2.0" */
    Downloading,

    /** "Checking the download…" */
    Verifying,

    /** "Installing…" */
    Installing,

    /** "Version 2.2.0 is downloaded and checked" */
    ReadyToInstall,

    /** "Test build" (B-218) */
    TestBuild,
}

/** The one main action of the version row (Flutter `_UpdatesBody` `action`). */
public enum class UpdatesAction { Update, TryAgain, OpenRelease, Cancel, Install }

/** Parsed certificate fingerprints for a signer-mismatch failure, shown only under Details (B-218). */
public data class SignerDetails(val own: String, val update: String)

/**
 * Everything the Updates page shows, as data (no words). Port of the Flutter `_UpdatesBody` decisions.
 *
 * [version] null means the installed version is not known yet (the title then names the app).
 */
public data class UpdatesUi(
    val supported: Boolean = true,
    /** B-218: a test build that cannot install public releases over itself. */
    val isTestBuild: Boolean = false,
    val version: String? = null,
    /** `2.1.0+57`, shown only under Details. */
    val build: String? = null,
    val line: UpdatesLine = UpdatesLine.NeverChecked,
    /** The newer release's version, the argument of the release lines. */
    val releaseVersion: String = "",
    /** Download size in whole MB, rounded up. */
    val sizeMb: Long? = null,
    /** The count for the "checked … ago" lines. */
    val agoCount: Int = 0,
    val action: UpdatesAction? = null,
    /** The progress view shows (downloading). */
    val showProgress: Boolean = false,
    /** 0..1, or null while the size is unknown (the bar waits). */
    val progress: Float? = null,
    /** Whole percent done, floor of [progress]. */
    val percent: Int? = null,
    /** Why the update stopped, in plain words on screen. */
    val failure: UpdateFailure? = null,
    /** Generated technical text for the Details fold; only when failed. Never shown outside Details. */
    val detail: String? = null,
    /** Labeled signer fingerprints for a signer mismatch, shown only under Details (B-218). */
    val signerDetails: SignerDetails? = null,
    /** "To install it, allow this app to install updates, then come back." */
    val needsPermission: Boolean = false,
    val checksOn: Boolean = true,
    val checkNowEnabled: Boolean = true,
    /** Release notes (Markdown source) for What's new; null when there are none. */
    val notes: String? = null,
    /** Safe `https` What's new link awaiting host confirmation (B-628). */
    val linkPrompt: NoteLinkPrompt? = null,
    /** True after an external link failed because no browser could handle it (B-628). */
    val noBrowser: Boolean = false,
)

private const val MEGABYTE = 1_000_000.0
private val SIGNER_DETAIL = Regex("""^signer\s+([^,]+),\s*expected\s+(.+)$""", RegexOption.IGNORE_CASE)

/** Parses `"signer <update>, expected <own>"` into labeled fingerprints, or null when the text has another shape. */
internal fun parseSignerDetails(raw: String?): SignerDetails? {
    val text = raw?.trim().orEmpty()
    val match = SIGNER_DETAIL.matchEntire(text) ?: return null
    val update = match.groupValues[1].trim()
    val own = match.groupValues[2].trim()
    if (own.isEmpty() || update.isEmpty()) return null
    return SignerDetails(own = own, update = update)
}

/** The page's state from the engine's [state] at [now] (Flutter `_UpdatesBody.build`). Pure. */
public fun updatesUi(state: AppUpdateState, now: Instant): UpdatesUi {
    val app = state.installed
    val buildText = app?.let { "${it.version}+${it.build}" }
    if (state.isTestBuild) {
        return UpdatesUi(
            supported = state.supported,
            isTestBuild = true,
            version = app?.version,
            build = buildText,
            line = UpdatesLine.TestBuild,
            checksOn = state.enabled,
            checkNowEnabled = false,
        )
    }
    val release = state.release
    val failed = state.phase == UpdatePhase.Failed
    val (line, ago) = lineFor(state, now)
    val action = if (state.supported) actionFor(state) else null
    val downloading = state.phase == UpdatePhase.Downloading
    val progress = state.progress?.coerceIn(0f, 1f)
    val failure = if (failed) state.failure ?: UpdateFailure.Network else null
    val signerDetails = if (failure == UpdateFailure.Signer) parseSignerDetails(state.detail) else null
    val rawDetail = if (failed && signerDetails == null) state.detail?.takeIf { it.isNotBlank() } else null
    return UpdatesUi(
        supported = state.supported,
        version = app?.version,
        build = buildText,
        line = line,
        releaseVersion = release?.version.orEmpty(),
        sizeMb = state.downloadSize?.let { ceil(it / MEGABYTE).toLong() },
        agoCount = ago,
        action = action,
        showProgress = downloading,
        progress = if (downloading) progress else null,
        percent = if (downloading) progress?.let { floor(it * 100f).toInt() } else null,
        failure = failure,
        detail = rawDetail,
        signerDetails = signerDetails,
        needsPermission = state.phase == UpdatePhase.ReadyToInstall && state.needsPermission,
        checksOn = state.enabled,
        checkNowEnabled = state.supported && !state.busy,
        notes = release?.notes?.takeIf { it.isNotBlank() },
    )
}

private fun lineFor(state: AppUpdateState, now: Instant): Pair<UpdatesLine, Int> =
    when (state.phase) {
        UpdatePhase.Checking -> UpdatesLine.Checking to 0
        UpdatePhase.Available, UpdatePhase.Failed -> {
            val release = state.release
            val line =
                when {
                    release == null -> UpdatesLine.NeverChecked
                    state.phase == UpdatePhase.Failed -> UpdatesLine.NotInstalled
                    state.downloadSize == null -> UpdatesLine.AvailableNoSize
                    else -> UpdatesLine.Available
                }
            line to 0
        }
        UpdatePhase.Downloading -> UpdatesLine.Downloading to 0
        UpdatePhase.Verifying -> UpdatesLine.Verifying to 0
        UpdatePhase.Installing -> UpdatesLine.Installing to 0
        UpdatePhase.ReadyToInstall -> UpdatesLine.ReadyToInstall to 0
        UpdatePhase.None -> checkedLineFor(state, now)
    }

private fun checkedLineFor(state: AppUpdateState, now: Instant): Pair<UpdatesLine, Int> {
    val at = state.checkedAt ?: return (if (state.enabled) UpdatesLine.NeverChecked else UpdatesLine.CheckingOff) to 0
    val since = now - at
    return when {
        since < 1.minutes -> UpdatesLine.CheckedJustNow to 0
        since < 1.hours -> UpdatesLine.CheckedMinutesAgo to since.inWholeMinutes.toInt()
        since < 1.days -> UpdatesLine.CheckedHoursAgo to since.inWholeHours.toInt()
        else -> UpdatesLine.CheckedDaysAgo to since.inWholeDays.toInt()
    }
}

private fun actionFor(state: AppUpdateState): UpdatesAction? =
    when (state.phase) {
        UpdatePhase.Available -> if (state.release != null) UpdatesAction.Update else null
        UpdatePhase.Failed -> {
            val release = state.release
            when {
                release == null -> null
                state.retryHelps -> UpdatesAction.TryAgain
                release.pageUrl.isNotEmpty() -> UpdatesAction.OpenRelease
                else -> null
            }
        }
        UpdatePhase.Downloading -> UpdatesAction.Cancel
        UpdatePhase.ReadyToInstall -> UpdatesAction.Install
        UpdatePhase.None, UpdatePhase.Checking, UpdatePhase.Verifying, UpdatePhase.Installing -> null
    }
