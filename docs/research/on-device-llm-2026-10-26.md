# On-device LLMs: best option for the app (2026-10-26)

**Decision (owner, 2026-10-26): not supported for now.** A model running on
the phone needs a very powerful phone, gets hot, uses most of the memory and
is still weak at real coding work. The idea is parked; the research below
stays for a later look (for example a model on a PC reached over Tailscale).


Question from the owner: should the app run models on the phone itself, maybe
Gemma, and what is the best way? Research only; nothing was built.

## Short answer

Run **llama.cpp's `llama-server` inside the built-in Linux**, next to the
in-app OpenCode 2 server, and add it to `opencode.json` as one more
OpenAI-compatible provider. OpenCode then drives it like any cloud model:
same chat, same tools, same permissions. No new client code, no new
protocol, and the first slice can ship as a Shorebird **patch**.

Offer two model choices: **Qwen3.5 4B** (best small tool caller found) and
**Gemma 4 E4B** (Google, Apache 2.0, the owner's question). Both are weak as
coding agents. Say so in the app.

GPU speed-up is a second step. It needs a native build and a new release.

## What the commenter asked for

No commenter named "Chad" exists. The private repo
`Eslamasabry/opencode-mobile` was not readable (GitHub tool: no access;
`gh` token expired). The public repo `Eslamasabry/opencode-mobile-next` was
readable. Its only local-model request is from **sahilsahilimtiyaz-code
(Sahil)** on issue #87, the issue #87 reporter. "Chad" is most likely a
voice-typing slip for "Sahil". His posts:

- **2026-10-25, "Native On-Device Local AI Models for Android"**. He wants:
  a model manager (download, "Import GGUF model files from Android storage",
  show size and memory needs, delete, warn on low memory); a native runtime,
  "potentially based on llama.cpp"; **full agent use, not chat only** ("Pass
  supported tool definitions to the model and execute tool calls through the
  agent's existing permission system"); offline use; Local and Cloud
  provider groups; "Never count local inference against OpenCode's cloud
  free-model quota"; "Offer conservative defaults for devices with limited
  RAM". Reference: [AndroidHarness](https://github.com/Sanuu7/AndroidHarness).
  His device for this report: Samsung Galaxy A54 (mid-range, 6–8 GB RAM).
- **Same post, part 2**: OpenCode's "Free usage limit reached" error shows up
  when he believes usage was low. Separate problem; see the end.
- **2026-10-25, follow-up**: "many local model should be in app if possible",
  pointing at AirLLM (loads one layer at a time; "1-3 minutes per response").
- **2026-10-21**: wants GLM models (Z.ai) selectable in OpenCode (option 1).

AndroidHarness (MIT, about 100 stars) does GGUF download and import,
converts Qwen and Llama safetensors to Q4 on the phone, and runs its own
agent. It recommends small models (SmolLM2, Qwen 2.5/3) and warns that
"speed and tool reliability depend on the model and device".

## Facts about this app that decide the answer

- The phone server is OpenCode 2 inside proot Ubuntu. Its config is
  `/root/.oc-opencode2/config/opencode/opencode.json`
  (`lib/builtin/agents/gen_ui_install_scripts.dart` already merges entries
  into it with a Python script).
- The Providers page already lists custom providers declared in
  `opencode.json` ("catalog source config",
  `lib/ui/screens/library/integrations_page.dart`). The model picker shows
  their models.
- Optional downloads already exist as setup components (voice, AI Team) in
  `lib/builtin/setup/components.dart`, with a total-RAM preflight
  (`preflight.dart`) and a thermal guard (`lib/builtin/thermal_guard.dart`).
- Native executables ship as `jniLibs` (`libproot.so`, `libaiteam_engine.so`,
  `useLegacyPackaging = true`). The release APK is about 105 MB.
- Owner's phone: nubia Z60 Ultra, NX721J, Snapdragon 8 Gen 3 (SM8650,
  Adreno 750, Hexagon v75), 16 GB, Android 15. OpenCode already uses about
  0.4–0.6 GB there. The OEM battery manager has force-stopped the app before.
- The emulator is x86_64: fine to test the flow, useless for speed.

## The options

| Option | Fits OpenCode's agent? | Runs on owner's phone? | Speed (8 Gen 3 class) | Native / Shorebird | Verdict |
|---|---|---|---|---|---|
| **A. llama-server (CPU) in proot Ubuntu** | Yes. OpenAI-compatible `/v1`, tool calls with `--jinja` | Yes, any arm64 | Decode ~10–15 tok/s for 4B Q4; prefill slow (see below) | None. Official `ubuntu-arm64` build (18 MB) downloads into the rootfs. **Patchable** | **First slice** |
| **B. llama-server with Adreno GPU (OpenCL) or Hexagon NPU** | Yes, same endpoint | Snapdragon 8 Gen 2/3/Elite only | Prefill several times faster than CPU | Needs the Android (bionic) build, not Ubuntu's. Native, new release, APK +25–60 MB unless downloaded | Second slice, if A's prefill is the blocker |
| C. Ollama in proot | Yes (Ollama provider exists) | Yes | Same as A or worse (CPU only here) | None, but a bigger daemon | No gain over A |
| D. LiteRT-LM / flutter_edge_ai (Gemma) in the app | No. Runs in the app process, has no chat endpoint (`litert-lm serve` is embeddings only). We would write an OpenAI shim and tool-call bridge | Yes | Best on GPU: Gemma 3n E2B ~16 tok/s decode, ~816 tok/s prefill (S24 Ultra); Gemma 4 E2B ~52 tok/s decode (S26 Ultra GPU) | New plugin, native code, new release; a multi-GB model in the UI process risks killing the app | Not now. Good later for small in-app jobs (titles, summaries) |
| E. Gemini Nano (AICore, ML Kit Prompt API) | No | **No.** Device list has no nubia/ZTE/RedMagic | n/a | Plugin, release | Rejected: foreground only (`BACKGROUND_USE_BLOCKED`), per-app quotas, no tool calling listed |
| F. MLC LLM | Has a server, but a heavy build per model | Maybe | GPU via OpenCL | Native | No. llama.cpp is simpler and newer |
| G. AirLLM (layer streaming) | No | Not usefully | Minutes per reply on laptops; worse on phone flash | n/a | Rejected |

### Why A first

- Zero client protocol work. OpenCode already speaks OpenAI-compatible
  providers (`@ai-sdk/openai-compatible`, `baseURL: http://127.0.0.1:<port>/v1`,
  per-model `tool_call: true` and `limit.context`).
- llama.cpp publishes ready builds. Release `b11889` (2026-10-25) has
  `llama-…-bin-ubuntu-arm64.tar.gz` (18 MB, CPU, with `llama-server`) and
  `llama-…-bin-android-arm64-snapdragon.tar.gz` (62 MB, OpenCL + Hexagon
  v73–v85 libraries).
- It runs inside the phone server's process tree. Start, stop, thermal pause
  and the background limits already apply to that tree.
- It is Dart plus shell scripts plus downloads. No pubspec or `jniLibs`
  change, so it can go out as a Shorebird patch.
- The same provider writer also serves "a model on my PC over Tailscale"
  (llama-server or Ollama on a desktop). That gives far better models with
  the same code.

### Why the GPU path is native

Ubuntu in proot uses glibc. The phone's GPU drivers (`/vendor/lib64/libOpenCL.so`)
are Android bionic libraries, and a glibc program cannot load them. So
llama-server inside Ubuntu is **CPU only**. GPU needs the Android build of
llama-server, started outside Ubuntu (as Termux does with
`LD_LIBRARY_PATH=/vendor/lib64`). Whether an app-sandboxed executable may
load the vendor OpenCL library, and use the Hexagon DSP, must be proven on
the owner's phone. llama.cpp lists Adreno 750 (8 Gen 3) as verified for
OpenCL and calls the Hexagon backend experimental. Vulkan on Android GPUs
has a poor record in llama.cpp.

## Models (approximate, Q4_K_M GGUF)

| Model | Licence | Disk | RAM with 32k context | Tool calling | Notes |
|---|---|---|---|---|---|
| Qwen3.5 4B | Apache 2.0 (verify) | ~2.7 GB | ~4–5 GB | BFCL-v4 0.503, "marginal" | Best small tool caller found |
| Gemma 4 E4B | Apache 2.0 | ~5 GB | ~6–7 GB | Native function calling; reports of weak agentic coding | Text + image + audio; 128K context |
| Gemma 4 E2B | Apache 2.0 | ~3 GB | ~4 GB | Below the ~4B reliability line | Fast; chat and explain only |
| Qwen3.5 9B | Apache 2.0 (verify) | ~5.7 GB | ~8 GB | BFCL-v4 0.661, "usable" | 12–16 GB phones only (owner's phone yes, Sahil's A54 no) |
| Gemma 4 26B-A4B (MoE) | Apache 2.0 | ~15 GB | too big | reported tool-call issues | PC only |

Gemma 4 came out on 2026-04-02 under Apache 2.0 (earlier Gemma used Google's
own licence). Sizes: E2B, E4B, 26B MoE, 31B dense. Sources agree that tool
reliability drops sharply below about 4B parameters.

## Honest limits

- **The first reply is slow on CPU.** OpenCode sends a long system prompt plus
  tool definitions, probably 10–15k tokens (measure it in the spike). CPU
  prefill for a 4B model on a phone is roughly 50–150 tok/s, so the first
  reply of a new chat can take **1.5–4 minutes**. Later turns are faster
  because llama-server reuses the cached prefix. GPU prefill (hundreds of
  tok/s) brings this to tens of seconds. This is why slice 2 exists.
- **Small models are weak agents.** Expect wrong tool arguments, loops and
  half-done edits on anything past small, single-file tasks. Good for:
  explain this file, write a small function, offline Q&A. Not a Claude or
  GPT replacement.
- **Memory.** A 4B model plus 32k context needs about 4–5 GB on top of
  OpenCode. 6–8 GB phones will hit the low-memory killer. Gate by total RAM.
- **Heat and battery.** Sustained inference heats phones fast. Reuse the
  thermal guard; unload the model when idle.
- **Background.** Android 15 caps `dataSync` foreground services at 6 hours
  per 24 h, and OEM killers stop the app anyway. A local run must survive a
  stop by refetch, like other OpenCode runs.
- **Storage.** 3–6 GB per model, plus the rootfs.

## Recommended plan

**Slice 0, spike (about a day, no app code).** In the app terminal on the
owner's phone (with permission) and on the emulator for flow only:
download the `ubuntu-arm64` llama.cpp build and two GGUFs (Qwen3.5 4B,
Gemma 4 E4B); run `llama-server --jinja -c 32768 --port 8090`; add the
provider to `opencode.json` by hand; run three real tasks (read and explain a
file, a one-file edit, run a test and fix it). Record first-reply time,
tok/s, peak RAM, temperature and tool-call success. Go or no-go, and which
model is the default. Evidence in `docs/qa/on-device-llm-<date>/README.md`.

**Slice 1, "Local model" component (patchable).** Finish line: a user picks
"Local model" in phone setup, it downloads, and the model appears in the
picker as "This phone · Qwen3.5 4B", works offline, and is removed with the
component. Non-goal: GPU, model import, a model store.
- Setup component like voice: pinned llama.cpp build and one model, both
  with SHA-256 checks; RAM gate (4B needs 8 GB or more, 9B needs 12 GB or
  more); free-space check.
- Write and remove the provider block in `opencode.json` (reuse the Python
  merge pattern); set `tool_call` and `limit.context` to match `-c`.
- Start llama-server on first use, stop after idle, stop with the phone
  server; thermal guard pauses it.
- In the picker, a plain note: "Runs on this phone. Slow to start and weak at
  multi-step coding."
- No pubspec or native change, so it can ship as a Shorebird patch.

**Slice 2, GPU (native release).** Spike the Android Snapdragon llama-server
started from `nativeLibraryDir` (like `libproot.so`) with the vendor OpenCL
library; fall back to CPU when it fails. Ships only in a new Shorebird
release; decide APK size (bundle vs per-device download).

**Slice 3, model manager.** Curated list (Gemma 4 E2B/E4B, Qwen3.5 4B/9B),
import a GGUF from phone storage, delete, show memory needs; "Local" and
"Cloud" groups in the picker. Also the same writer for "a model on my PC"
(Tailscale URL), which is the better answer for real coding work.

**Not doing:** Gemini Nano, an in-process Flutter LLM plugin for the agent,
AirLLM, Ollama on the phone.

## Owner decisions

1. Run slice 0 (a day of spike work, may use your phone's app terminal)?
   Yes / no.
2. Default model if both pass: Qwen3.5 4B (better tools) or Gemma 4 E4B
   (Google, multimodal)? Recommendation: let the spike's tool-call results
   pick; offer the other as a choice.
3. Minimum phone: 8 GB RAM for 4B (excludes many 6 GB mid-range phones,
   maybe Sahil's A54)?
4. GPU slice: accept a native release and +25–60 MB APK (or a per-device
   download) for much faster first replies?
5. Reply to Sahil on #87: local models planned as above; the "Free usage
   limit reached" report is OpenCode Zen's quota and goes to upstream
   (anomalyco/opencode#58835) as a separate item. The app should show that
   error in plain words with a Details section (owner's no-raw-errors rule).

## Sources

- Issue #87 comments: https://github.com/Eslamasabry/opencode-mobile-next/issues/87
- AndroidHarness: https://github.com/Sanuu7/AndroidHarness
- llama.cpp releases (b11889 assets): https://github.com/ggml-org/llama.cpp/releases
- llama.cpp Snapdragon backend: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md
- llama.cpp OpenCL backend (Adreno 750/830 verified): https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
- llama.cpp Vulkan on Android, slow: https://github.com/ggml-org/llama.cpp/discussions/9464
- LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM
- LiteRT-LM blog, Gemma 4 speeds (2026-05-19): https://developers.googleblog.com/en/blazing-fast-on-device-genai-with-litert-lm/
- Gemma 3n LiteRT-LM model card: https://huggingface.co/google/gemma-3n-E4B-it-litert-lm
- Gemma 4 release, Apache 2.0: https://9to5google.com/2026/04/02/google-gemma-4/ ,
  https://the-decoder.com/googles-gemma-4-is-now-available-with-apache-2-0-licensing-for-the-first-time/
- ML Kit GenAI (devices, foreground only, quotas): https://developers.google.com/ml-kit/genai
- Small-model tool calling (BFCL-v4 table): https://d-central.tech/local-llm-agent-capability/
- OpenCode + llama.cpp provider setup: https://medium.com/@anish.rai3737/run-a-local-llm-for-coding-with-llama-cpp-opencode-8eaa5b0db73b ,
  llama.cpp function calling: https://mintlify.com/ggml-org/llama.cpp/advanced/function-calling
- Phone llama.cpp CPU numbers (Pixel 9 Pro, 3B Q4: pp ~79, tg ~14 tok/s): https://redlib.hbubli.cc/r/LocalLLaMA/comments/1glx6a5/phone_llms_benchmarks
- NX721J = nubia Z60 Ultra, SM8650: https://browser.geekbench.com/v6/cpu/18453446
- Flutter packages (pub.dev): flutter_edge_ai 2.5.3 (was flutter_gemma), llamadart 0.16.2
