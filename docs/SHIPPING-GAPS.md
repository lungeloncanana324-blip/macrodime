# Shipping MacroDime: what is done, what is missing

Written 2026-09-20, against `e515cbe` plus the vegetable, currency, dietary and
onboarding work in this branch. Updated 2026-09-26 at `79c3a8c`, after the
first compile of that work and the first launch of the app.

This is a gap report, not a plan with dates. It is ordered by what actually
blocks a submission, so the top of the list is the work that has to happen before
App Store Connect will accept a build at all.

## 1. Blockers: no build can be submitted without these

| # | Gap | What it needs | Who | Cost |
| --- | --- | --- | --- | --- |
| 1 | **Apple Developer Program** | Enrolment with legal name, tax and banking details | Lungelo | $99/yr, 1 to 3 days |
| 2 | **Privacy policy URL** | Text is written and now publishable: `docs/privacy-policy.md` renders as a page once Pages is on. Three clicks, written up in `docs/index.md` | Lungelo | 10 min |
| 3 | **Support URL** | `docs/support.md` does the same job, including the questions a reviewer would ask and the refund route | Lungelo | done, host it |
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
| 17 | **No accessibility audit** | Low | Labels exist on rings, meters and cards. Largest Dynamic Type, and VoiceOver on the planner, have never been checked by a human |
| 18 | **No undo for a swap** | Low | A swap applies immediately. The engine is conservative (10% macro tolerance) but a mistake has no one-tap reversal |
| 19 | **No way to charge for it** | Decided, not built | Decided 2026-09-20: launch free with no in-app purchases, and add one StoreKit 2 subscription in a later build. Nothing about payments can be tested until the Paid Applications Agreement is active. The whole plan, including what stays free and the sandbox test list, is in `docs/MONETISATION.md` |
| 20 | **Quantities read like arithmetic** | Fixed 2026-09-26 | The first screenshots showed `1.5 × 2 large eggs` and `1 × 1 can (142 g drained)`. `ServingMeasure` now reads each serving into a count, unit and noun (derived from the stored text, so no schema change) and multiplies that: `3 large eggs`, `1¼ cans (178 g drained)`, `1.2 kg raw`. Every catalogue serving must parse and render back unchanged, or `ServingMeasureTests` fails. Settings' `1783 kcal` is grouped now too. Open: a grocery line can still ask for `1¼ cans`, which nobody can buy; rounding countables up in the trolley is a product decision |

## 3. What is verified, and by what

| Layer | Verified how | Confidence |
| --- | --- | --- |
| `Domain/`, `Engine/` | `swift test` on Linux and on `macos-15`, 113 tests, figures worked by hand | High |
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
