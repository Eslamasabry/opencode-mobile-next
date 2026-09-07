# Remaining usage — synthetic widget preview

The optional provider collector view uses the production Flutter screen, app theme
and bundled fonts with synthetic data. This is not a provider-account capture,
device screenshot, live quota check, or TalkBack run.

Reproduce from the repository root with the pinned Flutter development toolchain:

```sh
OC_QUOTA_CAPTURE=docs/qa/provider-quota/remaining-usage.png \
  flutter test --no-pub --concurrency=1 test/provider_quota_screen_test.dart \
  --plain-name "synthetic remaining usage rendered preview"
```

The capture is 411×1100 logical pixels, DPR 1. The same test file separately
checks 360×740 at 2.5× text with a 320dp keyboard inset, keyboard actions,
light/dark semantics, 48dp targets and reduced motion. Those automated checks
do not establish physical-device accessibility or native iOS compatibility.

![Synthetic remaining usage](remaining-usage.png)

The operator must separately install and secure the collector as described in
[`tool/quota/README.md`](../../../tool/quota/README.md). Reading the Remaining page requires consent for that screen visit. The separate
**Enable quota monitoring** dialog can persist source/account-specific polling
consent; device alerts require another opt-in. Up to three sources are read per
cycle (five minutes in the foreground, fifteen minutes while an existing live
background service is active). Larger source lists take multiple cycles, and
Android may stop the service. Monitoring never starts a service or switches the
active OpenCode server.

Monitoring stores source/account hashes, policy and dedupe markers, not quota
measurements or provider credentials. Expired measurements disappear. Native
alerts describe a past threshold observation and offer a fresh review; they do
not assert that the provider is still above the threshold. Provider-account
changes reject the old route. Unavailable refreshes retain a review/retry path.
Disable and profile deletion revoke the monitoring configuration.

`tool/capture/quota_monitor_test.dart` captures the production consent/review
screens in light and dark themes from memory-only synthetic fixtures. These
captures and controller/widget tests establish local behavior, not successful
collector deployment, permitted access to a particular user's provider account,
Android notification delivery, background longevity or physical-device
accessibility. No live provider-account checks were made for this slice.

Codex, MiniMax and GLM have collector implementations with their distinct
contracts documented in the collector README; GLM uses a pinned
provider-maintained plugin contract, not a promised general stable API. Claude
and Gemini remain unavailable. Actual-consumption budgets use server-reported
USD/token totals; they are personal alerts for an exact query period, not a
provider allowance or an enforced spending cap.
