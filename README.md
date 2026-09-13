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
| `Domain/NutritionFacts.swift` | Macro arithmetic and `MacroDrift` |
| `Domain/MealItem.swift` | `FoodSnapshot`, `Portion`, `MealItem` |
| `Domain/UnitConversion.swift` | metric ↔ imperial, display formatting |
| `Engine/BodyScienceEngine.swift` | BMI, BMR, TDEE, calorie target, macro split |
| `Engine/BudgetFoodEngine.swift` | Low-cost swap + rebalance |
| `Engine/FoodCatalog.swift` | 57 curated ingredients (36 strict tier, 21 moderate) |
| `Engine/GroceryListBuilder.swift` | Meal → shopping list aggregation |
| `Persistence/` | `UserProfile`, `BodyMeasurement`, `FoodItem`, `MealPlan`, `PlannedMeal`, `MealPortion`, `GroceryItem` |
| `ViewModels/` | `UserProfileViewModel`, `MealPlannerViewModel` |
| `Views/` | Onboarding, Dashboard, Meal Planner, Grocery List |

### Two conventions worth knowing

**Enums are stored as raw strings, not `Codable` blobs.** Raw values survive schema
migration and can be used inside `#Predicate`; each model exposes a typed computed
accessor over the stored string.

**The engine never touches a `@Model`.** SwiftData models are not `Sendable` and are
unsafe to read off their own context, so `FoodItem.snapshot` converts to a value type
at the boundary. That is what lets the swap engine propose an entire alternative day
without writing anything — the user sees the preview before it is committed.

## The science

| Quantity | Formula |
| --- | --- |
| BMI | `weight_kg / height_m²` |
| BMR | Mifflin-St Jeor: `10·kg + 6.25·cm − 5·age + (+5 male / −161 female)` |
| TDEE | `BMR × activity` (1.2 / 1.375 / 1.55 / 1.725) |
| Target | Fat loss `TDEE × 0.80`; muscle gain `TDEE × 1.08` |
| Protein | 2.0 g/kg cutting, 1.8 g/kg gaining |
| Fat | 25% of target calories ÷ 9 |
| Carbs | Remaining calories ÷ 4 |

Worked example — 80 kg, 180 cm, 30 y, male, moderately active, fat loss:

```
BMR    1780 kcal      BMI 24.69
TDEE   2759 kcal      (1780 × 1.55)
Target 2207.2 kcal    (−551.8, a 20% deficit)
       160 g protein · 253.85 g carbs · 61.31 g fat
```

Every figure in the test suite is worked by hand from these formulae, not captured
from a previous run — a test that only asserts "same as last time" cannot catch a
wrong formula.

### Two safety rules the spec does not state

Protein and fat are prescribed independently, so they can collectively exceed the
calorie target and drive carbohydrate negative. Rather than emit a nonsense plan the
engine squeezes fat toward a 20% floor, then clamps protein, and **reports every
override** in `Prescription.adjustments` so the UI never shows a number that silently
disagrees with the stated rules. Targets are also floored at 1,200 kcal (female) /
1,500 kcal (male).

## The Low-Cost Swap engine

Three rules: swap **within a category only** (salmon may become tuna or eggs, never
oats); measure drift **against the original meal, cumulatively**; and **never trade
up** a tier.

### Why a one-for-one substitution does not work

Matching canned tuna to salmon on protein leaves the meal **49.5% short on fat** — the
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

### A known weakness

That chain includes **fresh broccoli → carrots**. It is macro-valid and inside
tolerance, but it is not a swap most people want — the two are not culinary
substitutes. The cause is that `FoodCategory.vegetable` anchors on *calories*, so
any vegetable can stand in for any other. Protein, carb and fat swaps do not have
this problem because their categories anchor on a specific macro.

The honest fix is a finer-grained category (leafy / root / cruciferous) or a
user-facing "don't suggest this again" list. Until then, the per-ingredient swap
picker is the mitigation: it shows ranked options and lets the user choose rather
than accepting the engine's pick.

Two bugs found and fixed while building this, both covered by regression tests:

- **Anchoring on energy dominance.** Salmon is 153 kcal of fat against 136 kcal of
  protein, so "match the most energetic macro" matched it on *fat* and proposed
  roughly twelve cans of tuna. Anchoring is now driven by `FoodCategory.anchorAxis`.
- **The fat floor could raise fat.** `max(20% of calories, 0.5 g/kg)` exceeds the 25%
  allocation for a heavy person on a small target, so the "reduce fat" branch silently
  *increased* it. The floor is now capped at the original allocation.

## Prices and nutrition data

Macros follow standard reference data (USDA FoodData Central for whole foods, typical
label values for packaged goods). **Prices are approximate US national supermarket
averages** and exist to *rank* ingredients against each other, which is all the swap
engine needs. They are not converted to local currency — `DisplayFormat.currency`
formats in the device locale without converting the amount. A shipping build should
localise the table or let users edit `costPerServing` per item.

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
`ViewModels/` and `Persistence/` are excluded from that manifest — they need
SwiftUI, SwiftData and UIKit, which are Apple-only and closed source, so they
build in Xcode and nowhere else.

### The app, without a Mac

Apple requires macOS to compile iOS. You do not have to *own* one — CI rents a
Mac per build. `project.yml` defines the Xcode project as text so it can be
generated on a runner, and `.github/workflows/ios.yml` builds the app for the
simulator, which needs no code signing and therefore no Apple account.

See **BUILDING-WITHOUT-A-MAC.md** for the full path to TestFlight.

### The app, in Xcode

There is no `.xcodeproj` in this repo — these are source files. To build:

1. Xcode → new **iOS App**, product name `MacroDime`, interface **SwiftUI**,
   storage **SwiftData**, minimum deployment **iOS 17.0**.
2. Delete the generated `ContentView.swift` and `MacroDimeApp.swift`.
3. Drag `MacroDime/` into the project ("Create groups", add to the app target).
4. Drag `MacroDimeTests/` into the test target.
5. Add `NSPhotoLibraryUsageDescription` to Info.plist — the progress-photo picker
   needs it, and the app will crash on that screen without it.

Then ⌘U to run the tests.

### Status

**The pure layer compiles and its tests pass.** Built with Swift 6.2 on Linux
(WSL Ubuntu 24.04), in Swift 5 language mode to match Xcode 15 / iOS 17:

```
swift build    Build complete! (12.44s)     0 errors, 0 warnings
swift test     Executed 44 tests, with 0 failures
```

Every figure in this README is output from that compiled binary.

**The whole app compiles too, including the SwiftUI and SwiftData layer.** CI
builds it on a hosted macOS runner (`.github/workflows/ios.yml`) — which is how
it gets built without owning a Mac:

```
xcodebuild build   -scheme MacroDime                 success
xcodebuild test    iPhone 16 simulator, 44 tests     success
```

Three errors stood between the code and that result. Two were fixed by
inspection before the first run, neither reachable from the Linux tests:

- a `didSet` inside an `@Observable` class — the macro rewrites stored
  properties into computed ones, which cannot also carry property observers
- actor isolation across all 19 `View` structs — SwiftUI isolates `body` to the
  main actor through the protocol, but not a struct's other members

The third, the missing `platforms:` declaration above, was caught by CI itself.

**What compiling does not prove.** The app has never been *launched*. SwiftData
resolves its schema at runtime, so a bad model graph surfaces on first launch
rather than at build time — and `MacroDimeApp.init` deliberately `fatalError`s
if the container will not open. Onboarding, the catalogue seeder and the first
save are all still unexercised. The next real milestone is TestFlight on a
physical device.
