# PhotoZest

**Your photos. Your way.** Privacy-first photo organization by Aetherexa for **Android, iOS and Web**.

## Independently deployed applications

| Application | Directory | CI/release workflow | Production profile |
| --- | --- | --- | --- |
| Android · Jetpack Compose / ML Kit | `android/` | [Android](.github/workflows/android.yml) | `config/features/android.production.json` |
| iOS · SwiftUI / Vision / PhotoKit | `ios/` | [iOS](.github/workflows/ios.yml) | `config/features/ios.production.json` |
| Web · React / TypeScript | `web/` | [Web](.github/workflows/web.yml) | `config/features/web.production.json` |

**Three separate CI/CD pipelines:** each target validates only its own release and deploys only on an approved manual action from `main`. Platform-specific, compile-time feature gates let us ship a feature on one platform and leave it disabled elsewhere. See **[Release pipelines and feature rollout](docs/RELEASE_PIPELINES.md)**.

## Local development

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/feature_flags.py --platform android --profile preview
python3 scripts/feature_flags.py --platform ios --profile preview
python3 scripts/feature_flags.py --platform web --profile preview
# Android (requires Android SDK + Gradle 8.13, JDK 17):
cd android && gradle :app:testDebugUnitTest :app:assembleDebug
# Web (requires Node 22):
cd ../web && npm install && npm test && npm run build
# Swift policies (Swift installed):
cd .. && swift test --package-path ios/PhotoZestCore
# iOS app (macOS + Xcode + XcodeGen):
cd ios && xcodegen generate && xcodebuild -project PhotoZest.xcodeproj -scheme PhotoZest -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Older PhotoFlow layered Android sources remain under `.build-sources/photoflow` for audit and reconstruction via `scripts/materialize-android.sh`; **edit the readable `android/` sources for current development**, not the historical overlays.

## Release safeguards

- Android can change authorized original files only after explicit native SAF permissions and user confirmation; iOS uses Photos album membership (no physical moves); Web collections are virtual.
- **Production defaults disable Android original-file moves/OCR, iOS album writes, and Web OCR** until corresponding QA passes. Preview builds may retain these for testing.
- No backend image upload endpoint is introduced. Browser ML/OCR assets may be downloaded from model providers.
- Free/Plus/Pro/Max tier policies exist, but **payment and trusted entitlement enforcement are not yet implemented**.
- No store signing, Google Play listing or TestFlight credentials are committed; manual promotions are gated and cannot run without configuration.
- A public GitHub repository is not a grant of an open-source license.

See [platform capability matrix](docs/PLATFORM_MATRIX.md), [iOS release gates](docs/IOS_RELEASE.md), and [migration notes](docs/MIGRATION.md).
