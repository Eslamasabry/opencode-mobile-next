package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Lets a screen take a tapped link before it goes out to the browser (Dart `KitLinkScope`,
 * `lib/ui/kit/kit_link_scope.dart`).
 *
 * A conversation wraps its transcript in a [KitLinkScope] whose [KitLinkClaim] answers "is this link mine?". Local
 * dev-server links (`localhost:5173`) are the only thing claimed today: they open the in-app preview. Anything not
 * claimed goes to the caller's external opener, which must be the app's ExternalLinkPolicy → ExternalLinkOpener path
 * (SEC-1, native AGENTS rule 8), so the scope can only add a safer, narrower way to open a link, never skip the gate.
 *
 * States: none — a scope that only carries a claim.
 */
public fun interface KitLinkClaim {
    /** True when it handled [url] (opened its own view); false to let the link continue to the external opener. */
    public fun claim(url: String): Boolean
}

/** The nearest [KitLinkClaim], or null outside any [KitLinkScope] (Dart `KitLinkScope.maybeOf`). */
public val LocalKitLinkClaim: ProvidableCompositionLocal<KitLinkClaim?> = compositionLocalOf { null }

/** [content] under [claim]: the nearest scope wins, as Dart's inherited widget lookup does. */
@Composable
public fun KitLinkScope(claim: KitLinkClaim, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitLinkClaim provides claim, content = content)
}

public object KitLinkScopes {
    /**
     * What a tapped link does (Dart `KitLinkScope.open`): [claim] may take it; otherwise [openExternal] gets it. The
     * external opener is called at most once and never after a claim.
     */
    public fun open(claim: KitLinkClaim?, url: String, openExternal: (String) -> Unit) {
        if (claim != null && claim.claim(url)) return
        openExternal(url)
    }
}

/** A link-tap handler bound to the nearest scope: claimed links stay in the app, the rest go to [openExternal]. */
@Composable
public fun rememberKitLinkOpener(openExternal: (String) -> Unit): (String) -> Unit {
    val claim = LocalKitLinkClaim.current
    return { url -> KitLinkScopes.open(claim, url, openExternal) }
}
