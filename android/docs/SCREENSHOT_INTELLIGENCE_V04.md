# PhotoFlow Android v0.4 — Screenshot Intelligence

## What this release adds

- Bundled, on-device ML Kit Latin-script text recognition (no image upload and no INTERNET permission).
- OCR candidates limited to screenshot/transaction filenames and portrait PNG/JPEG/WebP lacking camera EXIF; camera shots are normally skipped for performance.
- Bounded bitmap decode (~2100 px maximum side for OCR candidates), native Android orientation handling, and optional grayscale contrast retry if OCR finds fewer than 3 lines.
- Pure Kotlin payment parser for Google Pay, PhonePe, Paytm, Amazon Pay, BHIM, and generic UPI screenshots.
- Payment amount (INR), direction, status, payee name, masked UPI handle, last four reference digits, confidence/review status.
- Independent screenshot & payment screenshot move categories; original files are moved only after explicit user confirmation, never copied or zipped.
- Searchable virtual tags for payment provider, payee, amount, status, date, original EXIF GPS and scene tags; one image can belong to multiple virtual collections with zero file copies.
- Payment app and payment direction filter chips, review detail card and outgoing-successful amount estimate. Income/failed/pending/uncertain values are excluded from the estimate.
- Extra confirmation before sharing original financial screenshots with third-party apps.
- Screenshot creation date from common screenshot filename patterns, labeled as `Screenshot filename` rather than EXIF capture timestamp.

## Strong limitations

1. OCR-extracted data is **unverified**: a screenshot can be edited, mislabeled or duplicated, and OCR can misread currency or payees. Never rely on totals as a financial statement.
2. The app does not connect to a bank, UPI app, financial account or payment provider; it cannot verify transactions or payment statuses.
3. Only Latin-script text recognition is bundled. Devanagari-only receipts and heavily stylized interfaces need future models/QA.
4. `PaymentProvider` is inferred only from visible OCR branding or generic UPI cues, not from app origin or screenshot's process metadata.
5. No recipient/UPI account number or complete transaction reference is written to tags or logs. Payee names and detected amount remain **in memory for the active scan**, not a remote service.
6. Sharing originals shares everything visible in the screenshot, including personal/payment details. A warning is shown; redaction is not performed.
7. A physical file has one destination folder. `Mumbai`, `Evening`, `Google Pay`, and payee names are virtual tags, not duplicated folders.
8. Folder moving is governed by Android SAF provider semantics. Cross-volume unsupported moves fail gracefully with originals left untouched.
9. Screenshot dates inferred from filenames are not cryptographically trustworthy; original EXIF is used where present.

## Manual acceptance scenarios

- Screenshot_20261125_115821.png from GPay: classify `Payment screenshots`, record Google Pay and extracted tags; not `Landscapes`.
- PhonePe screenshot showing successful payment to a person: extract amount/direction/payee; filter app = PhonePe, direction = Paid; see selected original in share preview.
- PhonePe screenshot showing payment **failed** or **pending**: stays browsable but is excluded from estimated expenses.
- Incoming receipt (`received from`): searchable under Received, excluded from expenses.
- Cashback promo mentioning ₹500: must **not** create an expense or confirmed payment entry.
- Receipt without logo text: mark app `Unknown app` or `Other UPI`, never pretend Google Pay/PhonePe.
- Screenshot containing Mumbai address but no embedded GPS: location remains Unknown; payee `Mumbai Cafe` is **not** a location tag.
- Export destinations: category-specific folders created only after explicit confirmation; no ZIP, copy, or deletion fallback.
- Disable Screenshot OCR: retain filename screenshot detection; do not read financial text.
- Share a payment screenshot: confirm financial details exposure before Android share chooser.

## Validation

Pure Kotlin OCR parsing and screenshot-detection tests can run without Android. The full Android build and `testDebugUnitTest` run on GitHub Actions. Real-device OCR accuracy and SAF move behavior require device acceptance testing.
