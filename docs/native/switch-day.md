# Switch day: moving the owner's phone from Flutter to native

The steps for installing the first native release over the Flutter app on the owner's phone, in order. Story E00-S16.
Read [release-identity.md](release-identity.md) and [release.md](release.md) first.

The update must install **in place**: same package, same signer, higher version code. Then Android keeps the app's
data (projects, servers, the built-in Linux, settings) and the native app migrates it on first launch. If any of the
three does not hold, Android refuses the update and the only way past is an uninstall that deletes the data. So every
check below happens **before** anything is installed, and the preflight stops at the first problem.

## Before the day

- [ ] The owner's phone carries the **Owner local** lineage (`1DE5BF08…D60C`, decision D-01). Native APKs for this phone
      are signed with that key, on the maintainer's machine only.
- [ ] A native release APK built from a tagged release, signed with Owner local, passed
      `native/tool/release/identity_gate.sh --expect-signer 1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C <apk>`.
- [ ] The same release has been installed over Flutter 2.1.4 on an emulator (upgrade journey, `native/tool/qa/upgrade_e2e.sh`).
- [ ] The owner has a current backup of anything they cannot lose (projects pushed to their remotes).

## On the day

1. Connect the phone with USB debugging on; unlock it. Disconnect every other device or emulator.
2. Run the read-only preflight with the lineage the phone should hold, and the APK you intend to install:

   ```bash
   native/tool/qa/owner_preflight.sh \
     --expect-signer 1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C \
     --apk native/build/native-release/v3.0.0+10010/opencode-mobile-3.0.0-10010-arm64-v8a.apk
   ```

   It only reads: the installed package and its signer (by copying `base.apk` off the phone and checking it with
   `apksigner`), the installed versionCode, the free space on `/data`, and the app's data size (which holds the
   built-in Linux). It takes no screenshot, installs nothing, changes nothing.
3. **If it prints `REFUSED` / `STOP`, stop.** Do not install anything. Most likely causes:
   - *signer is not the expected lineage*: this phone holds a different key; an APK for the other lineage can never
     update it. Ask the owner; never try another key.
   - *not above the installed versionCode*: the phone has a newer test build; take a new number with
     `native/tool/version_code.sh allocate --kind release --installed <its code>` and rebuild.
   - *not enough free space*: free space on the phone first.
4. When it prints `PASS`, install the APK as an update (never uninstall first):

   ```bash
   adb install -r native/build/native-release/v3.0.0+10010/opencode-mobile-3.0.0-10010-arm64-v8a.apk
   ```

5. Open the app. The migration screen runs once; wait for it to finish. Then check, in this order:
   - [ ] the server list and the last chat are there;
   - [ ] the built-in Linux starts and the projects in it are there;
   - [ ] placed home-screen widgets and Quick Settings tiles still work;
   - [ ] scheduled prompts are still listed;
   - [ ] notifications arrive with the owner's existing sound and importance choices.
6. Record the installed versionCode with `native/tool/version_code.sh seen --code <code> --for "owner phone, switch day"`.

## If something is wrong after the install

Fix forward ([release.md](release.md) › Fixing a broken release): a new release with a higher code installs over this
one. Never roll back to the Flutter APK: its version code is lower and Android would refuse it.
