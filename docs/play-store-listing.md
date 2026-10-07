
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
| Email address | | `macrodime.app@gmail.com` (created 2026-10-05; also on the support page and in the privacy policy) |
| Website | | `https://lungeloncanana324-blip.github.io/macrodime/` |
| Privacy policy | | `https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html` |

### Full description (4,000)

Play's metadata policy rejects all-caps headings, superlatives and testimonials,
so the iOS description's section headings are in sentence case here and nothing
claims to be "best".

```
MacroDime joins two things that are usually decided separately: what your body needs, and what you can afford.

Your targets come from published science. Basal metabolic rate by Mifflin-St Jeor, standard activity multipliers, a 20% deficit for fat loss or an 8% surplus for lean gaining, protein held at 2.0 g per kg while cutting, and fat no lower than 20% of calories. Every override the engine makes to protect those rules is reported rather than hidden.

Your week is planned for you. When you finish setting up, MacroDime builds seven days of simple meals for your calories, protein, food budget and diet, with a shopping list to match. Change anything you like; any day you clear can be planned again with one tap.

Your plan is built from a curated catalogue of 57 ingredients, and every meal shows its cost next to its macros. Where a US government agency publishes an average retail price, that is the price you see. The rest are estimates, and Settings says how many of each there are.

Sources, and a disclaimer

MacroDime is an independent app. It does not represent any government or government agency, and it is not affiliated with or endorsed by any organisation listed here. It uses figures these organisations publish for anyone to use, and each link opens the original.

Food prices: Average Price Data, US Bureau of Labor Statistics
https://data.bls.gov/toppicks?survey=ap

Fruit, vegetable and bean prices: USDA Economic Research Service
https://www.ers.usda.gov/data-products/fruit-and-vegetable-prices

Price adjustment: Consumer Price Index, US Bureau of Labor Statistics
https://data.bls.gov/timeseries/CUUR0000SAF113

Nutrition for whole foods: FoodData Central, US Department of Agriculture
https://fdc.nal.usda.gov/

The app shows the same links on its Sources screen, in Settings and in the menu of the subscription screen.

The low-cost swap

Pick any meal and MacroDime finds a cheaper version of it that keeps calories, protein, carbohydrate and fat within 10% of where they were. Swaps stay with food you would actually put on that plate: a hot main for a hot main, beans for beans, never tuna in your yogurt. A straight substitution cannot hold four macros at once, so the engine also re-portions the fat and carbs already in the meal to close the gap.

Eat what you actually eat

Tell the app your pattern (omnivore, pescatarian, vegetarian, vegan), anything to avoid, how many meals a day, and how much cooking you will really do. Those answers remove ingredients from the catalogue before any suggestion is made, so the plan never proposes something you will not eat.

What it tells you it cannot do

MacroDime audits its own plan and names the gaps: protein short, budget overrun, one protein source all day, ingredients that take longer than you said you would cook, or a target your budget cannot fund at all. It also states plainly that fibre, sodium, micronutrients and the meals you eat out are not tracked.

What it does not do

No account. No ads. No analytics. No internet permission at all. Everything you enter stays on your phone, and Delete All My Data removes all of it.

Subscription

A subscription is required to use MacroDime. It starts with a 14-day free trial of MacroDime Pro, which needs a payment method on your Google account. Nothing is charged on the day you start, the app reminds you two days before the trial ends, and then MacroDime Pro renews every year at the price shown in Google Play until you cancel. Cancel in Google Play before the trial ends and you are not charged.

MacroDime estimates energy needs from population averages. Your real metabolic rate can differ by 10% or more. It is not a medical device, it does not diagnose or treat anything, and it is for adults aged 18 and over. Prices are US supermarket averages; you can show them in your own currency at an exchange rate you enter.
```

### Graphics

| Asset | Requirement | Status |
| --- | --- | --- |
| App icon | 512 x 512 PNG | `android/play-store/icon-512.png`, drawn by `android/scripts/make_icons.py` with the launcher icon, the themed icon, the notification icon, the iOS icon and the website's icons, all from one set of shapes |
| Feature graphic | 1024 x 500 PNG or JPEG, required | `android/play-store/feature-graphic.png`, from `feature-graphic.html`: the app's first screen at banner width (ink, the food photograph, the icon, "Hit your macros on a real budget.") |
| Phone screenshots | 2 to 8, each side 320 to 3,840 px, no more than 2:1 | `android/play-screenshots/store/`, 1080 x 1920: the real screens from `ScreenshotTest` with the demo week (a placeholder name, never a real person's), framed with a caption each by `android/scripts/make_store_screenshots.py` |

The captions, in upload order:

| Screen | Caption |
| --- | --- |
| Today | `Your macros and your money, on one screen` |
| Plan | `Every meal priced to your daily budget` |
| Swap review | `Cheaper swaps that keep your macros` |
| Groceries | `A shopping list built from your week` |
| Today, dark | `Easy to read day or night` |

## App content: every declaration

| Section | Answer | Why |
| --- | --- | --- |
| Privacy policy | The URL above | Required for every app, including one that collects nothing |
| Ads | No, my app does not contain ads | |
| App access (now called Sign-in details) | **All or some functionality is restricted**, with the instructions below | No account or login, but everything after setup is behind the paywall, and Google requires reviewers to be given a way past a subscription paywall |
| Content rating | Complete the IARC questionnaire as below | |
| Target audience | **18 and over only** | The app prescribes calorie deficits and refuses any age under 18. Choosing only 18+ also keeps it outside the Families policy |
| News app | No | |
| COVID-19 contact tracing and status | Not a contact tracing or status app | |

### App access instructions

Google's rule: "If your app does not require sign-in details but you have
functionalities or content behind a subscription paywall, please provide
additional instructions or access details that will allow us to fully and
freely access and review the app behind the paywall." Add one entry with no
username or password, and paste this into the instructions (492 characters):

```
MacroDime has no account or login. Everything after setup is behind a subscription paywall. To review: 1. Complete setup with any values (for example weight 82 kg) and tick the acknowledgement. 2. On the paywall tap Start my 14-day free trial. Nothing is charged during the trial. 3. To avoid any charge, cancel in Play Store, Payments and subscriptions, Subscriptions before the trial ends. Health information, the privacy policy and Delete all my data are in the paywall's menu (top right).
```

The trial is free, so this gives reviewers full access without paying. If a
review is still rejected for App access, the fallback is a reviewer code that
unlocks Pro on one phone; that needs a new build, so wait for the rejection
before adding one.
| Data safety | As below | |
| Government apps | No | MacroDime has no government affiliation. Showing government figures is a separate matter, covered by the Misleading Claims policy: see Government information below |
| Financial features | My app does not provide any financial features | A food budget is planning, not a financial service |
| Health apps | Health and fitness: **Nutrition and Weight Management** only | It prescribes calorie and macro targets. It does not track activity, so Activity and Fitness is not ticked. It is not a medical device |
| Advertising ID | No | No ads SDK, and the build fails if `AD_ID` or any other permission is merged in |

### Government information (Misleading Claims policy)

Play rejected the first review on **6 October 2026**: "Missing Source Link for
Government Information". Any app that shows government information, affiliated
or not, must link to the original source and say plainly that it does not
represent a government. MacroDime shows BLS and USDA prices (and USDA nutrition
values) but named the agencies without linking them, and the description had
no disclaimer. Do not appeal: an appeal is for apps with written government
authorisation. The fix is an update:

1. **Description.** The full description above now has a "Sources, and a
   disclaimer" section right after the prices paragraph: the statement that
   MacroDime does not represent any government, then the four .gov sources
   with their URLs. Paste the whole description into Store listing again.
2. **The app.** Version 1.3.1 (version code 8) adds a Sources screen: the same
   statement, then every source with its address and a link that opens the
   original. It opens from Settings (under Prices), from Health & Safety, and
   from the paywall's menu, so a reviewer reaches it without starting a trial.
   The Settings price footer and the health statement also say MacroDime does
   not represent any government.
3. **Send the update for review** with the new bundle on the track that was
   rejected. Publishing overview lists the changes; Policy status shows the
   issue until the review passes.

`DataSourcesTest` (android/core) fails the build if the description loses a
.gov link or the statement, if the statement drifts from the app's, or if the
description passes Play's 4,000 characters (counting each line break twice,
in case the console sends CRLF).

**Second rejection, 7 October 2026: "Broken or Inaccessible Source Link"**, in
the full description. The links were there this time, and every one opened in
a desktop browser, but the two BLS links were on www.bls.gov, which sits behind
an Akamai bot filter: curl, Python and headless Edge all get a 403 "Access
Denied" page. Play checks source links with a machine, so it saw the block
page. The two USDA links answer anyone and were not the problem. Fixed in 1.3.2
(version code 9) by moving both BLS links to data.bls.gov, the same agency's
data site, which serves every client:

- Food prices: `https://data.bls.gov/toppicks?survey=ap`, the Average Price
  Data series list (bread, eggs, milk and the rest, by series id).
- Price adjustment: `https://data.bls.gov/timeseries/CUUR0000SAF113`, the one
  CPI series the app uses (fruits and vegetables, US city average), rather than
  the CPI home page.

To do: paste the full description above into Store listing again (only the two
BLS addresses changed), upload version code 9 to the rejected track, and send
for review. The app's Sources screen had the same two links, which is why it
needs the new bundle as well as the new description.

Two guards now: `DataSourcesTest` fails if a link goes back to www.bls.gov or
download.bls.gov, and `scripts/check_source_links.py` fetches every link in
`DataSources.kt` and in the description as a bot would (links.yml runs it from
GitHub's servers on every change and weekly). Run it before pasting any
description that adds a link. It passes the new links and fails the rejected
description on exactly the two BLS links.

### Content rating questionnaire (IARC)

Category: **All other app types**. Answer **No** to violence, sexuality, language,
controlled substances, crude humour, gambling, user-to-user interaction and
sharing location. Answer **Yes** to digital purchases: since version code 2 the
app sells MacroDime Pro. That adds the "In-app purchases" notice to the listing
and does not change the age rating. Expected result: Everyone / PEGI 3 / USK 0.

The rating describes content; the age limit is enforced separately, by the
target audience answer above and by the app itself.

### Data safety form

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not asked once the answer above is No |
| Do you provide a way for users to request that their data is deleted? | Not asked; the app has Settings, Delete All My Data anyway |

Why "No" is true, not just claimed:

- The app requests **no permission that reaches the network**. Without
  `INTERNET` it cannot open a connection. Its permissions are
  `com.android.vending.BILLING` (to talk to the Play Store app on the phone
  about MacroDime Pro), `POST_NOTIFICATIONS` (the local trial reminder) and
  `RECEIVE_BOOT_COMPLETED` (to set that reminder again after a restart), and
  `verifyReleasePermissions` fails the build if any library ever merges in
  anything else. It caught one on
  2026-10-03: Play Billing 9 brings Google's Data Transport library, which
  uploads the billing library's own diagnostics and merges in `INTERNET` and
  `ACCESS_NETWORK_STATE`. The app manifest removes both (see the comment in
  `AndroidManifest.xml`): purchases still work because the Play Store app does
  the networking, and Data Transport's refusal is caught on its own executor,
  so its diagnostics are dropped rather than sent. Recheck when Play Billing is
  upgraded.
- **Subscriptions go through Google Play Billing.** Google takes the payment
  under its own terms; the app never sees card or payment details. All it
  receives is whether a subscription is active, which it keeps on the phone and
  sends nowhere. Google's definition: "'Collect' means transmitting data from
  your app off a user's device", and "user data accessed by your app that is
  only processed locally on the user's device and not sent off device does not
  need to be disclosed". This is a judgement on Google's wording, so the
  cautious alternative is stated here too: declaring Financial info, Purchase
  history (collected, not shared, for app functionality) is never wrong, and
  costs only the "No data collected" label. Recheck the form's help text on
  the day it is submitted.
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

Version 1.2.0 (version code 3), the first with the planned week:

```
Your week is now planned for you the moment you finish setting up: simple meals for your calories, protein, budget and diet, with a shopping list to match. A new look built around the food, a 14-day free trial with a reminder before it ends, and cheaper swaps that stay with food you would actually put on the plate.
```

Version 1.3.1 (version code 8), the Misleading Claims fix:

```
New Sources screen: where every price and nutrition figure in MacroDime comes from, with a link to each original. MacroDime is an independent app and does not represent any government.
```

Version 1.3.2 (version code 9), the source link fix:

```
The Bureau of Labor Statistics links on the Sources screen now open the price data itself, on data.bls.gov.
```

## Pricing and MacroDime Pro

The app is **Free** in Play Console (a free app can never become paid, but it
can sell subscriptions), and sells one subscription, MacroDime Pro: a 14-day
free trial that renews into the yearly plan. Since 2026-10-04 there is no free
tier: after onboarding the paywall is the way into the app. Why:
`docs/MONETISATION.md`. Because a subscription is required to use the app,
Play's policy requires the offer to say so: the full description's
Subscription paragraph opens with it, and the paywall says it above the plan
and again at the start of the terms.

### Setting it up in Play Console, in this order

1. **Payments profile.** Setup, Payments profile: link or create one, with
   the bank account Google pays out to. Nothing can be sold until it exists.
2. **Upload a build that contains Play Billing.** Play Console only allows
   subscriptions to be created once an uploaded bundle declares the BILLING
   permission. Version code 1 does not; the next bundle (version code 2 or
   higher, `./gradlew :app:bundleRelease -PversionCode=2`) does. Upload it to
   internal testing.
3. **Create the subscription.** Monetize with Play, Products, Subscriptions,
   Create subscription. The ids must match the app exactly:

   | Field | Value |
   | --- | --- |
   | Product ID | `pro` |
   | Name | MacroDime Pro |
   | Base plan | ID `annual`, auto-renewing, billing period 1 year, $29.99 |

   Set the local prices Play suggests, or round them by hand (South Africa,
   for example). Activate the base plan. Create no monthly base plan: the
   trial renews into the yearly plan. The app still supports one (ID
   `monthly`, 1 month, $5.99, no trial) and shows it as a second choice the
   moment it is active, so it can be added later without a new build.
4. **Add the trial offer** to the `annual` base plan: Add offer, ID
   `free-trial-14-days`, eligibility **New customer acquisition: never had
   this subscription**, one phase: **Free trial, 2 weeks** (or 14 days).
   Activate it. If a monthly plan is ever added, give it no trial: the app
   sells the monthly plan without one, and ignores any offer it could not
   describe honestly.
5. **License testers.** Settings (the account level, not the app), License
   testing: add your Gmail **and every closed tester's**. With the paywall as
   the way in, a tester who is not a license tester must start a real trial
   with a real payment method and remember to cancel it. License testers pay
   nothing with Google's test card, but Google runs their subscriptions on a
   fast clock (verified 2026-10-04 in Google's billing test documentation):
   the free trial lasts 3 minutes, the yearly plan renews every 30 minutes,
   and after 6 renewals the subscription ends. Google Play's purchase sheet
   shows that test trial as 3 minutes; customers get the 14 days set here, and
   the app's own screens can only state a trial in days, weeks, months or
   years. So roughly every 3.5 hours a tester meets the paywall again. By then
   they have had the trial, so the button reads "Subscribe for $29.99 a year"
   (or the local price), and the test card is still never charged. Tell
   testers both things before the 14 days start, so they do not read either
   as a bug, or as a real charge.

The app reads every price, the trial's length and the eligibility from Play,
so changing a price or the trial later needs no new build.

### What to check on a phone, from the internal testing track

| Do this | Expect |
| --- | --- |
| Open the app | The photo intro: "Hit your macros on a real budget.", then what has been getting in the way, then the answers to what you picked |
| Finish onboarding | "Your week is ready": your own targets and budget, today's planned meals with their cost, "A subscription is required to use MacroDime." over Yearly with "14 days free", the timeline (today, day 12 reminder, day 14) and "Start my 14-day free trial" over "No payment today". No close button |
| Start the trial | On Android 13 and later, the notification permission first (for the reminder), then Google Play's own purchase sheet showing the trial and the price after it; after confirming, Today opens on the planned week |
| Settings | "Pro, yearly", the date the trial ends, Manage subscription |
| Manage subscription | Opens Google Play's Subscriptions list with MacroDime in it (since version code 5; before that it linked to MacroDime's own page, which Play leaves empty once a subscription has expired, as a license tester's does after about three hours) |
| Cancel in Play, return to the app | Pro stays until the trial ends; Today's reminder (last two days) says you won't be charged |
| Uninstall, reinstall, onboard again | No paywall: the store already knows the account has Pro |
| As a license tester, in Play's subscriptions page switch the payment method to "Test card, always declines" and wait for the next renewal (about 30 minutes) | During the grace period (about 5 minutes) Pro stays and Google Play shows its own payment message over the app; then account hold: "Your subscription is on hold" with "Fix it in Google Play", and no new subscription offered |
