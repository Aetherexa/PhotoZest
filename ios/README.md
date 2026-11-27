# PhotoZest iOS

Native iOS 17+ SwiftUI prototype: PhotosPicker (explicit user selection), optional authorized Recent Library scan, on-device Vision face/image classification, and safely adding existing Photos assets to albums after review. No file relocation, upload, or silent deletion.

- Core recognition/plan policy: `swift test --package-path ios/PhotoZestCore` (runs on Linux and macOS).
- App: `cd ios && xcodegen generate && xcodebuild -project PhotoZest.xcodeproj -scheme PhotoZest -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build` (requires Xcode/macOS).

**Not production-ready:** App Store signing, StoreKit 2 verified entitlements, privacy manifest, icon, accessibility/device QA and TestFlight remain outstanding. UI grants Free policy only; no paywall is active. See `../docs/IOS_RELEASE.md`.
