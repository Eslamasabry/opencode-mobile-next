# Public-alpha launch kit

## Positioning

OpenCode Mobile is an unofficial Android client for an OpenCode server the user
controls. It lets developers follow live sessions, approve tools, answer
questions, and review diffs away from their terminal. It can also guide an
advanced user through running OpenCode on the phone with Termux.

Do not describe it as an official OpenCode app, a hosted agent, a model
provider, production-ready, or Play Store distributed. OpenCode 2 support is a
pinned beta contract. Android arm64 is the primary public-alpha target.

## Launch gate

Publish broadly only when all of these are recorded on the release commit:

- Android quality, Linux, Windows, and SDK-regeneration workflows are green.
- The Android 12-16 smoke matrix and reconnect/background checks are recorded.
- A recoverable release keystore is backed up outside GitHub and its public
  certificate matches the release workflow and notes.
- The exact APK SHA-256, signer SHA-256, source commit, package ID, version,
  minimum Android version, and ABI are in the release body.
- Installation and update are tested from the previous public release.
- Private vulnerability reporting remains enabled and monitored.

## Core proof points

- Streaming transcripts and tool activity from OpenCode 1 and OpenCode 2.
- Explicit permission cards, questions, forms, and phone-sized diff review.
- Optional Android background status and privacy-safe notifications.
- Local voice transcription; audio is not uploaded by this app.
- Direct connection to the user's server; no developer-operated account,
  advertising, product analytics, or automatic crash upload.
- Optional guided Termux hosting for advanced on-device use.

## GitHub release intro

> OpenCode Mobile is an independent Android client for an OpenCode server you
> control. Follow streaming sessions, inspect tool activity, approve commands,
> answer questions, and review diffs from your phone. This is a public alpha:
> read the compatibility and signing notes before sideloading.

## X

I built OpenCode Mobile, an unofficial Android client for OpenCode.

Follow live runs, approve tools, answer questions, review diffs, or run the
server on-device through Termux. No hosted account or product analytics.

Public alpha, source + arm64 APK:
https://github.com/Eslamasabry/opencode-mobile-next

## Reddit

**Title:** I built an unofficial Android client for OpenCode

OpenCode Mobile connects directly to an OpenCode server you control. It streams
sessions, shows tool activity, handles permission requests and questions,
provides phone-sized diff review, and can optionally set up OpenCode on-device
through Termux.

It is MIT-licensed and in public alpha. The APK is currently arm64-only and
sideloaded, and OpenCode 2 support follows a pinned beta contract. I am looking
for concrete feedback on reconnect behavior, background use, and different
server setups.

Source, screenshots, demo, and APK:
https://github.com/Eslamasabry/opencode-mobile-next

This is an independent community project, not an official OpenCode app.

## Hacker News

**Title:** Show HN: OpenCode Mobile - an unofficial Android client for OpenCode

I built a Flutter client for controlling an OpenCode server from Android. It
supports streaming sessions, tool permissions, questions/forms, diff review,
share-to-session, local voice transcription, and optional on-device hosting
through Termux.

There is no hosted account or analytics service; the client talks to the server
the user chooses. The project is MIT-licensed and in public alpha. The current
distribution is an arm64 sideload APK, with experimental desktop builds.

Repository and demo:
https://github.com/Eslamasabry/opencode-mobile-next

## Discord

I am testing OpenCode Mobile, an independent Android client for OpenCode. It can
follow live sessions, handle permissions and questions, review diffs, and
optionally run OpenCode locally through Termux. It is a public alpha and ships
as an arm64 sideload APK. Feedback on server compatibility and
background/reconnect behavior would be especially useful:
https://github.com/Eslamasabry/opencode-mobile-next

## Channel order

1. Publish one verified GitHub prerelease as the source of truth.
2. Share in the OpenCode community/Discord and ask for protocol feedback.
3. Post the vertical video natively on X and link to the release.
4. Post to focused OpenCode and self-hosting communities after checking each
   community's self-promotion rules.
5. Use Show HN only after real-device acceptance and upgrade installation pass.

Do not buy followers, mass-DM users, astroturf comments, or post the same copy
across unrelated communities. Reply to every substantive early report and turn
reproducible feedback into a linked issue.
