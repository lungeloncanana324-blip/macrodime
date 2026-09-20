# Shipping MacroDime: what is done, what is missing

Written 2026-09-20, against `e515cbe` plus the vegetable, currency, dietary and
onboarding work in this branch.

This is a gap report, not a plan with dates. It is ordered by what actually
blocks a submission, so the top of the list is the work that has to happen before
App Store Connect will accept a build at all.

## 1. Blockers: no build can be submitted without these

| # | Gap | What it needs | Who | Cost |
| --- | --- | --- | --- | --- |
| 1 | **Apple Developer Program** | Enrolment with legal name, tax and banking details | Lungelo | $99/yr, 1 to 3 days |
| 2 | **Privacy policy URL** | Apple requires a live URL. Text is written in `docs/privacy-policy.md`; host it (GitHub Pages is free) | Lungelo | 30 min |
| 3 | **Support URL** | A page with a contact route. GitHub Pages or a mailto page is enough | Lungelo | 15 min |
| 4 | **Screenshots** | Automated by `.github/workflows/screenshots.yml`, which shoots 6.9 inch and 6.7 inch sets. Not yet run, because it needs the app to launch | CI, unverified | 20 min of CI |
| 5 | **A TestFlight build that runs** | `codemagic.yaml` has the signed workflow ready. This is the first time the app will ever execute, so budget for runtime fixes | Lungelo and CI | half a day |
| 6 | **App Store Connect record** | Bundle id `com.lungelo.macrodime`, name, category (Health and Fitness), age rating questionnaire | Lungelo | 45 min |
| 7 | **Age rating** | Answer yes to medical and treatment information. That is the honest answer for an app that prescribes a calorie deficit, and it lands at 12+ | Lungelo | 5 min |
| 8 | **App Privacy answers** | "Data not collected" for every category, matching `PrivacyInfo.xcprivacy`. No account, no analytics, no network calls at all | Lungelo | 20 min |
| 9 | **Export compliance** | Declared in `project.yml` (`ITSAppUsesNonExemptEncryption = NO`), so there is no per-build question | done | 0 |

## 2. Product gaps a reviewer, or a user in week two, will hit

| # | Gap | Severity | Note |
| --- | --- | --- | --- |
| 10 | **The SwiftUI half has never run** | High | The engines have 101 tests and are trustworthy. Onboarding, the seeder, the first SwiftData save and every screen are unexercised. This is the largest unknown in the project |
| 11 | **Disclaimer text is unreviewed** | High | `HealthDisclaimer` is general knowledge written by a developer, not checked by a clinician or a lawyer. The support link is NEDIC, which is Canadian; SADAG is the South African equivalent and a better fit |
| 12 | **Currency localisation is manual** | Medium, bug fixed | The reported bug (a device-locale symbol on a USD amount) is fixed: the catalogue currency is explicit, and a user can enter their own currency and rate in Settings. There is deliberately no bundled rate table, because an invented rate is worse than no conversion. Real localisation means localising the price table itself |
| 13 | **Fibre, sodium and micronutrients are not tracked** | Medium | Disclosed in the plan audit rather than hidden. Fibre would be the most valuable next engine feature, and it needs catalogue data that is not authored yet |
| 14 | **No data export** | Medium | Delete-all exists; export does not. Apple does not require it, but it is the honest counterpart, and it is the first thing a user asks for when changing phones |
| 15 | **iPhone only** | Low | `TARGETED_DEVICE_FAMILY: "1"`. iPad support means layout work that cannot be judged without a device |
| 16 | **English only, US-centric food** | Low | 57 US supermarket items. A South African launch needs local staples and prices: a content project, not a code change |
| 17 | **No accessibility audit** | Low | Labels exist on rings, meters and cards. Largest Dynamic Type, and VoiceOver on the planner, have never been checked by a human |
| 18 | **No undo for a swap** | Low | A swap applies immediately. The engine is conservative (10% macro tolerance) but a mistake has no one-tap reversal |

## 3. What is verified, and by what

| Layer | Verified how | Confidence |
| --- | --- | --- |
| `Domain/`, `Engine/` | `swift test` on Linux, 101 tests, figures worked by hand | High |
| `Persistence/`, `ViewModels/`, `Views/`, `App/` | `xcodebuild build` and `test` on `macos-15` in CI, plus `swiftc -parse` over every file | Compiles, never launched |
| App behaviour | Nothing | None |

The distinction matters. "It compiles" means the types line up. It does not mean
the food picker scrolls, that SwiftData opens its store, or that onboarding can
be finished.

## 4. Recommended order

1. Push this branch and let `.github/workflows/ios.yml` run. Fix what it reports.
   Free, and it is the only compiler available.
2. Run `.github/workflows/screenshots.yml`. Its output is both the store
   screenshots and proof that the app launches and renders four screens without
   crashing. The cheapest end-to-end test there is.
3. Enrol, then run the `testflight` workflow in `codemagic.yaml` and put the app
   on a phone. Fix what breaks on device.
4. Review the disclaimer text and replace NEDIC with a local equivalent.
5. Submit. Keep the App Store submission manual until the app has a week of real
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
3. Free, paid, or free with a paid tier? It changes the App Store Connect setup,
   and Apple's rules on health apps and subscriptions differ.
