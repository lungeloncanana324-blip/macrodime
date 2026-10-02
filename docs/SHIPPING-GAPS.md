# Shipping MacroDime: what is done, what is missing

Written 2026-09-20, against `e515cbe` plus the vegetable, currency, dietary and
onboarding work in this branch. Updated 2026-09-26 at `79c3a8c`, after the
first compile of that work and the first launch of the app.

This is a gap report, not a plan with dates. It is ordered by what actually
blocks a submission, so the top of the list is the work that has to happen before
App Store Connect will accept a build at all.

## 0. Google Play first (added 2026-10-02)

Google Play is now the first store. A SwiftUI app cannot be submitted to Play,
so `android/` is a native Kotlin and Jetpack Compose port: the same engines,
catalogue, prices and rules, with the iOS tests ported case for case. How to
build and sign it is in `android/README.md`; every Play Console answer is in
`docs/play-store-listing.md`.

### What blocks a Play release

| # | Gap | What it needs | Who | Cost |
| --- | --- | --- | --- | --- |
| P1 | **12 testers for 14 days** | New personal accounts must run a closed test with at least 12 testers opted in for 14 consecutive days before production access. The critical path: recruit them first | Lungelo | 14 days minimum, then up to a week of review |
| P2 | **Upload key** | Done 2026-10-03: `android/macrodime-upload.jks` with its password in `android/keystore.properties`, both git-ignored. Back both up outside the repository. The first signed bundle (versionCode 1) is built; the next upload needs `-PversionCode=2` | Lungelo, back-up only | 5 min |
| P3 | **First run on a real phone** | Upload the signed bundle to the internal testing track and install it from the Play link (steps in `docs/play-store-listing.md`), or sideload `android/build/MacroDime-test.apk`. The app has launched on an emulator, never on hardware | Lungelo | 30 min |
| P4 | **Privacy policy URL live** | Done 2026-10-03: `https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html` returns the policy. Use it in Play Console's store listing and in App content, Privacy policy | done | 0 |
| P5 | **App content declarations** | Data safety, health apps, target audience 18+, content rating, ads, financial features: answers written in `docs/play-store-listing.md` | Lungelo | 45 min |
| P6 | **Public contact email** | Play shows it on the listing | Lungelo | 5 min |
| P7 | **Phone screenshots** | The `android.yml` screenshots job captures every tab from an emulator once the branch is pushed | CI | 0 |

### What is verified, and by what

| Layer | Verified how |
| --- | --- |
| `android/core` (engines) | 145 JUnit tests on the JVM: the iOS suite case for case, plus the salmon dinner's seven swap candidates matched to the cent against the compiled Swift engine's printed output |
| Room database and repository | 19 tests against real SQLite (Robolectric): seeding, swaps, grocery regeneration, deletion, damaged values |
| The app, end to end | 8 Compose UI tests driving the real screens: onboarding, the acknowledgement gate, input validation, the food picker, a reviewed swap, the grocery list, Delete All My Data, editing the profile |
| How it looks | Every screen rendered in light, dark, a 360 dp phone and large text (`ScreenshotTest.kt`), and reviewed by eye |
| Store rules | Target API 36 (required since 31 August 2026); the release build fails if any permission is merged into the manifest; cloud backup excluded so "no data collected" holds |

The app has launched on an emulator: `android.yml` run `36955987904` (2026-10-02)
booted it with demo data and captured onboarding and every tab in light and
dark, with no crash. That was the debug build.

Not verified: a real device, and the R8-shrunk release build actually running.
Since 2026-10-02 there
is a test APK of that release build, signed with the debug key so a phone will
install it (`docs/TESTING.md`, Android section); it builds at 2.0 MB, requests
no permissions, and Room's `MacroDimeDatabase_Impl` survives shrinking under its
own name, but it has still not been launched.

### Bugs the port found in the iOS app

Found while porting and fixed on Android first. **Fixed on iOS 2026-10-02**,
each with the same tests as Android:

1. **A leftover rate multiplied dollar prices.** Choose ZAR, type 18.5, switch
   back to USD: every price was multiplied by 18.5 under a dollar sign ($9.00
   read $166.50), and budgets typed afterwards were divided by 18.5. Choosing
   ZAR before typing a rate labelled every dollar amount as rand. Fixed in
   `Money.swift`: `CurrencySettings` applies the code and the rate only when
   `isConverting` (`shownCode`). Tests: `testALeftoverRateIsNotAppliedToDollars`,
   `testACurrencyWithNoRateYetStillShowsDollars`.
2. **Price refreshes never reached existing installs.** `CatalogSeeder` reran
   only when `catalogVersion` was bumped by hand, and `prices.yml` never bumps
   it. The seeder now compares stored values with the catalogue on every launch
   (`Engine/CatalogSync.swift`, pure, so tested on Linux in
   `CatalogSyncTests`, including an install seeded with the hand prices and
   upgraded to the sourced ones). Knock-on: the seeder was the app's only
   `UserDefaults` user, so `PrivacyInfo.xcprivacy` now declares no
   required-reason API, and `ios.yml` fails the build if code starts using
   `UserDefaults` or `@AppStorage` without declaring CA92.1.
3. **The onboarding budget warning ignored the chosen currency.** Now formatted
   through the draft's `CurrencySettings`, like the meters beside it.
4. **A food tripping two exclusions gave a reason that varied between
   launches** (exclusions were read from a `Set`). Now checked in declaration
   order; the test asserts soy sauce under "no soy, no gluten" reports gluten,
   from sets of different capacities.
5. **The grocery list waited for a tap on Regenerate,** so a meal added on the
   Plan tab was missing from it. It now rebuilds when the tab is shown or the
   week changes (`.task(id: weekStart)`), keeping ticks, "already have" marks
   and lines added by hand, as Android does.

Items 2 (the seeder half) and 5 touch SwiftData and SwiftUI, so they are
type-checked only by `ios.yml` and behaviour-checked only by running the app.
All five compile: `ios.yml` run `36955987896` at `8a54c0f` built the app and
passed 136 tests, both as SwiftPM and hosted in the app on a simulator, the 12
new ones among them. What no test reaches is the behaviour on screen: the
grocery list rebuilding when its tab opens, and an existing install picking up
new prices, still need a tap through in Appetize or on a device.

Product observation, same on both platforms: carb bases swap freely within
their category, so the engine can offer dried pasta in place of potatoes in a
dinner. Macro-valid, culinarily debatable; the vegetable fix (culinary
families) is the pattern if it needs tightening.

## 1. Blockers: no build can be submitted without these

| # | Gap | What it needs | Who | Cost |
| --- | --- | --- | --- | --- |
| 1 | **Apple Developer Program** | Enrolment with legal name, tax and banking details | Lungelo | $99/yr, 1 to 3 days |
| 2 | **Privacy policy URL** | Done 2026-10-03: live at `https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html` (GitHub Pages, branch `master`, folder `/docs`). The site publishes only the home, policy and support pages; `docs/_config.yml` excludes the working notes | done | 0 |
| 3 | **Support URL** | Done 2026-10-03: live at `https://lungeloncanana324-blip.github.io/macrodime/support.html`, including the questions a reviewer would ask and the refund route | done | 0 |
| 4 | **Screenshots** | Done in principle: run `36270743631` of `screenshots.yml` launched the app on all four tabs and captured them at 1320x2868 (iPhone 16 Pro Max, the 6.9 inch size). Check them against the listing copy, and use a run from after the quantity fix (gap 20) | Lungelo | 10 min |
| 5 | **A TestFlight build that runs** | `codemagic.yaml` has the signed workflow ready. The app has now launched in a simulator, so this is the first run on real hardware rather than the first run at all | Lungelo and CI | half a day |
| 6 | **App Store Connect record** | Bundle id `com.lungelo.macrodime`, name, category (Health and Fitness), age rating questionnaire | Lungelo | 45 min |
| 7 | **Age rating** | Answer yes to medical and treatment information. That is the honest answer for an app that prescribes a calorie deficit, and it lands at 12+ | Lungelo | 5 min |
| 8 | **App Privacy answers** | "Data not collected" for every category, matching `PrivacyInfo.xcprivacy`. No account, no analytics, no network calls at all | Lungelo | 20 min |
| 9 | **Export compliance** | Declared in `project.yml` (`ITSAppUsesNonExemptEncryption = NO`), so there is no per-build question | done | 0 |

## 2. Product gaps a reviewer, or a user in week two, will hit

| # | Gap | Severity | Note |
| --- | --- | --- | --- |
| 10 | **The SwiftUI half has launched, but has not been used** | High | 2026-09-26: the app opens its SwiftData store, seeds demo data and renders Today, Plan, Groceries and Settings without a crash, and the 103 tests pass hosted inside it. Still unexercised: onboarding end to end, a first save from real input, the food picker, a swap, a sheet. Appetize is where to try those by hand (`BUILDING-WITHOUT-A-MAC.md`), and `docs/TESTING.md` says what to look for |
| 11 | **Disclaimer text is unreviewed** | High | `HealthDisclaimer` is general knowledge written by a developer, not checked by a clinician or a lawyer. The support link is NEDIC, which is Canadian; SADAG is the South African equivalent and a better fit |
| 12 | **Currency localisation is manual** | Medium, bug fixed | The reported bug (a device-locale symbol on a USD amount) is fixed: the catalogue currency is explicit, and a user can enter their own currency and rate in Settings. There is deliberately no bundled rate table, because an invented rate is worse than no conversion. Real localisation means localising the price table itself |
| 13 | **Fibre, sodium and micronutrients are not tracked** | Medium | Disclosed in the plan audit rather than hidden. Fibre would be the most valuable next engine feature, and it needs catalogue data that is not authored yet |
| 14 | **No data export** | Medium | Delete-all exists; export does not. Apple does not require it, but it is the honest counterpart, and it is the first thing a user asks for when changing phones |
| 15 | **iPhone only** | Low | `TARGETED_DEVICE_FAMILY: "1"`. iPad support means layout work that cannot be judged without a device |
| 16 | **English only, US-centric food** | Low | 57 US supermarket items. A South African launch needs local staples and prices: a content project, not a code change |
| 21 | **30 of 57 prices are still estimates** | Low, improved 2026-09-26 | 27 foods now carry official averages: 11 from BLS monthly data, 16 from USDA ERS fruit and vegetable prices carried forward with the CPI. `scripts/update_prices.py` writes them into `SourcedPrices.swift` at build time and `prices.yml` refreshes them on the 20th of each month, so the app still never goes online. The rest (fish, oils, nuts, most dairy, specialty breads) have no public average; a store API such as Kroger's would cover them, but only through a server, and it would change the App Privacy answers. A free `BLS_API_KEY` secret makes the monthly job independent of the shared no-key quota |
| 17 | **No accessibility audit** | Low | Labels exist on rings, meters and cards. Largest Dynamic Type, and VoiceOver on the planner, have never been checked by a human |
| 18 | **No undo for a swap** | Low | A swap applies immediately. The engine is conservative (10% macro tolerance) but a mistake has no one-tap reversal |
| 19 | **No way to charge for it** | Decided, not built | Decided 2026-09-20: launch free with no in-app purchases, and add one StoreKit 2 subscription in a later build. Nothing about payments can be tested until the Paid Applications Agreement is active. The whole plan, including what stays free and the sandbox test list, is in `docs/MONETISATION.md` |
| 20 | **Quantities read like arithmetic** | Fixed 2026-09-26 | The first screenshots showed `1.5 × 2 large eggs` and `1 × 1 can (142 g drained)`. `ServingMeasure` now reads each serving into a count, unit and noun (derived from the stored text, so no schema change) and multiplies that: `3 large eggs`, `1¼ cans (178 g drained)`, `1.2 kg raw`. Every catalogue serving must parse and render back unchanged, or `ServingMeasureTests` fails. Settings' `1783 kcal` is grouped now too. Open: a grocery line can still ask for `1¼ cans`, which nobody can buy; rounding countables up in the trolley is a product decision |
| 22 | **Totals can miss a cent against the lines above them** | Medium, both platforms | Found 2026-10-02 in the first Android emulator screenshots, which are also the Play screenshots. The demo breakfast lists $0.22, $0.57 and $0.27 under a $1.05 header (the lines add to $1.06), and the grocery Produce section reads $0.91 over $0.65 and $0.27. Each line is rounded for display, while totals sum the unrounded costs. In a budgeting app a column that does not add up reads as a bug. **Fixed 2026-10-03, both platforms.** Rounding in the engine, the first idea, would not have cured it: the app converts at display time, so a rand column rounds again and can still miss a cent. The rule is now enforced where figures are shown, in the shown currency: `CurrencySettings.shown` rounds a line to the currency's smallest unit (one ISO minor-units table, read by both the rounding and the formatter), and every total over lines (meal headers, the day's spend and what is left, grocery sections and summary, swap savings and each step of a swap) is the sum of its lines as shown. The engines and their pinned figures are untouched. Tests on both platforms: the screenshot breakfast, rand, yen and dinar columns, every tenth of a cent checked against exact decimal rounding, 2,000 random columns in five currencies checked in whole smallest units, and a real swap whose steps add up to its title. Verified in CI: `ios.yml` run `37075140978` at `272dba3` built the app and passed 144 tests, SwiftPM and in-app, every new one by name |

## 3. What is verified, and by what

| Layer | Verified how | Confidence |
| --- | --- | --- |
| `Domain/`, `Engine/` | `swift test` on Linux and on `macos-15`, 144 tests (2026-10-03), figures worked by hand against the frozen `FoodCatalog.reference` prices | High |
| `Persistence/`, `ViewModels/`, `Views/`, `App/` | `xcodebuild build` and `test` green on `macos-15` in CI (`5647a2c`), tests hosted in the app | Compiles and launches |
| App behaviour | `screenshots.yml`: launches with demo data, stays alive on all four tabs | Launch only; nothing tapped yet |

The distinction matters. "It launches" means SwiftData opens its store and four
screens render. It does not mean the food picker scrolls, that a swap applies,
or that onboarding can be finished.

## 4. Recommended order

1. Done 2026-09-26: `ios.yml` is green. The first compile of the new screens
   found two errors (a misplaced `@ViewBuilder`, a missing currency list), both
   fixed.
2. Done 2026-09-26: `screenshots.yml` is green. Getting there fixed two bugs in
   the workflow itself, none in the app: relative log paths that stopped the
   simulator spawning it, and a `grep -q` under `pipefail` that reported a live
   app as dead.
3. Tap through onboarding, a swap and the food picker in Appetize, using the
   `appetize-build` artifact from the same run. Free, no Apple account.
4. Enrol, then run the `testflight` workflow in `codemagic.yaml` and put the app
   on a phone. Fix what breaks on device.
5. Review the disclaimer text and replace NEDIC with a local equivalent.
6. Submit. Keep the App Store submission manual until the app has a week of real
   use behind it, which is what `codemagic.yaml` is already configured to do.

## 5. Costs

| Item | Cost |
| --- | --- |
| Apple Developer Program | $99/yr |
| GitHub Actions on a public repo | $0 |
| Codemagic (signing and TestFlight) | 500 min/mo free, then about $0.095/min |
| GitHub Pages for the two required URLs | $0 |
| A month of a remote Mac, optional, for UI polish | about $30 |

## 6. Open questions

1. App Store or web first? The engines are plain Swift and would port. Without an
   Apple device, iOS is the one platform where the UI cannot be seen.
2. Which market first: US prices and US foods, or South African staples and rand
   pricing? The currency mechanism now supports either; the catalogue holds one.
3. Pricing and monetisation: **resolved on 2026-09-20**, free at launch with one
   StoreKit 2 subscription planned for a later build. See `docs/MONETISATION.md`.
