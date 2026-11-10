# FactsBook F6: memory on this phone, proven on an emulator (2026-10-11)

Evidence record. The real `factsbook-node` runs **inside the app's built-in Linux** on an Android x86_64 emulator (AVD `FB_F6`, never the owner's phone), is started, paired and removed by the app itself, and the F5 trigger screens were driven against it. Release APK built from `feat/factsbook-ui` with `--dart-define=OC_FACTSBOOK=true` (sha256 `bd160b29ec13db1560cd335712c0d79ffbebc7a524bb94f3630c921618762432`). Screens are 1080 x 2400, one per image. The device PIN prompt sets `FLAG_SECURE` and cannot be captured; the PIN was typed blind and the result is the next image.

## The node build

Source: `/home/owner/Storage/Code/factsbook`, commit `91a8c8bf361423c22f9002566d9635515c7b57f9` (`git archive`, clean tree; the later commits `d4399fc` and `c4767d1` only touch tests and docs). Toolchain: Rust 1.92 from `rust-toolchain.toml`, `cargo-zigbuild` and `ziglang` 0.17.0 from a Python venv (no sudo, no system C compiler), `rustup target add`.

```
CARGO_HOME=/home/owner/Storage/cache/cargo-home \
CARGO_TARGET_DIR=/home/owner/Storage2/fb-target-<target> \
cargo +1.92 zigbuild --release -j 2 --target <target> -p fb-node --bin factsbook-node
```

| Target | Used for | sha256 |
| --- | --- | --- |
| `aarch64-unknown-linux-musl` | the owner's phone (static, stripped) | `7732f4fd632a11f4a8421bcb45015758c1b2489134311f199d55e60e2559417d` |
| `x86_64-unknown-linux-musl` | the emulator (static, stripped) | `0cdafd50d3d5944b218ff0a8abd6f94f78489b12855ed76b43613e1e8f7d2e29` |

Both were built only while no other `cargo` ran. The aarch64 binary was not run (no arm64 device here); only the x86_64 one was exercised.

Where the app looks for the build (no download address exists, so none is made up; `TODO(factsbook-release)` in `lib/builtin/factsbook_node_host.dart`):

`/sdcard/Android/data/io.github.eslamasabry.opencode_mobile/files/factsbook/factsbook-node-<aarch64|x86_64>`, with an optional `.sha256` file beside it that must match. Pushing it with adb: `adb push` then `adb shell chmod 755 .../files/factsbook` (the folder adb creates is otherwise unreadable by the app).

## How it works

- Reuses `BuiltinLinux.run` and `startService` (service name `factsbook`); no new MethodChannel, nothing on the Kotlin side changes.
- The app copies the build into the Linux (`/usr/local/bin/factsbook-node`), checks it runs, writes a random passphrase to `/root/.oc-builtin/factsbook.passphrase` (mode 600, inside the app), makes `/root/.factsbook` (config listens on `127.0.0.1:8787`, loopback only) **as the node's own user inside the Linux** (the node refuses a folder another user owns; the first attempt, which made it from Dart, failed on exactly that), runs `init`, starts `serve`, waits for it to answer, asks `pair --scopes read,write,admin,owner --owner-key <this phone's key>`, and pairs with the code from that one JSON line. The code and the passphrase are never logged, shown or kept in an error.
- The passphrase lives on the same phone as the vault (nothing leaves it): this is the "no sensitive unlock, no cloud, no other devices" scope. If the file is lost the vault cannot be opened; Remove deletes both.
- Stop, Restart, Start, Remove (confirm names what is lost), log under Details. States: not set up, no build, ready, installing (copying, creating, starting, connecting), running, stopped, failed (plain words, reason under Details).

## Images

| File | What it shows |
| --- | --- |
| 10-about-developer.png | About, Developer: FactsBook switch and "Run memory on this phone". |
| 11-memory-on-phone-ready.png | Ready to install. |
| 13-after-run.png | After one tap: installed, started, paired; running. |
| 14-about-you.png | About you served by the node on this phone. |
| 15-chats-home-memory.png | After the app was reinstalled the node had stopped: Memory says it could not be reached. |
| 16-memory-stopped.png | The page says stopped, data kept; Start brings it back and re-pairs nothing new. |
| 18-remove-confirm.png | Remove: what is lost, in plain words. |
| 50-stopped.png | After Stop memory. |
| 51-removed.png | After Remove: data deleted, ready to install again. |
| 20-triggers-empty.png | Triggers against the real node: no rules yet. |
| 21-rule-editor-filled.png | Rule editor, named. |
| 23-rule-draft-test.png | Draft test on the real node (`POST /v1/rules/test`): "It matches." |
| 25-rule-saved.png | Rule saved (owner-signed, template version 2). |
| 26-triggers-with-rule.png | Triggers with the rule. |
| 31-add-share-text.png | Add something, pasted text, "Let my rules read this". |
| 32-share-saved-as-event.png | Saved: signed as ordinary text and posted to `/v1/events` (not the inbox). |
| 33-triggers-after-share.png | The rule fired: an offer card, the rule, the message in Recent messages. |
| 34-why-this-fired.png | Why this fired: claims, trust, rule, what it found, what it proposed, no original text. |
| 35-memory-care-proposal.png | The reminder proposal with Approve, Edit, Reject, Skip and Why this fired. |
| 36-edit-proposal-sheet.png | Edit opens the usual reminder check. |
| 37-proposal-edited.png | Edited (owner-signed `decision: edit`): still waiting for a separate Approve. |
| 38-proposal-approved.png | Approved. |
| 40-needs-you-offer.png | The offer card in Needs you. |
| 41-new-chat-seeded.png | Talk about this: a new chat with the summary only; accepting is pending until the first message is sent. Leaving without sending reported the offer cancelled (42). |
| 42-triggers-offer-cancelled.png | The offer is gone after leaving without sending. |
| 43-notification-access.png | Notification access: not available yet. |
| ar-01-triggers.png, ar-02-why-this-fired.png, ar-03-memory-on-phone.png | Arabic, right to left. |

## Findings

1. The node moved to a **B5 contract** while this slice was being built (capabilities endpoint, template version 2, sender claim, draft test, owner-classified share/chat events, `/v1/events` instead of `/v1/inbox`, proposal edit, client-reported first message). The F5 code was changed to follow it (commit "follow the node's B5 trigger contract"). Before that the app's rule save was refused by the real node (`template.version` must be 2), which the fake node had hidden.
2. Reinstalling the app kills the in-app Linux's services, so the node stops; the page says stopped and Start brings it back with the same vault and pairing.
3. Files adb pushes into the app's folder are not readable by the app until the folder is made world-readable (above).
4. The host disk was at 97%: the emulator needs a 5 GB data partition (`disk.dataPartition.size=5G`).

## Not done

- The aarch64 binary was not run on a phone; a release asset and its download are a TODO.
- No automatic start of the node when the app opens (the page starts it on request).
- The owner's phone was never touched.
