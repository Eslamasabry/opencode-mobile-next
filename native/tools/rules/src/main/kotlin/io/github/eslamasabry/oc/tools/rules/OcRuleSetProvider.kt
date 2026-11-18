package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** The OC rule set: the source-pattern guard tests of `:quality` and of some modules, as rules. See RULES below. */
class OcRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "oc-rules"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                // Layer, kit and platform rules (KonsistRulesTest K1-K19, CacheFirst neighbours)
                OcKitOnlyFeatures(config),
                OcPresentationPlainKotlin(config),
                OcNoRunCatchingInSuspend(config),
                OcNoDispatchersOrClock(config),
                OcLogOnlyThroughAppLog(config),
                OcNoJvmInCommon(config),
                OcRedactorOptInPlace(config),
                OcNoGlobalScopeOrRunBlocking(config),
                OcMaterial3OnlyInKits(config),
                OcNoFeatureToFeature(config),
                // Field-fault rules (their own guard tests)
                OcNoLabelledReturnInContent(config),
                OcRawKeyboardInset(config),
                OcFailureLabel(config),
                OcLayerRecord(config),
                OcRawDelayLoop(config),
                OcTextGlyph(config),
                OcKitIconSize(config),
                // K-SEC (KSecRulesTest)
                OcSecNoAutoLink(config),
                OcSecTlsTrustOnlyInTrustGate(config),
                OcSecNoPinnerOrNetLogging(config),
                OcSecWebViewOnlyInKitWebPreview(config),
                OcSecNoWeakRandomForSecrets(config),
            ),
        )
}
