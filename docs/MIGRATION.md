# PhotoFlow → PhotoZest migration

**Destination:** [Aetherexa/PhotoZest](https://github.com/Aetherexa/PhotoZest)

## Sources (preserved, not merged or deleted)
- [PhotoFlow Web SPA PR #25](https://github.com/Aetherexa/Aetherexa-Tools/pull/25), browser engine and approved React UI.
- [Android recognition PR #26](https://github.com/Aetherexa/Aetherexa-Tools/pull/26), Home and conservative classification.
- [Android compact APK PR #27](https://github.com/Aetherexa/Aetherexa-Tools/pull/27), ABI split and R8.
- [Plan foundation PR #28](https://github.com/Aetherexa/Aetherexa-Tools/pull/28), typed plan policies, **not** verified entitlements.

The original Android build sources were stored as layered compressed snapshots under `.build-sources/photoflow/`. `scripts/materialize-android.sh` restores full readable source under `android/`. The newly dedicated web project has a separate Vite entry point. The iOS project uses SwiftUI, PhotoKit and Vision; it is not a Kotlin/ML Kit port.

## Definition of migration complete
1. Android sources materialized, brand and application ID audited; Kotlin tests and split APK build green.
2. Web unit tests, type check and production build green; browser smoke and privacy review on target browsers.
3. Swift package tests and iOS simulator Xcode build green; Photos limited-library and album-write behavior verified on device.
4. No regressions in user-approved selection-only sharing, original-file safety, Needs Review, and explicit opt-in OCR.
5. StoreKit 2 and Play Billing implemented with server-verified entitlements **before** any paid tier is sold.
6. Manual review of public repository contents, license, app names, branding and signing; no sensitive test photos committed.

Original Aetherexa Tools work remains intact. No automatic source deletion, PR merge, or store release.
