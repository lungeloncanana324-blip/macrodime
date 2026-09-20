# MacroDime

An iOS 17+ app (Swift / SwiftUI / SwiftData) that bridges BMI and body-composition
science with realistic meal budgeting.

## Architecture

Four layers, each depending only on the ones above it. The dependency direction is
the point: the engines are pure Swift, so they are testable without a
`ModelContainer` and reusable outside SwiftUI.

```
Domain/       value types + vocabulary    no SwiftData, no SwiftUI
Engine/       pure calculation            depends on Domain only
Persistence/  SwiftData @Model records    converts to Domain at the boundary
ViewModels/   @Observable, @MainActor     orchestrates Engine + Persistence
Views/        SwiftUI                     reads ViewModels
```

| Path | What lives there |
| --- | --- |
| `Domain/Enums.swift` | Goal, activity, tier, slot, section, category |
| `Domain/SwapGroup.swift` | The finer substitution axis: culinary families for vegetables |
| `Domain/NutritionFacts.swift` | Macro arithmetic and `MacroDrift` |
| `Domain/MealItem.swift` | `FoodSnapshot`, `Portion`, `MealItem` |
| `Domain/UnitConversion.swift` | metric ↔ imperial, display formatting |
| `Domain/Money.swift` | The catalogue's currency, amounts, and the user's conversion rate |
| `Domain/DietaryProfile.swift` | Traits, dietary patterns, exclusions, schedule, cooking effort |
| `Engine/BodyScienceEngine.swift` | BMI, BMR, TDEE, calorie target, macro split |
| `Engine/BudgetFoodEngine.swift` | Low-cost swap + rebalance |
| `Engine/FoodCatalog.swift` | 57 curated ingredients, plus the dietary and prep tables |
| `Engine/DietaryFilter.swift` | The one gate between the catalogue and any suggestion |
| `Engine/PlanAudit.swift` | The gap detector: what is wrong with this plan, and what to do |
| `Engine/GroceryListBuilder.swift` | Meal → shopping list aggregation |
| `Persistence/` | `UserProfile`, `BodyMeasurement`, `FoodItem`, `MealPlan`, `PlannedMeal`, `MealPortion`, `GroceryItem` |
| `Persistence/DemoData.swift` | Optional seeded day, used only by the screenshot workflow |
| `ViewModels/` | `UserProfileViewModel`, `MealPlannerViewModel` |
| `Views/` | Onboarding, Dashboard, Meal Planner, Grocery List, `PlanGapsCard` |
| `docs/` | Shipping gap report, privacy policy text, App Store listing copy |

### Two conventions worth knowing

**Enums are stored as raw strings, not `Codable` blobs.** Raw values survive schema
migration and can be used inside `#Predicate`; each model exposes a typed computed
accessor over the stored string.

**The engine never touches a `@Model`.** SwiftData models are not `Sendable` and are
unsafe to read off their own context, so `FoodItem.snapshot` converts to a value type
at the boundary. That is what lets the swap engine propose an entire alternative day
without writing anything, the user sees the preview before it is committed.

## The science

| Quantity | Formula |
| --- | --- |
| BMI | `weight_kg / height_m²` |
| BMR | Mifflin-St Jeor: `10·kg + 6.25·cm - 5·age + (+5 male / -161 female)` |
| TDEE | `BMR × activity` (1.2 / 1.375 / 1.55 / 1.725) |
| Target | Fat loss `TDEE × 0.80`; muscle gain `TDEE × 1.08` |
| Protein | 2.0 g/kg cutting, 1.8 g/kg gaining |
| Fat | 25% of target calories ÷ 9 |
| Carbs | Remaining calories ÷ 4 |

Worked example: 80 kg, 180 cm, 30 y, male, moderately active, fat loss:

```
BMR    1780 kcal      BMI 24.69
TDEE   2759 kcal      (1780 × 1.55)
Target 2207.2 kcal    (-551.8, a 20% deficit)
       160 g protein · 253.85 g carbs · 61.31 g fat
```

Every figure in the test suite is worked by hand from these formulae, not captured
from a previous run, a test that only asserts "same as last time" cannot catch a
wrong formula.

### Two safety rules the spec does not state

Protein and fat are prescribed independently, so they can collectively exceed the
calorie target and drive carbohydrate negative. Rather than emit a nonsense plan the
engine squeezes fat toward a 20% floor, then clamps protein, and **reports every
override** in `Prescription.adjustments` so the UI never shows a number that silently
disagrees with the stated rules. Targets are also floored at 1,200 kcal (female) /
1,500 kcal (male).

## The Low-Cost Swap engine

Three rules: swap **within a swap group** (salmon may become tuna or eggs, never
oats); measure drift **against the original meal, cumulatively**; and **never trade
up** a tier.

### Why a one-for-one substitution does not work

Matching canned tuna to salmon on protein leaves the meal **49.5% short on fat**, the
salmon was carrying 17 g of it. Under a 10% tolerance the single most valuable swap in
the app is rejected.

So a substitution is followed by a **rebalance pass**: the fat and carb sources already
in the meal are re-portioned to close the gap. On the salmon dinner the olive oil goes
from 1 tbsp to 2.25, and the meal lands within 4.6% on every macro.

Output below is from the **compiled engine** (Swift 6.2), not a model of it:

```
Salmon dinner    732.0 kcal · 43.2 P · 69.6 C · 31.5 F    $6.76

  raw swap → tuna      drift [17.5, 4.6, 0.0, 49.5]%   REJECTED
  + rebalance oil      drift [ 2.8, 4.6, 0.0,  4.0]%   ACCEPTED

7 candidates pass both gates for the salmon portion alone:
  Large Eggs             2.75 srv   saves $4.42   drift 3.2%
  Chicken Drumsticks     1.25 srv   saves $3.98   drift 3.5%
  Pork Shoulder          1.25 srv   saves $3.96   drift 3.2%
  Canned Tuna in Water   1.00 srv   saves $3.75   drift 4.6%
  Chicken Thighs         1.25 srv   saves $3.69   drift 6.9%
  Canned Sardines        1.50 srv   saves $3.17   drift 4.0%
  Greek Yogurt           2.00 srv   saves $3.10   drift 3.9%

full chain:  salmon → eggs (olive oil re-portioned 1 → 0.25)
             fresh broccoli → carrots · olive oil → canola oil
FINAL        741.0 kcal · 40.7 P · 72.3 C · 30.4 F        $1.37
             saves $5.39 (79.7% cheaper) at 5.79% worst-case drift
```

### The vegetable gap, closed

That chain used to include **fresh broccoli → carrots**. Macro-valid, inside
tolerance, and not a swap any cook would make: the two are not culinary
substitutes. The cause was that `FoodCategory.vegetable` anchors on *calories*, so
any vegetable could stand in for any other, and a single vegetable's protein loss
is diluted in the meal-wide drift (7.3% of 43 g, well inside 10%).

The fix is a second, finer axis. `SwapGroup` answers the question the swap engine
actually needs answered, "what would a person accept in place of this?", and for
six of the seven categories the answer is the category itself. Vegetables are
split into culinary families: leafy green, cruciferous, root, allium, fruiting,
stems, and frozen mixed bags.

The default for an unclassified vegetable is deliberately the safe direction. It
gets its own private group, so it substitutes with nothing, and a catalogue test
fails the build if any vegetable ships without a family. The old behaviour cannot
come back through a new ingredient.

What survives is the swap worth having: fresh broccoli to frozen broccoli, twice
the price difference and identical macros. What is gone is broccoli to carrots,
and the regression is pinned by `testCarrotsAreOfferedOnlyWhenTheFamilyGateIsLifted`,
which shows the swap *is* still legal on macros alone. The family gate is the only
thing rejecting it.

Two bugs found and fixed while building this, both covered by regression tests:

- **Anchoring on energy dominance.** Salmon is 153 kcal of fat against 136 kcal of
  protein, so "match the most energetic macro" matched it on *fat* and proposed
  roughly twelve cans of tuna. Anchoring is now driven by `FoodCategory.anchorAxis`.
- **The fat floor could raise fat.** `max(20% of calories, 0.5 g/kg)` exceeds the 25%
  allocation for a heavy person on a small target, so the "reduce fat" branch silently
  *increased* it. The floor is now capped at the original allocation.

## Prices, currency and nutrition data

Macros follow standard reference data (USDA FoodData Central for whole foods, typical
label values for packaged goods). **Prices are approximate US national supermarket
averages** and exist to *rank* ingredients against each other, which is all the swap
engine needs.

Every price in the app is **USD**, and the display used to lie about it: the
amount was formatted with the device locale's currency, so a user in South Africa
read `R9.00` for a day of food. That is worse than not localising at all, and it is
invisible to anyone testing in the US.

`Money` and `PriceBook` now make the currency a property of the data rather than a
guess about the device. A different currency is a *conversion*, at a rate the user
types in Settings, and the app says where its prices come from when it does it.
There is deliberately no bundled exchange-rate table: an invented rate would put a
number on screen that nobody could check. Storage stays single-currency, so a
budget and the meal costs it is compared against can never disagree about units.

The honest limit is that this localises the *symbol and the rate*, not the food.
57 US supermarket items priced in dollars are still 57 US supermarket items. A
real launch outside the US needs a local catalogue.

## Doing what the user asked, not what they should want

Targets alone do not make a followable plan. A 160 g protein target is useless to
someone who does not eat meat if every anchor in the plan is chicken, so
onboarding asks about the things that decide whether a plan gets followed:

| Question | What it changes |
| --- | --- |
| Dietary pattern | Which protein anchors exist at all (omnivore, pescatarian, vegetarian, vegan) |
| Allergies, intolerances, faith rules | Ingredients removed from the catalogue before anything is planned |
| Individual food bans | The one food the user will not eat no matter how well it fits |
| Meals a day | Which slots the plan fills, and how the target is divided |
| How much cooking | The longest preparation the catalogue may offer (5, 15, 30 minutes, or any) |
| Meals eaten out per week | Disclosed in the audit rather than silently ignored |

`DietaryFilter` is the single gate, and it runs once, in `MealPlannerViewModel.load`,
before the plan, the swap engine or the ingredient picker can see the catalogue. A
prohibited food cannot be suggested because it is not in the list being searched.

An important detail: "no answer" means **no constraint**. The unconstrained
profile filters nothing at all, which was a real bug found by the test suite: with
`PrepEffort.standard` as the default, an "unrestricted" profile silently dropped
dried lentils, brown rice and pork shoulder. A question nobody has been asked yet
must not remove food.

## The plan audit

The app checks its own plan and says what is wrong with it. `PlanAudit` runs in two
places: during onboarding, where the choices behind a problem can still be changed,
and on the dashboard, where the plan the user actually built is measured.

Every gap carries three things: what the problem is, the evidence, and what to do
about it.

| Gap | Severity |
| --- | --- |
| Nothing planned | caution |
| Protein short (80% of target, 60% for blocking) | caution, then blocking |
| Calories more than 15% from target, 30% for the louder message | info, then caution |
| Spend over allowance, 125% for blocking | caution, then blocking |
| A portion that breaks the user's own dietary rules, e.g. after a settings change | blocking |
| Ingredients that take longer than the stated cooking effort | caution |
| A target the budget cannot fund at the cheapest allowed protein source | blocking |
| No protein source left at all under the restrictions | blocking |
| No vegetable at dinner | info |
| One protein source all day | info |
| Empty slots in the user's own schedule | info |
| Fibre, sodium, micronutrients and meals eaten out are not tracked | info, always shown |

That last row is the point of the table. A plan that looks complete and is not is
the real risk, so the app states its blind spots rather than leaving the user to
infer them.

## Building

### The engines, anywhere (no Mac required)

`Domain/` and `Engine/` import nothing but Foundation, so `Package.swift` builds
and tests them on any platform Swift runs on:

```
swift build
swift test          # all 44 tests live in the pure layer
```

This is the practical payoff of keeping framework imports out of the engines: the
science and the swap algorithm can be verified on Linux or in CI. `App/`, `Views/`,
`ViewModels/` and `Persistence/` are excluded from that manifest: they need
SwiftUI, SwiftData and UIKit, which are Apple-only and closed source, so they
build in Xcode and nowhere else.

### The app, without a Mac

Apple requires macOS to compile iOS. You do not have to *own* one: CI rents a
Mac per build. `project.yml` defines the Xcode project as text so it can be
generated on a runner, and `.github/workflows/ios.yml` builds the app for the
simulator, which needs no code signing and therefore no Apple account.

See **BUILDING-WITHOUT-A-MAC.md** for the full path to TestFlight.

### The app, in Xcode

There is no `.xcodeproj` in this repo: these are source files. To build:

1. Xcode → new **iOS App**, product name `MacroDime`, interface **SwiftUI**,
   storage **SwiftData**, minimum deployment **iOS 17.0**.
2. Delete the generated `ContentView.swift` and `MacroDimeApp.swift`.
3. Drag `MacroDime/` into the project ("Create groups", add to the app target).
4. Drag `MacroDimeTests/` into the test target.
5. Add `NSPhotoLibraryUsageDescription` to Info.plist: the progress-photo picker
   needs it, and the app will crash on that screen without it.

Then ⌘U to run the tests.

### Status

**The pure layer compiles and its tests pass.** Built with Swift 6.2 on Linux
(WSL Ubuntu 24.04), in Swift 5 language mode to match Xcode 15 / iOS 17:

```
swift build    Build complete!              0 errors, 0 warnings
swift test     Executed 101 tests, with 0 failures
```

Every figure in this README is output from that compiled binary. The 101 tests
cover the body science, the swap engine, the vegetable swap groups, the currency
and conversion rules, the dietary filter and the plan audit. Several of them were
written to break the system rather than to confirm it: the unconstrained profile
that filtered food, and the four failures it produced, are the clearest example.

**The whole app compiles too, including the SwiftUI and SwiftData layer.** CI
builds it on a hosted macOS runner (`.github/workflows/ios.yml`):

```
xcodebuild build   -scheme MacroDime                 success
xcodebuild test    iPhone 16 simulator, 101 tests     success
```

**What compiling does not prove.** The app has never been *launched*. SwiftData
resolves its schema at runtime, so a bad model graph surfaces on first launch
rather than at build time, and `MacroDimeApp.init` deliberately `fatalError`s if
the container will not open. Onboarding, the catalogue seeder, the demo seeder and
the first save are all unexercised. `.github/workflows/screenshots.yml` exists to
close that gap: it boots a simulator, launches the app with `-MacroDimeScreenshots`
and captures every tab, which doubles as the first real end-to-end test.

**Shipping readiness is tracked in [`docs/SHIPPING-GAPS.md`](docs/SHIPPING-GAPS.md)**,
which lists what blocks a submission, what is unverified, and what it costs. The
App Store listing copy and the App Privacy answers are in
[`docs/app-store-listing.md`](docs/app-store-listing.md), the privacy policy text
in [`docs/privacy-policy.md`](docs/privacy-policy.md), the pricing decision with
the plan for the subscription build in
[`docs/MONETISATION.md`](docs/MONETISATION.md), and where each kind of test runs,
with commands, in [`docs/TESTING.md`](docs/TESTING.md).
