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
    "version": "v24.22.3",
    "sha256": {
      "arm64": "261af2af250dfd7e9f25cf151d1b2713a0adf8f10a9ad116c4947f68627affe8",
      "x64": "061ec3e3062115705b56b51fa9cc330ab50d34b6969387d4e4b1a50c6809acc2"
    }
  },
  "claudeCodeTestedVersions": [
    "2.1.283",
    "2.1.294",
    "2.1.316",
    "2.1.319",
    "2.1.321",
    "2.1.325",
    "2.1.327",
    "2.1.330"
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
        "version": "2.1.330",
        "executable": "claude",
        "launchArgs": [],
        "signInArgs": [
          "auth",
          "login",
          "--claudeai"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://downloads.claude.ai/claude-code-releases/2.1.330/linux-arm64/claude",
            "sha256": "6016f90cac783e367fb1aa97c768a2efb94a0128a5b46b70fb3d8fcbbd9dd444",
            "format": "executable",
            "member": null,
            "downloadBytes": 379971000,
            "installedBytes": 379971000
          },
          "x64": {
            "url": "https://downloads.claude.ai/claude-code-releases/2.1.330/linux-x64/claude",
            "sha256": "f390eaa0814e6a1bff74cbb47acb74cb9c89d30ced809fe0af5af00c7bfe8ebb",
            "format": "executable",
            "member": null,
            "downloadBytes": 381121040,
            "installedBytes": 381121040
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
        "version": "0.171.0",
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
            "url": "https://github.com/openai/codex/releases/download/rust-v0.171.0/codex-package-aarch64-unknown-linux-musl.tar.gz",
            "sha256": "d8c43bee6cf11b01eb18b95f4d7961092bc40228180808859e70dabb7784c1c9",
            "format": "tarGz",
            "member": "bin/codex",
            "downloadBytes": 199315347,
            "installedBytes": 516528332
          },
          "x64": {
            "url": "https://github.com/openai/codex/releases/download/rust-v0.171.0/codex-package-x86_64-unknown-linux-musl.tar.gz",
            "sha256": "8391d67757496bfd14893eca6cbf0c96994bf2627566ff1f018f70600c9ef6b3",
            "format": "tarGz",
            "member": "bin/codex",
            "downloadBytes": 197763180,
            "installedBytes": 521571182
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
        "version": "1.5.14",
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
            "url": "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.14-5302690847260672/linux-arm/cli_linux_arm64.tar.gz",
            "sha256": "e5f69a3123fac66974b303d9a201f381a0fb32b3ef869d9139da85d5caf67a2a",
            "format": "tarGz",
            "member": "antigravity",
            "downloadBytes": 92116379,
            "installedBytes": 256070320
          },
          "x64": {
            "url": "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.5.14-5302690847260672/linux-x64/cli_linux_x64.tar.gz",
            "sha256": "d58386322d2073e96b33f7cc32b4e9ae2db6228f58440369725967b2de48c7b5",
            "format": "tarGz",
            "member": "antigravity",
            "downloadBytes": 101195950,
            "installedBytes": 273240776
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
        "version": "0.28.4",
        "executable": "qwen",
        "launchArgs": [
          "--acp"
        ],
        "signInArgs": [],
        "artifacts": {
          "arm64": {
            "url": "https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.28.4.tgz",
            "sha256": "bd9daa7bef7ab4cccea8f74372d1924f54637412f73bd21242e6740c145fdfc5",
            "format": "npmTarGz",
            "member": "package/cli-entry.js",
            "downloadBytes": 44773289,
            "installedBytes": 162973081
          },
          "x64": {
            "url": "https://registry.npmjs.org/@qwen-code/qwen-code/-/qwen-code-0.28.4.tgz",
            "sha256": "bd9daa7bef7ab4cccea8f74372d1924f54637412f73bd21242e6740c145fdfc5",
            "format": "npmTarGz",
            "member": "package/cli-entry.js",
            "downloadBytes": 44773289,
            "installedBytes": 162973081
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
        "version": "7.8.32",
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
            "url": "https://registry.npmjs.org/@kilocode/cli-linux-arm64/-/cli-linux-arm64-7.8.32.tgz",
            "sha256": "ef6c6384d319fd19bf2d7c731c99f32d83e15ed5732955358ca6420292cde447",
            "format": "tarGz",
            "member": "package/bin/kilo",
            "downloadBytes": 83986745,
            "installedBytes": 264227832
          },
          "x64": {
            "url": "https://registry.npmjs.org/@kilocode/cli-linux-x64/-/cli-linux-x64-7.8.32.tgz",
            "sha256": "d37d2822060210cf9069ec7548f8866c737b391f46f0d24653c3049064b4c9dd",
            "format": "tarGz",
            "member": "package/bin/kilo",
            "downloadBytes": 84585073,
            "installedBytes": 265234608
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
        "version": "1.60.2",
        "executable": "goose",
        "launchArgs": [
          "acp"
        ],
        "signInArgs": [
          "configure"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://github.com/aaif-goose/goose/releases/download/v1.60.2/goose-aarch64-unknown-linux-gnu.tar.gz",
            "sha256": "7e74d796e93b89df374715dc669149cb634f74d778de0b87bb7c031b5b093731",
            "format": "tarGz",
            "member": "./goose",
            "downloadBytes": 64482004,
            "installedBytes": 199216392
          },
          "x64": {
            "url": "https://github.com/aaif-goose/goose/releases/download/v1.60.2/goose-x86_64-unknown-linux-gnu.tar.gz",
            "sha256": "5f6e463621a8428617ae4870eecee7a00a253e9bbfccb6340dfaa4f0ec2aaf32",
            "format": "tarGz",
            "member": "./goose",
            "downloadBytes": 63566822,
            "installedBytes": 213003128
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
        "version": "0.0.17",
        "executable": "fx",
        "launchArgs": [
          "acp"
        ],
        "signInArgs": [
          "login"
        ],
        "artifacts": {
          "arm64": {
            "url": "https://github.com/vercel-labs/fx/releases/download/v0.0.17/fx-linux-aarch64.tar.gz",
            "sha256": "b4633b8987ec43f1b9454e4629f3b5a4f1e086aae36b84ae4fefeacd6a53761f",
            "format": "tarGz",
            "member": "fx",
            "downloadBytes": 4550277,
            "installedBytes": 6869488
          },
          "x64": {
            "url": "https://github.com/vercel-labs/fx/releases/download/v0.0.17/fx-linux-x86_64.tar.gz",
            "sha256": "78c155d59fa59e42fd56d93037fd75041868c9c67865e8c7f0ca6dcdfac23e75",
            "format": "tarGz",
            "member": "fx",
            "downloadBytes": 4769247,
            "installedBytes": 8833664
          }
        }
      }
    }
  ]
}
"""
