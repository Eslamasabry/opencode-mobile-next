package io.github.eslamasabry.oc.engine.agents.guard

import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.scripts.freezeTipSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Port of "the inventory file is the live catalog" (test/coverage/phone_agent_catalog_coverage_test.dart): the native
 * catalog equals the Flutter inventory dump field for field. `limitation` and `resumeReason` are English copy in Dart
 * and words keys here, so they are left out; everything else is compared as the Dart dump writes it.
 */
class CatalogInventoryTest {
    private fun wire(name: String) = name.replaceFirstChar { it.lowercaseChar() }

    private fun artifact(a: AgentArtifact): JsonObject = JsonObject(
        buildMap {
            put("url", JsonPrimitive(a.url))
            put("sha256", JsonPrimitive(a.sha256))
            put("format", JsonPrimitive(wire(a.format.name)))
            a.archiveMember?.let { put("archiveMember", JsonPrimitive(it)) }
            a.downloadBytes?.let { put("downloadBytes", JsonPrimitive(it)) }
            a.installedBytes?.let { put("installedBytes", JsonPrimitive(it)) }
        },
    )

    private fun entry(d: AgentDescriptor): JsonObject = JsonObject(
        buildMap {
            put("id", JsonPrimitive(d.id.raw))
            put("name", JsonPrimitive(d.name))
            put("iconKey", JsonPrimitive(d.iconKey))
            put("route", JsonPrimitive(wire(d.route.name)))
            put("providerId", JsonPrimitive(d.providerId))
            put("signInMethod", JsonPrimitive(wire(d.signInMethod.name)))
            put("availability", JsonPrimitive(wire(d.availability.name)))
            d.unavailableReason?.let { put("unavailableReason", JsonPrimitive(wire(it.name))) }
            put(
                "capabilities",
                JsonObject(
                    mapOf(
                        "resumeVerified" to JsonPrimitive(d.capabilities.resumeVerified),
                        "modelList" to JsonPrimitive(d.capabilities.modelList),
                        "permissions" to JsonPrimitive(d.capabilities.permissions),
                        "images" to JsonPrimitive(d.capabilities.images),
                        "cancel" to JsonPrimitive(d.capabilities.cancel),
                    ),
                ),
            )
            d.recipe?.let { r ->
                put(
                    "recipe",
                    JsonObject(
                        mapOf(
                            "version" to JsonPrimitive(r.version),
                            "executable" to JsonPrimitive(r.executable),
                            "launchArgs" to JsonArray(r.launchArgs.map { JsonPrimitive(it) }),
                            "signInArgs" to JsonArray(r.signInArgs.map { JsonPrimitive(it) }),
                            "artifacts" to JsonObject(r.artifacts.entries.associate { (k, v) -> k.wire to artifact(v) }),
                        ),
                    ),
                )
            }
        },
    )

    /**
     * Pins that moved on native after the Flutter freeze, old text to new, applied to that agent's saved entry only:
     * OpenCode 2 2.2.5 -> 2.3.2 (93fcf4049; the values AgentPinsParityTest checks literally); omp's set-up command; the 2026-11-14 agent upgrades
     * below. Every other field still has to equal the inventory byte for byte.
     */
    private val postFreezeMoves = mapOf(
        "opencode2" to listOf(
            "2.2.5" to "2.3.2",
            "f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053" to
                "91a937129efdae8eb8c0c5018ae533f9f35117f26ffd9a4d1f7a5f9da1e69306",
            "873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08" to
                "e567dcdb1310dcae4e442077e0d508e10a80c1b996131a04dc9b978cdd26e5cd",
        ),
        // 2026-11-13: omp is set up in the app's terminal with `omp login` (its bare TUI was the old sign-in command).
        "omp-acp" to listOf(
            "\"signInArgs\":[]" to "\"signInArgs\":[\"login\"]",
            text("18.5.1", "18.20.0"),
            text("https://github.com/can1357/oh-my-pi/releases/download/v18.5.1/omp-linux-arm64", "https://github.com/can1357/oh-my-pi/releases/download/v18.20.0/omp-linux-arm64"),
            text("e7b81b96b3f9f391ec8afc7ee64c052ce1783290f88bbb59ba36d15b3b4e927c", "8b5e197db7c58b7ebabf83c6f03597f89bae3a766a84c6481b3a942a125f5e22"),
            number("downloadBytes", 230476072, 244435960),
            number("installedBytes", 230476072, 244435960),
            text("https://github.com/can1357/oh-my-pi/releases/download/v18.5.1/omp-linux-x64", "https://github.com/can1357/oh-my-pi/releases/download/v18.20.0/omp-linux-x64"),
            text("fb7638e82f0c9d087e96e15d3eb83a662d92374cd907187d6d4d2605e39f1e3a", "c8694be8c6e6a1f45e3636dd8a8e9eaa78878ad629a89000b403ec5e456d3f89"),
            number("downloadBytes", 280536544, 287484080),
            number("installedBytes", 280536544, 287484080),
        ),
        // 2026-11-14 agent upgrades (owner: upgrade all agents to latest), each from its own commit: Claude Code
        // 2.1.327, Codex 0.170.0, Antigravity 1.5.11, Qwen Code 0.27.3, Kilo 7.8.27, Goose 1.59.1, Oh My Pi 18.20.0 (above)
        // and fx 0.0.16. Version, URLs, hashes and sizes are the pins file's values; AgentPinsParityTest checks the
        // same agents' pins literally. Gemini CLI and Cline did not move.
        "claude" to listOf(
            text("2.1.319", "2.1.327"),
            text("https://downloads.claude.ai/claude-code-releases/2.1.319/linux-arm64/claude", "https://downloads.claude.ai/claude-code-releases/2.1.327/linux-arm64/claude"),
            text("a7e687fd1cb054d651c7588223de3786c99d4c682365e3c3ae1e59ce7f3a591f", "acf0ef2f9da8d5ab08f193f007e19a06cbfb608b96a2f1f758d63a92a386cff8"),
            number("downloadBytes", 339797432, 371779000),
            number("installedBytes", 339797432, 371779000),
            text("https://downloads.claude.ai/claude-code-releases/2.1.319/linux-x64/claude", "https://downloads.claude.ai/claude-code-releases/2.1.327/linux-x64/claude"),
            text("dabafec82ca1319fa43d8be36a10fc61b1d11821cb98ad5df04ad95e199b8eed", "343c744baa14bfd38286a7cb90871051f04379956da6c5b803a9e66019a76ac9"),
            number("downloadBytes", 340226576, 372904464),
            number("installedBytes", 340226576, 372904464),
        ),
        "codex" to listOf(
            text("0.167.0", "0.171.0"),
            text("https://github.com/openai/codex/releases/download/rust-v0.167.0/codex-aarch64-unknown-linux-musl.tar.gz", "https://github.com/openai/codex/releases/download/rust-v0.171.0/codex-package-aarch64-unknown-linux-musl.tar.gz"),
            text("843489acb4d4b1079745c36b03c5963b887b83155af208fd53584e33ee8eb281", "d8c43bee6cf11b01eb18b95f4d7961092bc40228180808859e70dabb7784c1c9"),
            number("downloadBytes", 114300898, 199315347),
            number("installedBytes", 278794648, 516528332),
            // Issue 147: the official package, whose entry is bin/codex beside its code-mode host.
            text("codex-aarch64-unknown-linux-musl", "bin/codex"),
            text("https://github.com/openai/codex/releases/download/rust-v0.167.0/codex-x86_64-unknown-linux-musl.tar.gz", "https://github.com/openai/codex/releases/download/rust-v0.171.0/codex-package-x86_64-unknown-linux-musl.tar.gz"),
            text("9d64836106d5b374e90ee4454c7ebcde7ca7106558bd7e41a34b9f26ebda251a", "8391d67757496bfd14893eca6cbf0c96994bf2627566ff1f018f70600c9ef6b3"),
            number("downloadBytes", 122587028, 197763180),
            number("installedBytes", 320575976, 521571182),
            text("codex-x86_64-unknown-linux-musl", "bin/codex"),
        ),
        "antigravity" to listOf(
            text("1.5.3", "1.5.13"),
            text("https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.3-6604829099950080/linux-arm/cli_linux_arm64.tar.gz", "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.13-5999302598852608/linux-arm/cli_linux_arm64.tar.gz"),
            text("2d50060c7b1327995e5c81ec58829c91c2564ebb9fec165d3f9f1325ec188675", "36596ad8038531fd75f0e97b5ba8967a30f2fd67f8d9c61959d2e4761d7b215f"),
            number("downloadBytes", 69843447, 91596782),
            number("installedBytes", 235622776, 253711024),
            text("https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.3-6604829099950080/linux-x64/cli_linux_x64.tar.gz", "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.13-5999302598852608/linux-x64/cli_linux_x64.tar.gz"),
            text("1b4850599cce73a17f94ffb422df3bf9531531b77dbddc40c1a435e0c2c16807", "7fa9cc6df0d09562693d637c406a86c1b0f52ab3364714b1fd2d79cc6060d4b2"),
            number("downloadBytes", 73694700, 100548668),
            number("installedBytes", 246837648, 270103240),
        ),
        "qwen" to listOf(
            text("0.26.4", "0.28.0"),
            text("https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.26.4.tgz", "https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.28.0.tgz"),
            text("a7640f1c0656b7e54ea5995335ed6cc18ee219b8b4200fe8a6141cff148aaa89", "32ca2695069210bae82410a8712d8658a7da5779ecbfcd67160adce415a4e1d9"),
            number("downloadBytes", 42796897, 43527360),
            number("installedBytes", 154807157, 158997434),
        ),
        "kilo" to listOf(
            text("7.8.18", "7.8.27"),
            text("https://registry.npmjs.org/@kilocode/cli-linux-arm64/-/cli-linux-arm64-7.8.18.tgz", "https://registry.npmjs.org/@kilocode/cli-linux-arm64/-/cli-linux-arm64-7.8.27.tgz"),
            text("0f1bd0fb983f67ec1edf53c185e9521268f8f133af2a4cec2f1967e5d60e45ef", "992a0d0f4956926c6f624de742792eeab140516640f66a5cf5b7f9c357c06eb4"),
            number("downloadBytes", 83529597, 83530144),
            number("installedBytes", 258264056, 258198520),
            text("https://registry.npmjs.org/@kilocode/cli-linux-x64/-/cli-linux-x64-7.8.18.tgz", "https://registry.npmjs.org/@kilocode/cli-linux-x64/-/cli-linux-x64-7.8.27.tgz"),
            text("bde638d0a493155088e4454fbe4a9697571443665e2a22d5a78cad645b96b554", "1da75cf0996f249abc9e1ae10bdcebcefd760dd375c440e704ae70bb9be3ce0b"),
            number("downloadBytes", 84132773, 84123912),
            number("installedBytes", 259213488, 259201200),
        ),
        "goose" to listOf(
            text("1.54.0", "1.60.2"),
            text("https://github.com/aaif-goose/goose/releases/download/v1.54.0/goose-aarch64-unknown-linux-gnu.tar.gz", "https://github.com/aaif-goose/goose/releases/download/v1.60.2/goose-aarch64-unknown-linux-gnu.tar.gz"),
            text("0f174b5118a53888cd4e65b7ca9a4b72dd0b887e89142c8ce41e2409205e40d5", "7e74d796e93b89df374715dc669149cb634f74d778de0b87bb7c031b5b093731"),
            number("downloadBytes", 93850226, 64482004),
            number("installedBytes", 294298272, 199216392),
            text("https://github.com/aaif-goose/goose/releases/download/v1.54.0/goose-x86_64-unknown-linux-gnu.tar.gz", "https://github.com/aaif-goose/goose/releases/download/v1.60.2/goose-x86_64-unknown-linux-gnu.tar.gz"),
            text("ab396c95aebbd28b4d38c175234be8d4a3ea0c96e7ccb24c307e4618c9be57e1", "5f6e463621a8428617ae4870eecee7a00a253e9bbfccb6340dfaa4f0ec2aaf32"),
            number("downloadBytes", 90912367, 63566822),
            number("installedBytes", 307756800, 213003128),
        ),
        "fx" to listOf(
            text("0.0.13", "0.0.17"),
            text("https://github.com/vercel-labs/fx/releases/download/v0.0.13/fx-linux-aarch64.tar.gz", "https://github.com/vercel-labs/fx/releases/download/v0.0.17/fx-linux-aarch64.tar.gz"),
            text("78f6a8171193a1d2033f93d1b5a2940ea4a62147daf60a37ae670a9bf85f12e9", "b4633b8987ec43f1b9454e4629f3b5a4f1e086aae36b84ae4fefeacd6a53761f"),
            number("downloadBytes", 5692736, 4550277),
            number("installedBytes", 11167446, 6869488),
            text("https://github.com/vercel-labs/fx/releases/download/v0.0.13/fx-linux-x86_64.tar.gz", "https://github.com/vercel-labs/fx/releases/download/v0.0.17/fx-linux-x86_64.tar.gz"),
            text("53a3b30ce1541048f8fa4b42b7bc68161466bb2e3708db104449a58af3807430", "78c155d59fa59e42fd56d93037fd75041868c9c67865e8c7f0ca6dcdfac23e75"),
            number("downloadBytes", 5787883, 4769247),
            number("installedBytes", 13194918, 8833664),
        ),
    )

    private fun text(old: String, new: String) = "\"$old\"" to "\"$new\""

    private fun number(key: String, old: Long, new: Long) = "\"$key\":$old" to "\"$key\":$new"

    @Test
    fun `B-BUILD-006 the phone agent catalog inventory file is the live catalog`() {
        // The freeze tip's inventory: since the master merge test/ holds the released 2.1.5 app's older one.
        val saved = Json.parseToJsonElement(
            freezeTipSource("test/fixtures/coverage/phone_agent_catalog_source.json"),
        ).jsonObject.getValue("agents").jsonArray.map { e ->
            val id = e.jsonObject.getValue("id").jsonPrimitive.content
            val moved = postFreezeMoves[id].orEmpty().fold(e.toString()) { text, (old, new) ->
                check(old in text) { "$id no longer holds $old" }
                text.replace(old, new)
            }
            JsonObject(Json.parseToJsonElement(moved).jsonObject.filterKeys { it != "limitation" && it != "resumeReason" })
        }
        val live: List<JsonElement> = AgentCatalog.builtIn.agents.map { entry(it) }
        assertEquals(saved.size, live.size)
        for ((s, l) in saved.zip(live)) assertEquals(s, l, (s["id"] as JsonPrimitive).content)
    }
}
