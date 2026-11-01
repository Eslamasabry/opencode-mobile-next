**Verdict: NO-GO.** The plan still contains migration failures, incomplete credential recovery, and conflicting security and release contracts.

Read-only review. Files changed during review; this report incorporates the section 03 revision timestamped **17:48 GST, 1 November 2026**, and section 05’s **17:46 GST** revision. No files were modified or builds run. The optional remote implementation was not inspected because the GitHub connector denied access under the current approval policy.

**Status of Sol’s seven blockers**

| Earlier blocker | Status in the latest text |
|---|---|
| Writer admission/drain race | Fixed in 03 §17.3 by registering outstanding work before admission checking and draining it. |
| Compose compile SDK mismatch | Fixed in 03’s conventions with API 37 minor level 1; **not reconciled with the binding ADR**. |
| Forbidden dependencies for address, link and credential types | Original dependency violations addressed through relocated types and transport seams. |
| Incompatible storage contracts | **Partially fixed.** Contract ownership is documented, but incompatible `ProfileScope` definitions remain. |
| Automatic mutation retries | Addressed in 04 through one-shot bodies, response interception and HTTP/1.1. |
| R2 replaying completed outbound work | Addressed by prohibiting R2 after the first gate opening; additional tombstones and holds are specified. The new post-send R2 test contradicts that eligibility rule. |
| Forbidden gateway, migration and socket dependencies | Original type-placement violations addressed. |

“Fixed” here means fixed in the specification, not independently demonstrated by executable tests.

**BLOCKER**

**B1. The authoritative document still overrides several fixes**

- **Where:** [README, precedence rule](/home/eslam/Storage/Code/wt/native-plan/README.md:3); [01 §§8, 9.7, 10.3](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:479); 03 §§0.3, 6.3–6.4, 10.5; 05 §§3.3, 7.1.
- **Problem and evidence:** README explicitly makes 01 authoritative. That ADR still requires synchronous first-launch backup in `Application.onCreate`, while revised 03/05 prohibit it. It still treats compileSdk 37.1 as conditional, while 03 now requires it. AndroidX confirms that consuming applications and libraries must update to 37.1. [Compose release notes](https://developer.android.google.cn/jetpack/androidx/releases/compose-ui?hl=en).
  
  Storage contracts also remain incompatible: 03’s `ProfileScope` exposes `registrations` and `sweep`; 05’s exposes registration methods, `scopedKeys`, `ownedData` and `reservedSegments`. Consumers and tests use both shapes.
- **Concrete fix:** Amend 01 before approval. Publish one compilable contract per shared type, remove obsolete executable examples, and compile representative migration, deletion and scheduler consumers against those contracts. A later reconciliation task is not a completed contract freeze.

**B2. Lost-photo migration can either miss the photo or block the entire upgrade**

- **Where:** [05 §7.4, M3/L4b and M5](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:895); [10 §16.3](/home/eslam/Storage/Code/wt/native-plan/plan/10-media-voice.md:2082).
- **Problem and evidence:** The new L4b writes the pending-photo record and an import marker, but M5’s permitted mutation set still names only M2’s re-entry flags and M3’s L1–L3 transforms. Following both instructions produces `Blocked(AuthorityChanged)` after a successful photo import.
  
  L4b also describes `oc.pendingPromptPhoto.path` and `oc.pendingPromptPhoto.*` as though they were preference keys. The existing app stores **one JSON value**, `oc.pendingPromptPhoto`. The critical recovery case has an origin record with **no path and no ref**; only the plugin cache knows the returned photo. That is explicitly handled by [Flutter’s `recoverLostData()`](/home/eslam/Storage/Code/wt/oc_app-batch1/lib/state/prompt_photos.dart:304) and by section 10, but not consistently by L4b’s condition.
- **Concrete fix:** Specify one migration step against the actual JSON schema. Recover plugin-only results when the origin exists and both result fields are absent. Use `MigrationWriter`, include the exact JSON key and marker in M5’s allowed changes, and require a full migration test that reaches `GateState.Open` with the attachment in the original conversation.

**B3. Credential deletion and rollback reconciliation lack a durable transaction**

- **Where:** [05 §4, M2 retry policy, §7.5 and §9.3](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:1368).
- **Problem and evidence:** The revised R1 guard deletes stale FSS credentials and then removes `oc.native.staleLegacySecrets`. On return, native must distinguish those guard deletions from deletions made by the person—but the guard does not persist the consumed list anywhere.
  
  Concrete failure: native changes password A to B; R1’s guard deletes legacy A and clears the list; the process dies. Native returns and sees a credential present in its baseline but absent from FSS. Without the missing transition record, its reconciliation rule can delete B.
  
  There is another resurrection path: M2 retries flagged credentials whenever `SecretStore` has no value. A deliberate reset also produces “no value.” The retry algorithm does not consult a deletion tombstone or credential revision before importing the old FSS value.
- **Concrete fix:** Define a durable credential transition journal with mutation revisions, deletion tombstones and consumed guard-deletion records. Persist invalidation before changing either credential store. Make M2 retries respect deliberate deletion. Fault-test native change/reset → R1 guard → process death → native return, including two rollback cycles.

**MAJOR**

**M1. Profile deletion leaves private content in migration backups**

- **Where:** [16 §1.4, `FlutterEraSweep`](/home/eslam/Storage/Code/wt/native-plan/plan/16-security-privacy-release.md:276); [05 §3.5](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:397).
- **Problem and evidence:** The backup cleaner removes profile-shaped keys and FSS entries. But queued prompts, drafts and profile records live inside app-wide JSON values such as `oc.offlineQueue`, `oc.sessionDrafts` and `oc.profiles`. Those keys do not match `oc.<what>.<id>`.
  
  Section 05 explicitly performs structural collection edits for live data; section 16 does not require those edits in S0, replaced or start-fresh copies. Deleting a server can therefore leave its prompt text and address in retained plaintext backups. Testing only registered key shapes misses this.
- **Concrete fix:** Apply the same structural ownership cleanup to every retained copy, including authoritative `.bak` files. Test embedded records, unattributed drafts and widget snapshots—not just matching key names. Preserve queued prompts only under the person’s explicit keep-as-drafts choice.

**M2. Exported launch intents have contradictory authority**

- **Where:** [16 §1.8.1](/home/eslam/Storage/Code/wt/native-plan/plan/16-security-privacy-release.md:407); [07 §§7.4–7.7](/home/eslam/Storage/Code/wt/native-plan/plan/07-screens-navigation.md:1549).
- **Problem and evidence:** Section 16 says any app can supply `MainActivity` extras, but those extras are navigation-only and network-silent until a tap. Section 07 makes `oc.shortcut=new_task` execute `CreateSession`, and `usage_target` connect another server before opening its target.
  
  An allowlisted string is not proof that an intent came from the app’s own shortcut or widget. Implementing section 07 directly violates section 16’s security promise.
- **Concrete fix:** Separate authenticated internal actions from untrusted exported arrivals. Use a non-exported destination for app-owned PendingIntents where appropriate; require confirmation before external arrivals connect or mutate. Test explicit intents from another application and assert zero gateway mutations before confirmation.

**M3. The updater is not specified to work on supported API 26–27 devices**

- **Where:** [16 §8.3, verification steps 6–7](/home/eslam/Storage/Code/wt/native-plan/plan/16-security-privacy-release.md:1412); 01 §8, minSdk 26.
- **Problem and evidence:** The pipeline unconditionally uses `GET_SIGNING_CERTIFICATES`, `signingInfo` and `longVersionCode`. The signing API and long-version accessor were introduced in API 28. No API 26–27 branch is specified, although those devices remain supported. The verification tests fake the archive inspector, so they cannot catch this platform failure. [Android `PackageInfo` reference](https://developer.android.com/reference/android/content/pm/PackageInfo.html).
- **Concrete fix:** Specify and implement the legacy signature inspection path below API 28, use a compatible version-code accessor, and run a real update-verification/install journey on API 26. Include wrong signer, wrong package and lower-version APKs.

**M4. The WebView policy does not enforce its claimed origin boundary**

- **Where:** [16 §1.8.5](/home/eslam/Storage/Code/wt/native-plan/plan/16-security-privacy-release.md:490); [17 §2.4](/home/eslam/Storage/Code/wt/native-plan/plan/17-gap-closure.md:540).
- **Problem and evidence:** The plan treats `shouldOverrideUrlLoading` as the mechanism preventing unwanted cross-origin navigation. Android explicitly documents that it is not called for POST requests. It also is not a general filter for page subresources or JavaScript network traffic. A page can therefore initiate traffic outside the described link-confirmation path. [Android WebViewClient reference](https://developer.android.com/reference/android/webkit/WebViewClient.html).
  
  The justification for disabling Safe Browsing says previews load only the phone’s own servers; section 17 explicitly supports remote development servers.
- **Concrete fix:** Define whether the requirement covers navigation alone or all preview egress. Test form POSTs, redirects, frames, fetches and WebSockets against that requirement. Implement a boundary that actually covers the chosen policy, and correct the remote-server/Safe Browsing assumption. Do not present a navigation callback as a network sandbox.

**M5. The version-code rules can produce an uninstall-only dead end**

- **Where:** [15 §2.6](/home/eslam/Storage/Code/wt/native-plan/plan/15-delivery-roadmap.md:294); 05 §9.1; [11 CR11-18 and §6.4 R3](/home/eslam/Storage/Code/wt/native-plan/plan/11-android-platform-native.md:92).
- **Problem and evidence:** Section 15 restricts stable builds to 10000–19999 and starts owner candidates at 10010. Revised section 05 allocates above every hosted or installed number. Section 11 records signed local builds **900055 and 900058** already installed on an emulator.
  
  These rules cannot all hold. Refusing a downgrade is necessary, but it does not supply a data-preserving upgrade route.
- **Concrete fix:** Use one authoritative allocator and ledger across native candidates, bridge builds and R1. Remove the conflicting upper range and fixed starting numbers. Validate every produced APK against the allocation, and require an in-place journey over the highest supported existing build without uninstalling.

**M6. The M0 readiness gate omits the checks added to resolve the audits**

- **Where:** [15 §4, M0 exit](/home/eslam/Storage/Code/wt/native-plan/plan/15-delivery-roadmap.md:383); [03 §23](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:3800); 15 §4.2 and Appendix A.
- **Problem and evidence:** Section 15 requires checks **1–14**. Section 03 added **15–17**: contract compilation, update-triggered background restoration, and manifest/startup security checks. The roadmap can therefore label M0 passed without the newly required contract and manifest evidence.
  
  Its W2 finish line also promises frozen slice 1–3 contracts and runnable parity flows, while all Flutter flows finish in W4 and catalog top-up continues through W5. Appendix A does not schedule lane work; it assumes those dependencies finish in their assigned weeks. That leaves the advertised dates dependent on unmodelled readiness.
- **Concrete fix:** Generate milestone gates from one checklist. Require checks 15 and 17 at M0, and assign check 16’s harness and implementation proof to explicit gates. Require the relevant catalog delta and baseline tests before freezing each affected contract. Recalculate dates with test lanes, coordinator capacity and emulator reservations represented as dependencies and resources.

**M7. Feature removal can pass under defaults rather than an owner decision**

- **Where:** [01 §10.1 and §12](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:735); [02 §§3.3–3.6 and §10](/home/eslam/Storage/Code/wt/native-plan/plan/02-principles-scope.md:296).
- **Problem and evidence:** The parity rule requires owner approval for deferred features. The decision table nevertheless says work proceeds on defaults; the scope file accepts a decision reference such as `"D6"`; the readiness gate accepts “owner answers or recorded defaults.”
  
  Consequently, AI Team/FactsBook deferral, A2A removal and other capability losses can disappear from the switch denominator without evidence that the owner accepted those losses. A referenced unanswered question is not approval.
- **Concrete fix:** Distinguish proposed defaults from accepted decisions in machine-readable scope records. Defaults may guide planning, but release scope must require an explicit approval record for each removal or deferral. Publish both the original behavior count and the approved switch subset.

**MINOR**

**N1. The headline size comparison uses different APK configurations**

- **Where:** [README §1](/home/eslam/Storage/Code/wt/native-plan/README.md:18); [12 §11](/home/eslam/Storage/Code/wt/native-plan/plan/12-package-registry.md:1044).
- **Problem and evidence:** README compares today’s 107 MB app with a native target below 22 MB. Section 12 identifies the published Flutter arm64 split as approximately **36 MB**, and budgets native using an arm64 split.
- **Concrete fix:** Compare identically signed release artifacts for the same ABI and feature payload. Separate savings from framework replacement from savings caused by deferred features or changed packaging.

**N2. The new R2 replay journey cannot exercise the specified production path**

- **Where:** 05 §9.4 and §10.5, `J2-R2`.
- **Problem and evidence:** R2 is prohibited after `gate.firstOpenAt`. The new journey sends a prompt and fires a schedule, forces `Blocked`, and then restores with R2. Sending already required the gate to open; forcing `Blocked` does not make `firstOpenAt` null.
- **Concrete fix:** Make the production journey assert that R2 is refused after first use. Test tombstone partitioning separately, or explicitly label any eligibility-bypass test as a lower-level recovery test rather than a supported user journey.

**Top five changes first**

1. **Freeze one authoritative revision:** amend 01, reconcile shared contracts, and remove obsolete examples.
2. **Repair and prove photo migration end to end:** plugin-only recovery, exact JSON write set, and successful M5 completion.
3. **Make credential transitions durable:** deletion tombstones, persisted R1 guard records, retry suppression and crash-point tests.
4. **Complete deletion across every retained copy:** structurally remove embedded profile data and verify the actual backup bytes.
5. **Make readiness enforceable:** one version allocator, complete milestone gates, explicit scope approvals, and resource-backed dates.