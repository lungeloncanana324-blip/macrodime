# App Store Connect: copy, answers and review notes

Everything App Store Connect asks for, drafted so the submission is data entry
rather than writing. Character counts are respected where Apple enforces them.

## Identity

| Field | Value |
| --- | --- |
| App name (30) | `MacroDime: Budget Meal Planner` (29) |
| Subtitle (30) | `Macros that fit your budget` (28) |
| Bundle id | `com.lungelo.macrodime` |
| Primary category | Health and Fitness |
| Secondary category | Food and Drink |
| Price | Free (no in-app purchases at launch; see `docs/MONETISATION.md`) |
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

Your plan is built from a curated catalogue of 57 ingredients priced at US
supermarket averages, and every meal shows its cost next to its macros.

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

Both URLs come from the `docs/` folder, published through GitHub Pages. The
three-step setup is written at the top of `docs/index.md`.

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
collected data types and no tracking domains. The app has no networking code.

## App Review notes

```
MacroDime has no account and no sign-in. Every feature works offline; the app
makes no network requests at all.

To see it populated rather than empty, add meals from the Plan tab using the
catalogue picker, or run a low-cost swap on a meal you have built.

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
