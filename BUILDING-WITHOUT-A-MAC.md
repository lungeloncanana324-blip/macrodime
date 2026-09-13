# Building MacroDime without a Mac

Apple requires macOS to compile and submit an iOS app. There is no legal way
around that. What there *is*: rent a Mac per build, from CI, and never own one.

This repo is set up for that. `project.yml` defines the Xcode project as text,
so it can be generated on a runner instead of created in Xcode.

---

## Stage 1 — Fix the compile errors (free, no Apple account)

**Simulator builds require no code signing.** So the ~2,900 lines of
SwiftUI/SwiftData that have never been compiled can be built and fixed for
nothing, before you spend a cent.

1. Push this repo to GitHub.
2. `.github/workflows/ios.yml` runs automatically on a `macos-15` runner.
3. Read the **job summary** — it collects every compiler diagnostic into one
   list, so each run gives you a full fix list rather than one error at a time.

Cost: **free on public repos.** On private repos, macOS minutes bill at **10×**,
so a free account's 2,000 monthly minutes is ~200 macOS minutes — roughly 15-25
runs of this workflow. Make the repo public while fixing errors if that pinches.

Expect several rounds. This code has never seen a compiler, and the SwiftData
model graph plus the `@Observable` view models are the likeliest to need work.

## Stage 2 — Run it on a real device (needs $99/yr)

Once it compiles, enrol in the Apple Developer Program and ship to TestFlight.
You do **not** need a Mac for this, but you do need an iPhone or iPad to see the
app run.

Signing without a Mac:

- Generate the certificate request with OpenSSL, which runs fine on Windows/WSL:
  ```
  openssl req -new -newkey rsa:2048 -nodes -keyout ios.key -out ios.csr \
    -subj "/emailAddress=you@example.com/CN=Your Name/C=ZA"
  ```
  Upload `ios.csr` at developer.apple.com, download the `.cer`, then:
  ```
  openssl x509 -in ios.cer -inform DER -out ios.pem -outform PEM
  openssl pkcs12 -export -inkey ios.key -in ios.pem -out ios.p12
  ```
- Or skip all of that and let **Codemagic** manage signing from an App Store
  Connect API key — see `codemagic.yaml`. This is the easier path.

## Stage 3 — Submit

App Store Connect is a website; submission itself never needs a Mac. Uploading
the build does, and the CI runner does it for you (`altool`/`fastlane pilot`).

---

## What you lose without a Mac

Be clear-eyed about this — it is the real cost:

| | With a Mac | CI only |
|---|---|---|
| See a compile error | seconds | 5-15 min round trip |
| SwiftUI previews | yes | **no** |
| Simulator, click around | yes | **no** |
| Debugger, view hierarchy | yes | **no** |
| See the UI at all | instantly | screenshots, or TestFlight on a real iPhone |

Iterating on *layout* this way is genuinely painful. Fixing *compile errors* is
perfectly fine. So: use CI to get it compiling and onto TestFlight, then judge
the UI on a physical iPhone.

**If you have no Apple device at all**, you are flying blind on the UI, and I'd
think hard about whether iOS is the right first target. The `Domain/` and
`Engine/` layers are plain Swift with no Apple dependencies — the science and
the swap algorithm would port to a web app you can actually see and iterate on.

## Paid alternatives, if CI round trips get tiring

| Option | Cost | Notes |
|---|---|---|
| **Codemagic** | 500 min/mo free, then ~$0.095/min | Built for exactly this; web UI, handles signing |
| **Scaleway Apple silicon** | ~€0.10/hr, 24h minimum | Cheapest real Mac you can SSH into |
| **MacinCloud** | ~$1/hr or ~$30/mo | Managed, remote desktop, beginner-friendly |
| **MacStadium** | ~$79+/mo | Dedicated hardware, overkill for one app |
| **AWS EC2 Mac** | ~$0.65/hr, **24h minimum** | Avoid; minimum charge makes it expensive |

A remote desktop Mac (MacinCloud/Scaleway) is worth one month's rent when you
reach the UI-polish stage, purely to get previews and the simulator back.
