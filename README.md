# PhotoZest

**Your photos. Your way.** Privacy-first organization for Android, iOS and the web, by Aetherexa.

## Monorepo
- `android/` — Kotlin + Jetpack Compose + ML Kit + Android SAF; reconstructed from the preserved PhotoFlow source snapshots using `bash scripts/materialize-android.sh`.
- `web/` — independent React/TypeScript app using browser-local image selection, classification, EXIF, optional OCR and virtual collections.
- `ios/` — native SwiftUI and Apple PhotoKit/Vision foundation, with standalone Swift package tests.
- `docs/` — migration provenance, platform and release gates.

The Android code is stored as historical source archives under `.build-sources/photoflow/`. The materialization script restores *readable Kotlin source* to `android/`, applies compact packaging, and switches the product ID and user-facing name to PhotoZest. The workflow attempts to check those readable sources into this branch.

## Quick checks
```sh
bash scripts/materialize-android.sh
cd android && gradle :app:testDebugUnitTest :app:assembleDebug
cd ../web && npm install && npm test && npm run build
cd .. && swift test --package-path ios/PhotoZestCore
# On macOS with XcodeGen and Xcode:
cd ios && xcodegen generate && xcodebuild -project PhotoZest.xcodeproj -scheme PhotoZest -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Status and safeguards
- Android prototype is based on PhotoFlow v0.8 (compact); commercial plan policy from v0.9 is **not** a payment or subscription implementation.
- iOS is a native preview foundation, **not** a signed IPA or TestFlight/App Store release.
- Android original-file changes require explicit SAF authorization; iOS stores *album references* to existing Photos assets, not physical moves; the browser uses only virtual collections.
- Nothing is published to app stores. Billing, device QA, privacy disclosures, signing, App Store Connect and Google Play approval remain pending.
- No open-source license has been selected. A public repo alone is not a license grant.

See `docs/MIGRATION.md` and `docs/IOS_RELEASE.md`.