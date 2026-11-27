# PhotoFlow 0.3 — EXIF-aware smart collections

PhotoFlow extracts image metadata using AndroidX ExifInterface before calculating on-device visual signals and selecting one **physical** primary category. A single photo can have many **virtual** tags without additional files or duplicated storage.

## Photo pipeline

1. Enumerate readable images from a Storage Access Framework source folder (up to 900).
2. Read embedded DateTimeOriginal / DateTimeDigitized / DateTime and GPS coordinates. Do **not** read current device location.
3. Resolve GPS into approximate city using a small offline list of 16 Indian city centers. Unknown/off-list locations must remain **Location unknown** until user correction. This is not polygon-accurate reverse geocoding; never claim to pinpoint administrative city limits.
4. Decode downscaled image once, run bounded tone analysis (brightness, contrast, clipping, sharpness, perceptual hash), run optional bundled ML Kit image-label and face models, infer one primary category and multiple search tags.
5. User can edit primary category, add/remove custom tags, and override city. Manual tags/city survive folder rescans and URI changes through an app-private filename+size+capture-time identity. This identity can collide for identical name/size/date photos; no database migration yet.
6. Combine category + place + capture-time + month + multiple AND smart tags + free-text search. Share up to 50 selected original URI references through Android share sheet with temporary read grants; sending apps may create their own copies.
7. If requested, move originals via `DocumentsContract.moveDocument` after explicit confirmation. Existing native move-only implementation **does not copy, delete, ZIP, or overwrite**. This physical sort uses the single primary category, not virtual tags or city.

## Limits and privacy

- Location and capture time are **unknown** if EXIF was stripped by a messenger/social app. Never guess city from what the landscape resembles or local device GPS.
- Date and time use camera wall-clock; camera time-zone settings can be wrong and EXIF often carries no offset. `Modified` EXIF time is labeled as such.
- Offline city matching is approximate and limited to 16 centers; missing/off-list GPS remains unknown, and city names can be overridden manually.
- No image bytes or GPS uploaded by PhotoFlow. The manifest has no INTERNET permission. Sharing **originals** can expose embedded GPS EXIF to recipients; user must consider location privacy before sharing.
- All labels are session values except manual city/custom tags stored in private app preferences. Automated extraction is rerun each scan.
- No storage copies are produced by PhotoFlow on share or moving; recipients may store shared copies.
- All photos are **moved**, never copied, only after a separate explicit move approval. Native provider may reject moves.

## Device test checklist

- [ ] Scan EXIF GPS photo in central Mumbai and central Pune; confirm correct chips and search.
- [ ] Scan files with GPS stripped; confirm unknown instead of false city.
- [ ] Scan camera EXIF time 09:00 and 18:00; confirm Morning and Evening.
- [ ] Scan screenshots without EXIF time and verify time unknown.
- [ ] Scan dark and overexposed photos; verify quality hints, not firm ratings.
- [ ] Apply city + Evening + Landscape orientation, share filtered originals; verify nonmatching images excluded.
- [ ] Rename override; re-scan same folder and check manual edits persist.
- [ ] Move 10 disposable photos and validate metadata survives and no duplicates appear.
- [ ] Check filtered gallery performance on 300+ images; thumbnails remain bounded.
- [ ] Share to target apps (WhatsApp, Google Photos, Nearby Share) and confirm content URI grants and metadata privacy notice.
