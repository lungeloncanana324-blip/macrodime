# Monetisation: the decision, and the plan for the build that adds it

Decided 2026-09-20.

## The decision

**MacroDime launches free, with no in-app purchases.** One auto-renewable
subscription is added in a later build, implemented with Apple's own StoreKit 2
and no third-party SDK.

Three reasons, in order of weight:

1. **The app has never run.** A paywall in front of an app that has not launched
   means the first paying users are also the first testers, and a broken purchase
   flow is a guaranteed rejection. Prove the free app works on TestFlight first.
2. **It keeps the privacy claim true.** The privacy manifest declares no data
   collected and the app makes no network calls. Apple processes the payment; no
   card data and no purchase data ever reaches this code.
3. **The subscription here is small enough to write directly.** A product fetch,
   a purchase, a transaction listener, an entitlement check and a restore button
   is a few hundred lines. A service that exists to manage that for you has to
   earn its 1%.

## Why not the alternatives

| Option | What it is | Why not now |
| --- | --- | --- |
| **RevenueCat** | A subscription layer that wraps StoreKit (and Google Play, and web billing), keeping entitlements in one place with remote-configurable paywalls. Free to $2,500 monthly tracked revenue, then 1% of what it tracks | Its SDK makes network calls and collects purchase and device data, so `PrivacyInfo.xcprivacy` would have to declare collected data, the App Privacy answers would change from "no data collected", and the privacy policy would have to name a processor. Worth revisiting if you want A/B tested paywalls, or an Android or web build sharing one entitlement |
| **Stripe** | A payment processor | **Not permitted for unlocking app features.** Apple's guideline 3.1.1 requires in-app purchase for that. It is required, not merely allowed, for the opposite case: guideline 3.1.3(e) says physical goods or services consumed outside the app "must use purchase methods other than in-app purchase" |
| **Web checkout from inside the app** | Paying on a website, then unlocking in the app | Region gated. Apple's `External Purchase` entitlements cover the EU, South Korea, Japan, Brazil, Russia and Dutch dating apps. The United States has a separate path opened in 2025 after the Epic ruling; check the current wording of guideline 3.1.1(a) before depending on it. All of these need accounts, a backend and entitlement sync, none of which this app has. That is a different product, not a payment choice |
| **Paid app up front** | A price tier in App Store Connect, zero code | Eliminates trying before buying, which a diet app badly needs, and a health app that costs money up front converts poorly |

## What the second build needs

### 1. App Store Connect, before any code can be tested

Nothing about money works until the **Paid Applications Agreement** is accepted
and banking and tax details are in. For a South African individual, that means a
tax form (W-8BEN) and a bank account Apple can pay into.

Then, for the subscription:

| Thing | Value to use |
| --- | --- |
| Subscription group | `MacroDime Pro` |
| Monthly product | `com.lungelo.macrodime.pro.monthly` |
| Annual product | `com.lungelo.macrodime.pro.annual` |
| Annual price | about ten months of the monthly price |
| Introductory offer | optional, and simplest to put on the annual only |
| Reference name | the same string as the product id, so the two cannot drift |
| Localisations | English (South Africa) and English (United States) |
| Review screenshot | the paywall itself, which is also a good check that it renders |

Set both prices in ZAR for the South African storefront and let Apple convert for
everything else. The first in-app purchase has to be submitted **with a build**;
it will not go live on its own.

### 2. The code

| File | Responsibility |
| --- | --- |
| `Commerce/SubscriptionStore.swift` | `Product.products(for:)` to fetch, `product.purchase()` to buy, `Transaction.updates` to hear about renewals and refunds while running, `Transaction.currentEntitlements` for the gate, `AppStore.sync()` behind Restore, `Product.SubscriptionInfo.status` if the paywall should show renewal state |
| `Commerce/Entitlement.swift` | The persisted answer to "is this user Pro": an expiry date and a **grace window** of a few days, so a subscription that lapses while the phone is offline does not lock a paying user out mid-trip. This is the price of having no server |
| `Views/PaywallView.swift` | Title, duration, price, what it unlocks, Restore, and links to Terms of Use and the privacy policy |
| `App/MacroDimeApp.swift` | Inject the entitlement into the environment beside the currency, and start the transaction listener |

No server, no account. The entitlement belongs to the Apple Account, so a
reinstall or a new phone is covered by `currentEntitlements` plus the Restore
button. Family Sharing stays off unless you decide otherwise.

### 3. What stays free

Apple rejects an app that is a stub without its purchase, and a nutrition app
that hides its safety information behind a paywall deserves to be rejected. The
line:

| Free, forever | Subscription |
| --- | --- |
| Onboarding, targets, body science | The meal planner |
| The health disclaimer and safety screens | The low-cost swap engine |
| Today's macro and budget rings | The grocery list |
| The gap audit | Progress history and photos |
| Delete all my data | |

### 4. Compliance checklist

- In-app purchase for the digital unlock (3.1.1), because there is no other legal
  route for this kind of feature.
- A **restore mechanism is mandatory** (3.1.1). `AppStore.sync()` plus a visible
  Restore button.
- Subscription disclosure: title, length, price, and what the subscription
  provides, visible before purchase.
- Links to Terms of Use and the privacy policy, in the app metadata and on the
  paywall. Apple's standard EULA link is acceptable for Terms.
- A route to manage or cancel, and cancel instructions in plain words.
- No countdown timers, no fake discounts, no "offer ends tonight".

### 5. Testing plan for this hardware

There is no Mac, so the `.storekit` configuration file and Xcode's local StoreKit
testing are unavailable. Everything happens in the sandbox:

1. Create sandbox testers in App Store Connect (Users and Access, Sandbox,
   Testers).
2. Run the build through TestFlight, signed in with a sandbox account on the
   iPhone.
3. Exercise, at minimum: purchase, restore after deleting and reinstalling,
   restore on a second device, cancel, upgrade and downgrade inside the group, a
   declined payment, a refund request, and the offline grace window (buy, then
   turn on airplane mode and relaunch).
4. Sandbox renewals run on a compressed clock, so a month or a year of
   subscription lifecycle takes minutes rather than months. Check Apple's
   "Testing in-app purchases with sandbox" page for the current table rather than
   trusting a number written here.

### 6. Commission

Enrol in the **App Store Small Business Program** for a 15% commission instead of
30% while proceeds stay under $1M a year. Subscriptions drop to 15% after a
subscriber's first year in any case. Apple handles VAT and regional payment
methods, and pays out to the South African bank account.

## What would change this decision

- Wanting paywall experiments, or an Android or web build sharing one
  entitlement, makes RevenueCat's 1% look cheap.
- If the US external purchase path survives its appeals, a web checkout could
  take a slice out of the commission, but it needs accounts and a backend first.
