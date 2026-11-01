# Geist fonts in `:ui:kit`

Copied byte for byte from the Flutter app's `assets/fonts/geist/` (plan 06 §4.4). Licence: SIL Open Font License 1.1,
shipped as `src/commonMain/composeResources/files/licenses/geist_ofl.txt`.

| Kit resource (`src/commonMain/composeResources/font/`) | Flutter source | SHA-256 |
|---|---|---|
| `geist_variable.ttf` | `Geist-Variable.ttf` | `cdcc4815cbf5f9882fa74e48f8ab410a0495781a58ff7316570f664e7e987753` |
| `geist_italic_variable.ttf` | `Geist-Italic-Variable.ttf` | `c5db6397dae7993afb72e397277c5a5308ba32de010eb19ec2b8b73f5e9d3ec4` |
| `geist_mono_variable.ttf` | `GeistMono-Variable.ttf` | `0e1af3f507a1c8dfbb03d13ffad585834cd45ed7ccb78c756c7ce7873d180d30` |
| `geist_mono_italic_variable.ttf` | `GeistMono-Italic-Variable.ttf` | `e5800990ff5069667f4ff0a5dc582b260597e0adc022a887d9d103fc4cdd2cf2` |
| `files/licenses/geist_ofl.txt` | `OFL.txt` | `c683bfbcc7e087f5d37a54ef628f10387c451a83ddc459b151403a164ac46c90` |

This file sits outside `composeResources/font/` because the resource generator accepts font files only there.
