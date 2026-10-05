# App Store Connect: copy, answers and review notes

Everything App Store Connect asks for, drafted so the submission is data entry
rather than writing. Character counts are respected where Apple enforces them.

## Identity

| Field | Value |
| --- | --- |
| App name (30) | `MacroDime: Budget Meal Planner` (30) |
| Subtitle (30) | `Macros that fit your budget` (28) |
| Bundle id | `com.lungelo.macrodime` |
| Primary category | Health and Fitness |
| Secondary category | Food and Drink |
| Price | Free, with one auto-renewable subscription, MacroDime Pro (below; why: `docs/MONETISATION.md`) |
| Copyright | `2026 <legal name>` |

## Promotional text (170)

```
Dial in your macros without blowing your food budget. MacroDime plans the day,
prices every meal, then shows you a cheaper version of the same meal with the
macros held inside 10%.
```

## Description

```
MacroDime joins two things that are usually decided separately: what your body
needs, and what you can afford.

Your targets come from published science. Basal metabolic rate by Mifflin-St
Jeor, standard activity multipliers, a 20% deficit for fat loss or an 8% surplus
for lean gaining, protein held at 2.0 g per kg while cutting, and fat no lower
than 20% of calories. Every override the engine makes to protect those rules is
reported rather than hidden.

Your plan is built from a curated catalogue of 57 ingredients, and every meal
shows its cost next to its macros. Where the US government publishes an
average retail price (the Bureau of Labor Statistics, and USDA fruit and
vegetable prices), that is the price you see. The rest are estimates, and
Settings says how many of each there are. Prices are built into the app, so
checking one never sends anything anywhere.

THE LOW-COST SWAP

Pick any meal and MacroDime finds a cheaper version of it that keeps calories,
protein, carbohydrate and fat within 10% of where they were. A straight
substitution cannot hold four macros at once, so the engine also re-portions the
fat and carbs already in the meal to close the gap. Swapping fresh salmon for
canned tuna in a salmon dinner costs 55% less and lands within 5% on every macro.

EAT WHAT YOU ACTUALLY EAT

Tell the app your pattern (omnivore, pescatarian, vegetarian, vegan), anything to
avoid, how many meals a day, and how much cooking you will really do. Those
answers remove ingredients from the catalogue before any suggestion is made, so
the plan never proposes something you will not eat.

WHAT IT TELLS YOU IT CANNOT DO

MacroDime audits its own plan and names the gaps: protein short, budget overrun,
one protein source all day, ingredients that take longer than you said you would
cook, or a target your budget cannot fund at all. It also states plainly that
fibre, sodium, micronutrients and the meals you eat out are not tracked.

WHAT IT DOES NOT DO

No account. No ads. No analytics. No network connection. Everything you enter
stays on your device, and Delete All My Data removes all of it.

MACRODIME PRO

Your targets, the health and safety information and your measurements are
free. MacroDime Pro adds the meal planner, the low-cost swap, the grocery list
and progress photos, yearly with a 14-day free trial for new subscribers, or
monthly. Payment is charged to your Apple Account, and the subscription renews
automatically unless you cancel at least 24 hours before the end of the
period, in Settings under your Apple Account.
Terms of Use: https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
Privacy policy: https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html

MacroDime estimates energy needs from population averages. Your real metabolic
rate can differ by 10% or more. It is not a medical device, it does not diagnose
or treat anything, and it is for adults aged 18 and over.
```

## Keywords (100)

```
macro,budget,meal plan,protein,calorie,grocery,BMI,fat loss,cheap,high protein,nutrition
```

## URLs

| Field | Value |
| --- | --- |
| Support URL | `https://lungeloncanana324-blip.github.io/macrodime/support.html` |
| Privacy policy URL | `https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html` |
| Marketing URL | optional; leave blank or point at the repo README |

Both URLs are live since 2026-10-03: GitHub Pages serves the `docs/` folder from
`master`, and `docs/_config.yml` lists what the site publishes.

## Age rating questionnaire

| Question | Answer | Why |
| --- | --- | --- |
| Medical or treatment information | Yes | The app prescribes a calorie target. Answering no would be dishonest |
| Health or wellness topics | Yes | The whole app |
| Everything else | No | No violence, no user-generated content, no gambling, no unrestricted web access |

Expected result: 12+. The app refuses a calorie target for anyone under 18, which
is the substantive protection behind the rating.

## App Privacy answers

| Question | Answer |
| --- | --- |
| Do you or your partners collect data from this app? | No |
| Tracking | No |
| Data linked to the user | None |
| Data not linked to the user | None |

This matches `MacroDime/Resources/PrivacyInfo.xcprivacy`, which declares no
collected data types and no tracking domains. The app has no networking code of
its own. MacroDime Pro is bought through StoreKit: Apple takes the payment, and
the app only asks StoreKit on the device which subscription is active, so no
purchase data is collected by the developer.

## In-app purchase: MacroDime Pro

Built in the app (`MacroDime/Commerce/SubscriptionStore.swift`), waiting for
these App Store Connect steps, in order:

1. **Paid Applications Agreement**, with banking and tax (a W-8BEN for a South
   African individual). Nothing can be sold or sandbox-tested until it is active.
2. **Subscription group** `MacroDime Pro`, with two auto-renewable
   subscriptions whose product ids must match the code exactly:

   | Product id | Duration | Price to start with |
   | --- | --- | --- |
   | `com.lungelo.macrodime.pro.annual` | 1 year | $29.99 |
   | `com.lungelo.macrodime.pro.monthly` | 1 month | $5.99 |

3. **Introductory offer** on the annual product only: Free trial, 2 weeks, for
   new subscribers. The app shows the trial only when StoreKit says the person
   is eligible, and sells a yearly product that renews other than yearly, or an
   intro offer that is not a plain free trial, as nothing at all.
4. **Review screenshot**: the paywall. Display names: "MacroDime Pro, yearly"
   and "MacroDime Pro, monthly".
5. **Sandbox tester** (Users and Access, Sandbox), then test from TestFlight.

The paywall carries what guideline 3.1.2 asks: the title, length and price,
the price after the trial, how to cancel, Restore, and working links to the
Terms of Use (Apple's standard EULA) and the privacy policy. Why the trial is
14 days and only on the yearly plan: `docs/MONETISATION.md`.

## App Review notes

```
MacroDime has no account and no sign-in. Every feature works offline; the app
makes no network requests of its own. Subscriptions go through StoreKit.

MacroDime Pro is an auto-renewable subscription, yearly (with a 14-day free
trial for new subscribers) or monthly. Free without it: onboarding, calorie and
protein targets, the health and safety information, measurements, and
deleting all data. Pro adds the meal planner, swaps, the grocery list and
progress photos. The paywall appears after onboarding, can be dismissed with
"Not now", and opens again from the Plan or Groceries tab or Settings, where
Restore purchases also is. Please use a sandbox account to start the trial.

To see it populated rather than empty, start the trial, then add meals from the
Plan tab using the catalogue picker, or run a low-cost swap on a meal you have
built.

Health and safety: onboarding requires acknowledging a health disclaimer before
the summary step can be completed, and the same text is available at Settings,
Health and Safety. The app refuses to calculate targets below 1,200 kcal for a
female user and 1,500 kcal for a male user, and refuses any input under 18.
```

## Screenshot captions (in the order `screenshots.yml` captures them)

| File | Caption |
| --- | --- |
| `today.png` | `Your macros and your money, on one screen` |
| `plan.png` | `Every meal priced, with a cheaper version one tap away` |
| `groceries.png` | `A shopping list built from the week you planned` |
| `settings.png` | `Change what you eat, or what you spend, any time` |

Apple needs 6.9 inch and 6.7 inch iPhone sets; 6.5 inch is optional and the app
excludes iPads, so no iPad screenshots are required.
