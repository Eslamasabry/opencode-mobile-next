"""Turns one suspect drawing effect off in the source tree before a CI emulator build (bisect of the 2026-11-09
"opening a chat crashes the emulator" bug: qemu segfaults with "Failed to find ColorBuffer" under swiftshader).

python3 -I tool/qa/emu-chat/suspect.py <native-dir> <suspect>

Only the CI workflow native-chat-emu.yml runs it, on a throwaway checkout. Every edit must find its anchor, or the
script fails, so a moved anchor never passes as "no crash without it".
"""
import pathlib
import sys

KIT = "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/"
ANDROID_KIT = "ui/kit/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/kit/"


def edit(root, rel, old, new, count=1):
    p = root / rel
    s = p.read_text()
    if s.count(old) < count:
        sys.exit(f"anchor not found in {rel}: {old[:60]!r}")
    p.write_text(s.replace(old, new))
    print(f"edited {rel}")


def frost_source(root):
    # The chat's recording of its body and transcript into a GraphicsLayer (kitFrostSource), every style.
    edit(root, KIT + "foundation/KitFrost.kt",
         "state.layer.record { this@drawWithContent.drawContent() }\n"
         "            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { state.version++ }\n"
         "            drawLayer(state.layer)",
         "drawContent()")


def frost_chat(root):
    edit(root, "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt",
         "Box(Modifier.fillMaxSize().kitFrostSource(frost)) {", "Box(Modifier.fillMaxSize()) {")


def frost_transcript(root):
    edit(root, "ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/transcript/TranscriptList.kt",
         ".keepsBottom(pin).kitFrostSource(backdrop)", ".keepsBottom(pin)")


def offscreen(root):
    # Every offscreen compositing layer (edge fades, phrase chips, live sweep, fresh-text fade, code overflow, masks).
    n = 0
    for p in list((root / "ui").rglob("*.kt")) + list((root / "feature").rglob("*.kt")):
        if "/build/" in str(p) or "Test" in p.name or "/desktop" in str(p):
            continue
        s = p.read_text()
        t = s.replace("CompositingStrategy.Offscreen", "CompositingStrategy.Auto")
        if t != s:
            p.write_text(t)
            n += 1
    if n == 0:
        sys.exit("no CompositingStrategy.Offscreen found")
    print(f"offscreen: {n} files")


def blur(root):
    edit(root, ANDROID_KIT + "platform/AndroidKitPlatform.kt",
         "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) AndroidFrost.effect(spec, density)\n        else null",
         "null")


def shader(root):
    edit(root, KIT + "live/LiveLooks.kt",
         "fun androidRunsLiveShaders(sdkInt: Int): Boolean = sdkInt >= LIVE_SHADER_MIN_SDK",
         "fun androidRunsLiveShaders(sdkInt: Int): Boolean = sdkInt < 0")


def sampler(root):
    edit(root, ANDROID_KIT + "platform/AndroidKitPlatform.kt",
         "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AndroidBackdropSampler() else null", "null")


SUSPECTS = {
    "none": lambda root: print("baseline: nothing changed"),
    "frost-source": frost_source,
    "frost-chat": frost_chat,
    "frost-transcript": frost_transcript,
    "offscreen": offscreen,
    "blur": blur,
    "shader": shader,
    "sampler": sampler,
}

if __name__ == "__main__":
    root, name = pathlib.Path(sys.argv[1]), sys.argv[2]
    for part in name.split("+"):
        if part not in SUSPECTS:
            sys.exit(f"unknown suspect {part}; known: {', '.join(SUSPECTS)}")
        SUSPECTS[part](root)
