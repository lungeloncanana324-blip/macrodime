# MacroDime for Android

The Google Play version of MacroDime. Same engines, same rules, same 57-food
catalogue and the same prices as the iOS app, rebuilt natively in Kotlin and
Jetpack Compose. Unlike iOS, all of it builds, tests and runs on Windows.

## Layout

```
core/   Domain + Engine: pure Kotlin, no Android imports
        a port of MacroDime/Domain and MacroDime/Engine, held to the iOS tests
app/    Room database, view models, Compose UI, the Android manifest
```

| iOS | Android |
| --- | --- |
| `Domain/`, `Engine/` (Swift) | `core/src/main/kotlin/.../domain`, `.../engine` |
| `MacroDimeTests/` (XCTest) | `core/src/test` (JUnit), the same cases test for test |
| SwiftData `@Model` classes | Room entities, `app/.../data/Entities.kt` |
| `MealPlannerViewModel`, `UserProfileViewModel` | `DayPlanViewModel`, `OnboardingViewModel` |
| SwiftUI views | Compose screens in `app/.../ui` |
| `SourcedPrices.swift` | `SourcedPrices.kt`, generated from the same table by `scripts/update_prices.py` |

## Requirements

- JDK 17 or newer (21 recommended). Android Studio bundles one.
- Android SDK with platform 37. Android Studio installs it; on the command line,
  the build downloads it once the SDK licence has been accepted.
- `android/local.properties` with `sdk.dir=` pointing at the SDK (forward
  slashes on Windows: `sdk.dir=C:/Users/you/AppData/Local/Android/Sdk`).
  Android Studio writes this file itself.

## Build and test

From `android/`:

```
./gradlew :core:test                 # 145 engine tests, plain JVM, seconds
./gradlew :app:testDebugUnitTest     # database and full-app UI tests (Robolectric)
./gradlew :app:lintRelease           # lint is an error, not a report
./gradlew :app:verifyReleasePermissions
./gradlew :app:assembleDebug         # an installable debug APK
./gradlew :app:bundleRelease         # the .aab Play Console takes
```

On Windows use `gradlew.bat`, or `./gradlew` from Git Bash.

`verifyReleasePermissions` fails the build if the release manifest requests any
permission. MacroDime declares none, and the Play Data safety answer "No data
collected" depends on that staying true.

## Running it

- **On a phone:** enable developer options and USB debugging, plug it in, then
  `./gradlew :app:installDebug`.
- **In Android Studio:** open the `android/` folder, pick a device, Run.
- **With demo data** (debug builds only), for screenshots:
  `adb shell am start -n com.lungelo.macrodime/.MainActivity --ez macrodime.demo true --ei macrodime.tab 1`
  where the tab is 0 Today, 1 Plan, 2 Groceries, 3 Settings. Release builds
  ignore these extras: any app on a phone can send them, and in a release build
  they would overwrite the user's profile.

## Signing for Play

Play App Signing keeps the key that signs the app users install. You keep an
**upload key**, which signs what you send to Play Console. If the upload key is
lost, Google can reset it; the app is not lost with it.

1. Create the key once (keytool ships with the JDK):

   ```
   keytool -genkeypair -v -keystore macrodime-upload.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Put it in `android/` and create `android/keystore.properties`:

   ```
   storeFile=macrodime-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```

   Both are listed in `.gitignore`. Back the `.jks` and the passwords up
   somewhere that is not this repository.

3. `./gradlew :app:bundleRelease -PversionCode=1` writes
   `app/build/outputs/bundle/release/app-release.aab`. Every later upload needs
   a higher `versionCode`.

To have CI produce signed bundles instead, add the four
`MACRODIME_UPLOAD_*` repository secrets listed at the top of
`.github/workflows/android.yml` (the keystore goes in base64:
`base64 -w0 macrodime-upload.jks`).

## Store listing and declarations

Everything Play Console asks, with the answers and the order to do it in, is in
[`../docs/play-store-listing.md`](../docs/play-store-listing.md). The icon,
feature graphic and their sources are in `play-store/`. The store screenshots
are the app's real screens, rendered by `ScreenshotTest` with the demo week and
framed with a caption each by `scripts/make_store_screenshots.py`.

## Deliberate differences from iOS

| What | Why |
| --- | --- |
| Cloud backup is off; device-to-device transfer is on | Keeps "nothing leaves the phone" literally true for the Data safety form, without costing a user their history when they change phones |
| Portion and grocery actions are in a visible menu | iOS uses long-press and swipes, which Android users do not look for |
| Progress photos are re-encoded on import | Drops GPS and camera metadata and caps the size at 2,048 px |

Five more differences existed until 2026-10-02, when the iOS app was fixed to
match: a leftover currency rate multiplying dollar prices, the catalogue
seeder's hand-bumped version number, the onboarding budget warning formatted in
USD, a grocery list that waited for a tap on Regenerate, and an exclusion
reason that varied run to run. Each fix is listed in
`docs/SHIPPING-GAPS.md` section 0.

## Regenerating assets

```
python3 android/scripts/make_icons.py                       # the icon everywhere: launcher, themed, notification, Play, iOS, website
powershell -File android/scripts/render_feature_graphic.ps1  # feature graphic, from its HTML
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'  # the screens, with the demo week
python3 android/scripts/make_store_screenshots.py           # store screenshots, framed and captioned
python3 scripts/update_prices.py --sync-kotlin               # Kotlin prices, from the Swift table
```
