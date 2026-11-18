package io.github.eslamasabry.oc.engine.agents.catalog

// A byte-for-byte copy of native/engine/agents/pins/agent-pins.json, which is the source of truth (hand-owned since
// 2026-11-13). Edit the JSON (only with an app release, S3-PINS) and paste it here; AgentPinsParityTest fails when
// the two differ.
internal const val AGENT_PINS_JSON: String = """{
  "schema": 1,
  "source": "lib/domain/agent_catalog.dart _builtInCatalog(), lib/builtin/agents/paseo_scripts.dart, lib/paseo/tested_versions.dart (copied verbatim; a pin moves only with an app release)",
  "paseo": {
    "version": "0.16.1",
    "packageLockSha256": "d5317553e5380c6705cf5742e4be475553ef788114671cc1ca217a24b068b6d5",
    "cliSha256": "2b761f40e5a6416e4aaa733f38a47d5a4639b42cc1f88fc060256d2c4ed8cf94",
    "payloadSha256": "8620d03c3e7e6776c3bfc4d22a8875f92209a563612fe7cda63dea7512f83c09",
    "codeTreeSha256": "766af65e563318895cdb7d4e79fed2629602fef5a98eb8215cb9d9ab3dc22a8a"
  },
  "node": {
    "version": "v24.22.2",
    "sha256": {
      "arm64": "95c264150aceb7f3437a29fd6984924f5f8d1511ea3cbd6c508b83f6c555dae2",
      "x64": "361718c444e49af2ca5a110c83ba6a8901143f23ab285ebbef0341f391f482a9"
    }
  },
  "claudeCodeTestedVersions": [
    "2.1.283",
    "2.1.294",
    "2.1.316",
    "2.1.319",
    "2.1.321",
    "2.1.325",
    "2.1.327"
  ],
  "paseoTestedVersions": [
    "0.9.2",
    "0.11.2",
    "0.12.0",
    "0.13.2",
    "0.14.0",
    "0.15.3",
    "0.15.4",
    "0.16.1"
  ],
  "agents": [
    {
      "id": "opencode2",
      "name": "OpenCode 2",
      "route": "opencode2",
      "providerId": "opencode",
      "signInMethod": "apiKeyHost",
      "recipe": {
        "version": "2.3.2",
        "executable": "opencode",
        "launchArgs": [
          "serve",
          "--hostname",
          "127.0.0.1"
        ],
        "signInArgs": [],
        "artifacts": {
          "arm64": {
            "url": "https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.3.2.tgz",
            "sha256": "91a937129efdae8eb8c0c5018ae533f9f35117f26ffd9a4d1f7a5f9da1e69306",
            "format": "tarGz",
            "member": "package/bin/opencode",
            "downloadBytes": null,
            "installedBytes": null
          },
          "x64": {
            "url": "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.3.2.tgz",
            "sha256": "e567dcdb1310dcae4e442077e0d508e10a80c1b996131a04dc9b978cdd26e5cd",
            "format": "tarGz",
            "member": "package/bin/opencode",
            "downloadBytes": null,
            "installedBytes": null
          }
        }
      }
    },
    {
      "id": "claude",
      "name": "Claude Code",
      "route": "paseoNative",
      "providerId": "claude",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "2.1.327",
        "executable": "claude",
        "launchArgs": [],
        "signInArgs": [
          "auth",
          "login",
          "--claudeai"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://downloads.claude.ai/claude-code-releases/2.1.327/linux-arm64/claude",
            "sha256": "acf0ef2f9da8d5ab08f193f007e19a06cbfb608b96a2f1f758d63a92a386cff8",
            "format": "executable",
            "member": null,
            "downloadBytes": 371779000,
            "installedBytes": 371779000
          },
          "x64": {
            "url": "https://downloads.claude.ai/claude-code-releases/2.1.327/linux-x64/claude",
            "sha256": "343c744baa14bfd38286a7cb90871051f04379956da6c5b803a9e66019a76ac9",
            "format": "executable",
            "member": null,
            "downloadBytes": 372904464,
            "installedBytes": 372904464
          }
        }
      }
    },
    {
      "id": "codex",
      "name": "Codex",
      "route": "paseoNative",
      "providerId": "codex",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "0.170.0",
        "executable": "codex",
        "launchArgs": [
          "app-server"
        ],
        "signInArgs": [
          "login",
          "--device-auth"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://github.com/openai/codex/releases/download/rust-v0.170.0/codex-package-aarch64-unknown-linux-musl.tar.gz",
            "sha256": "3f7e1ae38c4fe27322e082b855e8eec936d08fa538d62b71c07e2a53f34e8847",
            "format": "tarGz",
            "member": "bin/codex",
            "downloadBytes": 195884517,
            "installedBytes": 497311036
          },
          "x64": {
            "url": "https://github.com/openai/codex/releases/download/rust-v0.170.0/codex-package-x86_64-unknown-linux-musl.tar.gz",
            "sha256": "a77ff87be7ba809d715728270a78f5ce3527dc07529416d1ef8929b90b8475eb",
            "format": "tarGz",
            "member": "bin/codex",
            "downloadBytes": 193641516,
            "installedBytes": 501309254
          }
        }
      }
    },
    {
      "id": "antigravity",
      "name": "Antigravity",
      "route": "acpPaseo",
      "providerId": "antigravity",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "1.5.11",
        "executable": "agy",
        "launchArgs": [
          "acp",
          "--disable-telemetry"
        ],
        "signInArgs": [
          "acp",
          "--login"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.11-6132547114106880/linux-arm/cli_linux_arm64.tar.gz",
            "sha256": "de4fea15f83f40685cb30374b58337318d58340efcc8779a2eb1461b620b69f4",
            "format": "tarGz",
            "member": "antigravity",
            "downloadBytes": 91203180,
            "installedBytes": 252531376
          },
          "x64": {
            "url": "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.11-6132547114106880/linux-x64/cli_linux_x64.tar.gz",
            "sha256": "df652c98fdf519a87c017b4e1d92f3fdb66c344de16e5e685c596637bbd28334",
            "format": "tarGz",
            "member": "antigravity",
            "downloadBytes": 100130747,
            "installedBytes": 268841672
          }
        }
      }
    },
    {
      "id": "gemini",
      "name": "Gemini CLI",
      "route": "acpPaseo",
      "providerId": "gemini",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "0.66.0",
        "executable": "gemini",
        "launchArgs": [
          "--acp"
        ],
        "signInArgs": [],
        "artifacts": {
          "arm64": {
            "url": "https://registry.npmjs.org/@google/gemini-cli/-/gemini-cli-0.66.0.tgz",
            "sha256": "269734d35287fbd8ed2982534258ecf4a95f184b6e06e6732e0d6850c28701d9",
            "format": "npmTarGz",
            "member": "package/bundle/gemini.js",
            "downloadBytes": 20889095,
            "installedBytes": 98815455
          },
          "x64": {
            "url": "https://registry.npmjs.org/@google/gemini-cli/-/gemini-cli-0.66.0.tgz",
            "sha256": "269734d35287fbd8ed2982534258ecf4a95f184b6e06e6732e0d6850c28701d9",
            "format": "npmTarGz",
            "member": "package/bundle/gemini.js",
            "downloadBytes": 20889095,
            "installedBytes": 98815455
          }
        }
      }
    },
    {
      "id": "qwen",
      "name": "Qwen Code",
      "route": "acpPaseo",
      "providerId": "qwen",
      "signInMethod": "apiKeyHost",
      "recipe": {
        "version": "0.27.3",
        "executable": "qwen",
        "launchArgs": [
          "--acp"
        ],
        "signInArgs": [],
        "artifacts": {
          "arm64": {
            "url": "https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.27.3.tgz",
            "sha256": "313a567f05dd036b489b9142b0f20f21dfe348532e5d07b6f0818a47c5ac5333",
            "format": "npmTarGz",
            "member": "package/cli-entry.js",
            "downloadBytes": 43802478,
            "installedBytes": 159348420
          },
          "x64": {
            "url": "https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.27.3.tgz",
            "sha256": "313a567f05dd036b489b9142b0f20f21dfe348532e5d07b6f0818a47c5ac5333",
            "format": "npmTarGz",
            "member": "package/cli-entry.js",
            "downloadBytes": 43802478,
            "installedBytes": 159348420
          }
        }
      }
    },
    {
      "id": "kilo",
      "name": "Kilo",
      "route": "acpPaseo",
      "providerId": "kilo",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "7.8.27",
        "executable": "kilo",
        "launchArgs": [
          "acp"
        ],
        "signInArgs": [
          "auth",
          "login"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://registry.npmjs.org/@kilocode/cli-linux-arm64/-/cli-linux-arm64-7.8.27.tgz",
            "sha256": "992a0d0f4956926c6f624de742792eeab140516640f66a5cf5b7f9c357c06eb4",
            "format": "tarGz",
            "member": "package/bin/kilo",
            "downloadBytes": 83530144,
            "installedBytes": 258198520
          },
          "x64": {
            "url": "https://registry.npmjs.org/@kilocode/cli-linux-x64/-/cli-linux-x64-7.8.27.tgz",
            "sha256": "1da75cf0996f249abc9e1ae10bdcebcefd760dd375c440e704ae70bb9be3ce0b",
            "format": "tarGz",
            "member": "package/bin/kilo",
            "downloadBytes": 84123912,
            "installedBytes": 259201200
          }
        }
      }
    },
    {
      "id": "cline",
      "name": "Cline",
      "route": "acpPaseo",
      "providerId": "cline",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "3.0.74",
        "executable": "cline",
        "launchArgs": [
          "--acp"
        ],
        "signInArgs": [
          "auth"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://registry.npmjs.org/@cline/cli-linux-arm64/-/cli-linux-arm64-3.0.74.tgz",
            "sha256": "10c1f63c143b6f7eba3320543df2f6081232d79fb1830c293cf006115dce9049",
            "format": "tarGz",
            "member": "package/bin/cline",
            "downloadBytes": 54812128,
            "installedBytes": 162253096
          },
          "x64": {
            "url": "https://registry.npmjs.org/@cline/cli-linux-x64/-/cli-linux-x64-3.0.74.tgz",
            "sha256": "52502438a8d78a451156c38df2eb31116d658c94d1d5461b4e29761d06211bfc",
            "format": "tarGz",
            "member": "package/bin/cline",
            "downloadBytes": 55037574,
            "installedBytes": 161793504
          }
        }
      }
    },
    {
      "id": "goose",
      "name": "Goose",
      "route": "acpPaseo",
      "providerId": "goose",
      "signInMethod": "apiKeyHost",
      "recipe": {
        "version": "1.59.1",
        "executable": "goose",
        "launchArgs": [
          "acp"
        ],
        "signInArgs": [
          "configure"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://github.com/aaif-goose/goose/releases/download/v1.59.1/goose-aarch64-unknown-linux-gnu.tar.gz",
            "sha256": "e3619b2ebf76dbbe8924561fd748220f289a34671e2757cda1cd833e44194f8e",
            "format": "tarGz",
            "member": "./goose",
            "downloadBytes": 63280232,
            "installedBytes": 195103264
          },
          "x64": {
            "url": "https://github.com/aaif-goose/goose/releases/download/v1.59.1/goose-x86_64-unknown-linux-gnu.tar.gz",
            "sha256": "04c08900ab2ae78b8e2f556f1ed535a91b61c080a3ff62e9fd8b280cceb544ef",
            "format": "tarGz",
            "member": "./goose",
            "downloadBytes": 62448338,
            "installedBytes": 208579376
          }
        }
      }
    },
    {
      "id": "omp-acp",
      "name": "Oh My Pi",
      "route": "acpPaseo",
      "providerId": "omp-acp",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "18.20.0",
        "executable": "omp",
        "launchArgs": [
          "acp"
        ],
        "signInArgs": [
          "login"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://github.com/can1357/oh-my-pi/releases/download/v18.20.0/omp-linux-arm64",
            "sha256": "8b5e197db7c58b7ebabf83c6f03597f89bae3a766a84c6481b3a942a125f5e22",
            "format": "executable",
            "member": null,
            "downloadBytes": 244435960,
            "installedBytes": 244435960
          },
          "x64": {
            "url": "https://github.com/can1357/oh-my-pi/releases/download/v18.20.0/omp-linux-x64",
            "sha256": "c8694be8c6e6a1f45e3636dd8a8e9eaa78878ad629a89000b403ec5e456d3f89",
            "format": "executable",
            "member": null,
            "downloadBytes": 287484080,
            "installedBytes": 287484080
          }
        }
      }
    },
    {
      "id": "fx",
      "name": "fx",
      "route": "acpPaseo",
      "providerId": "fx",
      "signInMethod": "browserOAuthHost",
      "recipe": {
        "version": "0.0.16",
        "executable": "fx",
        "launchArgs": [
          "acp"
        ],
        "signInArgs": [
          "login"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://github.com/vercel-labs/fx/releases/download/v0.0.16/fx-linux-aarch64.tar.gz",
            "sha256": "ca9441b104357e2105039dbfd4c2eaf0d6ee65a84085f23f51e880c95cbf46af",
            "format": "tarGz",
            "member": "fx",
            "downloadBytes": 5562847,
            "installedBytes": 10832712
          },
          "x64": {
            "url": "https://github.com/vercel-labs/fx/releases/download/v0.0.16/fx-linux-x86_64.tar.gz",
            "sha256": "f0fb822c7be546c80bcc89d50521bc1454435e0c6f586e8420c4e2b84fa80599",
            "format": "tarGz",
            "member": "fx",
            "downloadBytes": 5680318,
            "installedBytes": 12814000
          }
        }
      }
    }
  ]
}
"""
