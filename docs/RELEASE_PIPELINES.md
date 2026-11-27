# Independent production delivery — Android, iOS and Web

PhotoZest is one product with **three independently shipped applications**. GitHub Actions workflows are separated by target, and each platform has its **own preview and production feature configuration**.

## Ownership, release and deployment boundaries

| Target | Workflow | Validate automatically on PR/push | Manual production promotion | GitHub environment | Production features |
| --- | --- | --- | --- | --- | --- |
| Android | `.github/workflows/android.yml` | Kotlin unit tests + four ABI debug APKs | Signed Android App Bundle to **Google Play internal testing** | `android-production` | `config/features/android.production.json` |
| iOS | `.github/workflows/ios.yml` | Swift package tests + Xcode iOS simulator build | Signed IPA upload to **App Store Connect / TestFlight** | `ios-production` | `config/features/ios.production.json` |
| Web | `.github/workflows/web.yml` | Vitest, TS typecheck, Vite bundle | GitHub Pages at `https://aetherexa.github.io/PhotoZest/` (only after Pages enabled) | `web-production` | `config/features/web.production.json` |

Android/iOS 'production' means the **production-configured binary**; the initial publishing target is an internal/beta track. These jobs do **not** publish directly to the public Play/App Stores.

Each workflow only responds automatically to files in its own platform plus the shared feature-generator scripts. Shared generator changes correctly validate all three platforms. There is no monolithic release pipeline.

## Feature configuration and release cadence

Source of truth:

```
config/features/
  android.preview.json      android.production.json
  ios.preview.json          ios.production.json
  web.preview.json          web.production.json
```

Each JSON has its platform, its profile, and a full list of **explicit boolean** switches. No inheriting cross-platform flags; undefined and misspelled flags fail validation. Key defaults:

| Capability | Android production | iOS production | Web production |
| --- | --- | --- | --- |
| On-device image recognition | Enabled | Enabled | Enabled (downloadable browser model) |
| Screenshot/payment OCR | Disabled pending QA | Not integrated | Disabled pending QA |
| Existing originals moved into real folders | Disabled pending real-device QA | Not supported | Not supported |
| iOS authorized Photos library scan | N/A | Enabled | N/A |
| iOS add existing Photos assets to albums | N/A | Disabled pending real-device QA | N/A |
| Browser smart collections | N/A | N/A | Enabled |
| Browser original-file sharing | N/A | N/A | Enabled when supported by browser |
| Browser folder import | N/A | N/A | Enabled |

Preview profiles intentionally retain the existing prototype features for QA.

**How to enable just one platform:** edit only its `*.production.json` (for instance `web.production.json`), create a PR, wait for that platform's checks, merge to `main`, then manually dispatch **only that platform's workflow** with `publish=true` and authorize the environment gate. The other published apps and their feature profiles remain unchanged. App store releases also need new build/version numbers.

Feature flags are **compile-time distribution controls**, not remote kill switches: a previously installed Android/iOS binary keeps its existing behavior until updated; deployed web changes take effect once clients refresh/cache-expire. Store entitlements must still be authenticated independently. Flags **never** permit bypassing Free/Plus/Pro/Max billing rules, and billing is not enabled yet.

## Local commands

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/feature_flags.py --validate-all
python3 scripts/feature_flags.py --platform android --profile preview
python3 scripts/feature_flags.py --platform ios --profile preview
python3 scripts/feature_flags.py --platform web --profile preview
# Alternatively generate for a specific release:
python3 scripts/feature_flags.py --platform web --profile production
```

The generator writes typed platform constants into Kotlin, Swift and TypeScript. It validates **all six profiles** on each invocation. Generated files are tracked with preview defaults for ordinary local builds; each GitHub workflow overwrites **only its target** with the platform's production profile before validating/building. Do not manually edit generated files.

## Production environment / credential setup

Create and protect the following GitHub environments under **Settings → Environments**:

- `android-production`: set required reviewer(s), deployment branch `main`, and secrets `ANDROID_UPLOAD_KEYSTORE_B64`, `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`. Configure your Google Play Console application ID `com.aetherexa.photozest` and grant the service account upload permission. Choose a **new** `version_code` for every upload. The workflow publishes to the **internal** track only.
- `ios-production`: required reviewer(s), deployment branch `main`, `IOS_TEAM_ID`, `IOS_PROFILE_NAME`, `IOS_PROVISIONING_PROFILE_B64`, `IOS_DISTRIBUTION_CERT_B64`, `IOS_CERT_PASSWORD`, `APPSTORE_KEY_ID`, `APPSTORE_ISSUER_ID`, `APPSTORE_PRIVATE_KEY_B64`. Finalize the iOS bundle ID `com.aetherexa.photozest.ios`, distribution provisioning, App Store Connect record and agreements. Supply a new `build_number` for TestFlight uploads. Confirm `xcrun altool` availability on the selected Xcode image before using the release job; if Apple removes it, migrate the upload to a supported Transporter/Fastlane step.
- `web-production`: required reviewer(s), deployment branch `main`; enable **Settings → Pages → Source: GitHub Actions** in this repository. The workflow uses `pages:write` and OIDC `id-token:write`. The production base path is `/PhotoZest/`. Pages will not deploy until configured and a reviewer explicitly triggers publishing.

**Do not** store signing identities, API keys or provisioning profiles in JSON flag files, commits, PR comments or GitHub Actions logs. Publish jobs validate required inputs/secrets and fail closed.

Use GitHub **Actions → [platform workflow] → Run workflow** on **main** with **publish=true**. A routine push or PR only runs validation; it never promotes a release. Approved environment reviewers are an additional gate.

## Current release gates

- The Android and iOS Play/TestFlight jobs are **wired**, not executed end-to-end; they require real organization Apple/Google credentials, signing and store-side configuration.
- Device acceptance is required before enabling original-file moves or album writes. Test with disposable assets and verify no silent copy/delete.
- iOS adds **album references**, not filesystem moves; Web collections are virtual.
- Mobile payment entitlements and store-ready privacy/data-safety disclosures are separate projects.
- Automated deployment safety: review all environment protections and store tracks before any public launch.
