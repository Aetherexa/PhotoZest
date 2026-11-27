# PhotoZest iOS launch gates

Native SwiftUI app (iOS 17+) based on Apple Photos + on-device Vision. A Photos album is a collection of asset references, **not a filesystem move**.

## Required before TestFlight
- Apple Developer Program access; final Bundle ID and signing team.
- Xcode simulator + iPhone validation with limited/denied permissions and iCloud-only assets.
- Confirm request for Photos library read access only when user opts to use library scanning; prefer PhotosPicker for ad hoc selection.
- Verify album-write authorization and add-only behavior; no silent deletes, duplicate exports or original relocations.
- App icon, launch screen, localized privacy descriptions, privacy manifest and App Store privacy questionnaire.
- Accessibility, crash handling, large libraries, low memory, thermal and offline QA.
- Native OCR accuracy and user review; no OCR financial assertion as settled fact.
- Configure StoreKit 2, product identifiers, restore, cancellation/refunds, subscription terms and a trusted entitlement verification path before enabling commercial plans.
- Automated CI xcodebuild on macOS, signed archive, TestFlight, App Store Connect and Apple review.

## Parity caveats
Android: verified SAF provider moves with user authorization.
iOS: Photos albums reference originals; no general Android-style physical move.
Web: browser-local virtual collections; no physical relocation.
