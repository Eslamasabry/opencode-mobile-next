package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.feature.servers.presentation.AddressPolicy

/**
 * The production binding of [AddressPolicy], kept in tests until the app graph binds it (report: catalog request R2).
 * It only forwards, so every row here runs against the real `:core:security` rules.
 */
object RulesAddressPolicy : AddressPolicy {
    override fun normalize(typed: String): Outcome<ServerOrigin> = AddressRules.normalize(typed)

    override fun origin(url: String): Outcome<ServerOrigin> = AddressRules.origin(url)

    override fun needsCleartextConsent(origin: ServerOrigin): Boolean = AddressRules.needsCleartextConsent(origin)

    override fun isLoopback(host: String): Boolean = AddressRules.isLoopback(host)

    override fun isPrivateNetwork(host: String): Boolean = AddressRules.isPrivateNetwork(host)

    override fun isTailnet(host: String): Boolean = AddressRules.isTailnet(host)

    override fun checkPin(origin: ServerOrigin, fingerprint: String): Outcome<String> =
        AddressRules.checkPin(origin, fingerprint)
}
