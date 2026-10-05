# Where MacroDime can be tested

Written for a machine with no Mac. Every layer below runs somewhere that is not
this laptop, and the columns say plainly what each one does and does not prove.

| # | Layer | Runs on | Proves | Does not prove |
| --- | --- | --- | --- | --- |
| 1 | Engine tests | WSL on this machine | The science, the swap engine, currency conversion, the dietary filter, the plan audit, the catalogue sync, totals that add up, MacroDime Pro's copy and rules (158 tests) | Anything that draws a view |
| 2 | Syntax gate | WSL on this machine | No unbalanced braces or bad interpolation in the SwiftUI half | That it type-checks |
| 3 | Build and unit tests | GitHub Actions, `macos-15` | The app compiles, the test target runs in a simulator, the privacy manifest is present and valid | That it launches |
| 4 | **Run it** | GitHub Actions, `macos-15` | The app launches, SwiftData opens its store, the catalogue seeds, four screens render | Gestures, scrolling feel, sound, haptics |
| 5 | Real device | TestFlight, needs the $99 membership | It works on hardware, with a real store on disk | Nothing important |
| 6 | Click around by hand | A rented remote Mac, optional and paid | Layout, previews, view hierarchy debugging | Your problem if you skip it |

Layer 4 is the answer to "where can I test the app today" on iOS. For Android,
the answer is your own phone, below.

## Android: on your phone, today

Android needs no store account to test. The test build is a release build
(shrunk by R8, so it runs at the speed users will get) signed with this
machine's debug key so a phone will install it. Play App Signing re-signs the
real one; this build is for testing only.

Build it from `android/` (Git Bash, JDK 21 on `JAVA_HOME`):

```
./gradlew :app:assembleRelease
"$ANDROID_HOME/build-tools/36.0.0/apksigner.bat" sign \
  --ks ~/.android/debug.keystore --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out build/MacroDime-test.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

Get it onto the phone, any of three ways:

1. **WhatsApp or email it to yourself** and tap the attachment on the phone.
2. **Google Drive:** upload `MacroDime-test.apk` from the PC's browser, open it
   in the Drive app.
3. **USB:** copy it into the phone's `Download` folder, open it in Files. Or,
   with USB debugging on, `adb install -r build/MacroDime-test.apk`.

Android then asks to allow installs from that app (Settings opens; switch
"Allow from this source" on and go back). Play Protect may warn that it does
not recognise the developer, because the app is not from Play: choose
**More details, Install anyway**. To update, install the new APK over the old
one; your data stays.

No Android phone? Upload the same APK at appetize.io and pick Android.

### What to tap, and what each step proves

Since 2026-10-04 the app opens on a three-screen intro and, after setup, on the
subscription screen, which is the way in. Start the trial as a license tester
(free) on the build installed from the internal testing link. The sideloaded
test APK is signed with a different key, and Google Play's billing may refuse
it, which would leave it on that screen.

| # | Do this | Expect |
| --- | --- | --- |
| 0 | Open the app, pick two of the five problems, continue | The photo intro, then your two picks answered, and only those, then Welcome |
| 1 | Finish onboarding, ticking the health acknowledgement | Build my week unlocks only after the tick; "Your week is ready" shows your targets, budget and today's planned meals with their cost |
| 1b | Start the trial | Android 13 and later ask about notifications first; then Google Play's sheet (for a license tester it shows Google's 3-minute test trial; customers get 14 days); then Today opens with every meal of the day planned |
| 2 | Plan tab: add a food, then change its servings | Totals, rings and the day's cost move together, and each meal's header is exactly its lines added up |
| 2b | Plan tab: move a week ahead | "Nothing planned for this day" with Plan this day for me; tapping it fills the day |
| 3 | Open a meal's swaps, apply one | The saving shown on the card is the saving applied; macros stay within the stated tolerance |
| 4 | Groceries tab, straight after step 2 | The food you added is already on the list, without tapping anything |
| 5 | Settings: currency ZAR, rate 18.5 | Every amount shows in rand, about 18.5 times the dollar figure, and every meal header and grocery section still adds up to the rand lines under it |
| 6 | Switch the currency back to USD | Every amount returns to the original dollar figure (this was a real bug: $9.00 used to read $166.50) |
| 7 | Settings: Delete All My Data | The app returns to the intro with nothing left |
| 8 | Turn on dark mode and the largest font size, revisit each tab | Nothing clipped, overlapping or unreadable |

Report anything that looks wrong with a screenshot and the step number.

## 1. The engines, on this machine

```
wsl -d Ubuntu
export PATH=/opt/swift/usr/bin:$PATH
cd /mnt/c/Users/SIYA/projects/macrodime
swift test --scratch-path /root/.mdbuild
```

Expect `Executed 158 tests, with 0 failures`. This is the only layer that is
genuinely verified rather than merely compiled, and it is where the three bugs
found on 2026-09-20 surfaced: the vegetable swap gap, the locale currency bug and
these tests' own discovery that an unconstrained dietary profile silently removed
food from the catalogue.

## 2. The syntax gate, on this machine

```
swiftc -parse $(find MacroDime -name '*.swift' | sort)
```

Silence is success. This catches the class of mistake that is invisible in an
editor: an unbalanced brace, a broken string interpolation, a stray keyword. It
does **not** catch a wrong type, a missing member or a bad API call, because
`parse` never resolves an import. `SwiftUI`, `SwiftData` and `StoreKit` are
Apple-only and closed source, so no toolchain on this machine can type-check them.
That is what layer 3 is for.

## 3. Compile and unit tests in CI

Triggered on every push, and manually from the Actions tab.

```
gh workflow run ios.yml
gh run watch
```

Read the job summary: it collects every compiler diagnostic into one list, so one
run gives the whole fix list rather than stopping at the first error. Free on a
public repository; on a private one, macOS minutes bill at ten times the Linux
rate.

## 4. Running the app, which is the real test

```
gh workflow run screenshots.yml -f device="iPhone 16 Pro Max"
gh run watch
gh run download <run-id>
```

What it does, in order: boots the simulator, builds for it (no signing needed,
which is why this works with no Apple account), installs, launches with
`-MacroDimeScreenshots` so the store has a real day in it and `-MacroDimeTab <n>`
so it opens on a chosen tab, waits, checks the process is still alive, and only
then captures the screen. It does that four times, once per tab.

What you get back:

| File | What to look for |
| --- | --- |
| `screenshots/today.png`, `plan.png`, `groceries.png`, `settings.png` | What the four screens actually look like. These are also the App Store screenshots |
| `logs/<tab>.out` | The app's own stdout. If `MacroDimeApp.init` fatalErrors on the model container, the message naming the entity is here |
| `logs/<tab>.err` | Swift runtime warnings, including constraint and concurrency complains |
| `logs/<tab>.launch` | The pid the simulator reported, which is how the liveness check knows the app started at all |

A crash fails the job and prints those logs into the run, plus any `.ips` crash
report written in the last fifteen minutes. That last part is deliberate: an app
that dies on launch would otherwise still produce a black PNG, and a workflow that
reports success on a black screenshot teaches you nothing.

## Reading the first failure

The first run is expected to fail. There are three shapes it can take, and they
mean different things.

- **`Could not create the model container` in `logs/*.out`.** The SwiftData schema
  is wrong: a relationship that does not resolve, a duplicate unique constraint,
  a property type SwiftData will not store. The message names the entity. This is
  the failure the project has been carrying as an unknown since it was written.
- **A crash report, with no stdout.** A `View` body did something illegal. The
  `.ips` file names the process and the top frames, which point at the file.
- **No crash, but the wrong picture.** Screenshots show an empty list, a stuck
  spinner or a card overlapping another. The app is alive, so no diagnostic fires;
  the screenshot is the evidence. This is the case where a rented Mac earns its
  keep, because layout cannot be iterated on through a 10 minute CI round trip.

## What still cannot be tested anywhere

- **Purchases.** StoreKit's local testing needs Xcode, so there is no way to test a
  purchase without the membership. Once the Paid Applications Agreement is active,
  purchases are tested in TestFlight with a sandbox tester. See
  `docs/MONETISATION.md`.
- **Anything requiring signing**, including push notifications, until the
  membership exists.
- **Review behaviour.** Only a real submission tells you what App Review thinks.
