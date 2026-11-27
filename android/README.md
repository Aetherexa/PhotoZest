# PhotoFlow Android v0.4 (Preview)

Native Kotlin/Jetpack Compose Android application, with EXIF/GPS metadata tags, scene classification, local screenshot OCR, and a confirmation-gated native move workflow.

## Smart collections

- Scene categories: Landscapes, Group Photos, Selfies, Candids, Food, Portraits, Screenshots, Payment Screenshots and Needs Review.
- Attributes: EXIF date/time, ISO/camera, city approximation from embedded GPS, screenshot filename time, scene/quality hints.
- Receipt OCR: Google Pay, PhonePe, Paytm, Amazon Pay, BHIM and generic UPI, when recognisable in screenshot text.
- Virtual tags: cities, capture times, payment provider, payee, screenshot type, transaction direction/status and OCR amount. Tags do not create copies.
- Explicit Move: organizes each original image into **one** folder, without copy/ZIP/delete fallback.
- Sharing: sends original URIs via Android share sheet and warns when payment screenshots may expose sensitive details.

## Privacy and reliability

Bundled ML Kit models are processed on device, no broad media/storage or INTERNET permissions. The app cannot authenticate a payment and its estimated expense total is a convenience hint, **not** an accounting record.

Only preview code has been tested in CI. **Test with disposable screenshots first**, particularly folder moves. For detailed known limitations and acceptance checks, see [Screenshot Intelligence](docs/SCREENSHOT_INTELLIGENCE_V04.md).

## Build

Install JDK 17, Android SDK 36 and Gradle 8.13, then:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`


## v0.5 Gallery-first selection
- Select up to 100 photos from Android Photo Picker or use another compatible gallery app.
- Folder picker remains for entire-album workflows. Gallery URIs are **read only**; can share without copying.
- To **move without copies**, grant access to the physical source folder. Selected images are matched on exact filename + size + SHA-256 of contents against SAF file entries. Unmatched originals never move.
- Choose category-wise subfolders or one named collection, and review a confirmation before moving.
- Multi-photo selection applies to filtered photos and to both Share / Create folders.
- Android does not guarantee a third-party gallery will support multi-select or source-folder selection; available pickers vary by device.

## v0.6: Photos-first search results
The post-scan screen now centers on a compact search bar and date-grouped 3-column image grid.
Advanced EXIF, GPS, OCR, and smart-tag filters remain available from the filter sheet.
Sharing or moving original photos requires an explicit selection; the previous action-on-all-visible
behavior was removed. See `docs/PHOTOS_FIRST_V06.md` for real-device acceptance checks.
