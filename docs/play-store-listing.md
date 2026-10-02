
# Google Play: what to enter, what to answer, and in what order

Everything Play Console asks for, drafted so the submission is data entry rather
than writing. The Android app is in `android/`; how it is built and signed is in
`android/README.md`. Character counts were checked with a script, not by eye.

## The one requirement that sets the launch date

New **personal** developer accounts (created after 13 November 2023) cannot
publish to production until they have run a **closed test with at least 12
testers who stayed opted in for 14 days in a row**. Google then reviews an
application for production access, which takes up to about a week. A tester who
opts out and back in restarts their own 14 days.

So the earliest public launch is roughly **three weeks after the first closed
build is live**, and the 12 testers are the critical path, not the code. Start
recruiting them before anything else. Internal testing (below) does not count
toward the 12.

Confirm the rule on your own account before planning around it: Play Console,
Dashboard, the "Apply for production" card states the requirement that applies
to you. Organisation accounts are exempt.

## Order of work

1. **Upload key.** Generate it once and back it up (commands in
   `android/README.md`). Play App Signing holds the real signing key; this key
   only signs uploads, and Google can reset it if it is lost.
2. **Create the app** in Play Console: name below, default language English
   (United States), App, Free.
3. **Internal testing** first: upload the first `.aab`, add yourself, install it
   from the Play link on a real phone. No review, available in minutes. This is
   the first time the app runs on real hardware; fix what breaks here.
4. **App content** section: every declaration below. Play will not let a
   release go to review until each one is answered.
5. **Store listing**: text, graphics, screenshots below.
6. **Closed testing**: create a track, add the 12+ testers (an email list or a
   Google Group), send them the opt-in link. Start the 14-day clock.
7. **Apply for production access** once 12 testers have 14 continuous days.
8. **Production**, with a staged rollout (start at 20%).

## Store listing

| Field | Limit | Value |
| --- | --- | --- |
| App name | 30 | `MacroDime: Budget Meal Planner` (30) |
| Short description | 80 | `Macro targets from real science, and a meal plan priced against your budget.` (76) |
| Category | | Health & Fitness |
| Tags | up to 5 | Meal planner, Nutrition, Calorie counter, Diet, Budgeting (choose from Play's list; the closest available) |
| Email address | | Required and public. Use a support address you are happy to publish |
| Website | | `https://lungeloncanana324-blip.github.io/macrodime/` |
| Privacy policy | | `https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html` |

### Full description (4,000)

Play's metadata policy rejects all-caps headings, superlatives and testimonials,
so the iOS description's section headings are in sentence case here and nothing
claims to be "best".

```
MacroDime joins two things that are usually decided separately: what your body needs, and what you can afford.

Your targets come from published science. Basal metabolic rate by Mifflin-St Jeor, standard activity multipliers, a 20% deficit for fat loss or an 8% surplus for lean gaining, protein held at 2.0 g per kg while cutting, and fat no lower than 20% of calories. Every override the engine makes to protect those rules is reported rather than hidden.

Your plan is built from a curated catalogue of 57 ingredients, and every meal shows its cost next to its macros. Where the US government publishes an average retail price (the Bureau of Labor Statistics, and USDA fruit and vegetable prices), that is the price you see. The rest are estimates, and Settings says how many of each there are. Prices are built into the app, so checking one never sends anything anywhere.

The low-cost swap

Pick any meal and MacroDime finds a cheaper version of it that keeps calories, protein, carbohydrate and fat within 10% of where they were. A straight substitution cannot hold four macros at once, so the engine also re-portions the fat and carbs already in the meal to close the gap. Swapping fresh salmon for canned tuna in a salmon dinner costs 55% less and lands within 5% on every macro.

Eat what you actually eat

Tell the app your pattern (omnivore, pescatarian, vegetarian, vegan), anything to avoid, how many meals a day, and how much cooking you will really do. Those answers remove ingredients from the catalogue before any suggestion is made, so the plan never proposes something you will not eat.

What it tells you it cannot do

MacroDime audits its own plan and names the gaps: protein short, budget overrun, one protein source all day, ingredients that take longer than you said you would cook, or a target your budget cannot fund at all. It also states plainly that fibre, sodium, micronutrients and the meals you eat out are not tracked.

What it does not do

No account. No ads. No analytics. No internet permission at all. Everything you enter stays on your phone, and Delete All My Data removes all of it.

MacroDime estimates energy needs from population averages. Your real metabolic rate can differ by 10% or more. It is not a medical device, it does not diagnose or treat anything, and it is for adults aged 18 and over. Prices are US supermarket averages; you can show them in your own currency at an exchange rate you enter.
```

### Graphics

| Asset | Requirement | Status |
| --- | --- | --- |
| App icon | 512 x 512 PNG | `android/play-store/icon-512.png`, generated from the iOS icon by `android/scripts/make_icons.py` |
| Feature graphic | 1024 x 500 PNG or JPEG, required | `android/play-store/feature-graphic.png` |
| Phone screenshots | 2 to 8, each side 320 to 3,840 px, no more than 2:1 | The `android.yml` workflow captures all four tabs from an emulator with demo data and uploads them as the `play-screenshots` artifact |

Screenshot captions, in tab order, if you add text frames:

| Tab | Caption |
| --- | --- |
| Today | `Your macros and your money, on one screen` |
| Plan | `Every meal priced, with a cheaper version one tap away` |
| Groceries | `A shopping list built from the week you planned` |
| Settings | `Change what you eat, or what you spend, any time` |

## App content: every declaration

| Section | Answer | Why |
| --- | --- | --- |
| Privacy policy | The URL above | Required for every app, including one that collects nothing |
| Ads | No, my app does not contain ads | |
| App access | All functionality is available without special access | No account, no login |
| Content rating | Complete the IARC questionnaire as below | |
| Target audience | **18 and over only** | The app prescribes calorie deficits and refuses any age under 18. Choosing only 18+ also keeps it outside the Families policy |
| News app | No | |
| COVID-19 contact tracing and status | Not a contact tracing or status app | |
| Data safety | As below | |
| Government apps | No | |
| Financial features | My app does not provide any financial features | A food budget is planning, not a financial service |
| Health apps | Health and fitness: **Nutrition and Weight Management** only | It prescribes calorie and macro targets. It does not track activity, so Activity and Fitness is not ticked. It is not a medical device |
| Advertising ID | No | No ads SDK, and the build fails if `AD_ID` or any other permission is merged in |

### Content rating questionnaire (IARC)

Category: **All other app types**. Answer **No** to violence, sexuality, language,
controlled substances, crude humour, gambling, user-to-user interaction, sharing
location, and digital purchases. Expected result: Everyone / PEGI 3 / USK 0.

The rating describes content; the age limit is enforced separately, by the
target audience answer above and by the app itself.

### Data safety form

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not asked once the answer above is No |
| Do you provide a way for users to request that their data is deleted? | Not asked; the app has Settings, Delete All My Data anyway |

Why "No" is true, not just claimed:

- The app requests **no permissions**. Without `INTERNET` it cannot open a
  network connection, and `verifyReleasePermissions` fails the build if any
  library ever merges a permission in.
- Prices are compiled into the app; nothing is fetched.
- Progress photos come through the system photo picker, which needs no storage
  permission, and are copied into the app's private storage.
- **Cloud backup is switched off** (`android:allowBackup="false"` and
  `res/xml/data_extraction_rules.xml`). Android's backup sends app data to the
  user's Google Drive, and Google's Data safety guidance does not say whether
  that counts as collection. Excluding it makes the answer true by
  construction. Device-to-device transfer to a new phone is still allowed: it
  copies directly between the two phones.
- No analytics, crash reporting or ads SDK is included. Google Play's own
  Android vitals are collected by Google under its policy, not by the app.

If any of this changes (a store API for prices, a subscription SDK, crash
reporting), the form must be updated before that build is released.

## Release notes for the first build

```
First release. Macro targets from your body and goal, a daily meal plan priced against the allowance you set, low-cost swaps that keep your macros within 10%, and a grocery list built from the week you planned. Everything stays on your phone.
```

## Pricing

Free, with no in-app purchases, matching the iOS decision in
`docs/MONETISATION.md`. A later subscription on Android would use Google Play
Billing, which is required for digital features sold in a Play app, and would
need the Data safety form and the privacy policy revisited.
