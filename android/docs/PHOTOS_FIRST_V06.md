# PhotoFlow v0.6 — Photos-first result UX

## Validated user feedback
PhotoFlow v0.5 displayed all metadata filters above the photos, photo cards repeated EXIF/OCR details,
and two sets of competing Share/Create folder actions. This turned results into a technical dashboard.

## Changes
- Dedicated compact results UI after scanning; onboarding hero and configuration remain only on the empty state.
- Search first; non-empty category shortcuts second; advanced filters in one on-demand bottom sheet.
- 3-column square crop photo grid, chronological month dividers, no metadata text on thumbnails.
- Tap tile for detailed review, long-press to select. Select/Done explicit mode.
- Share/Create folders actions only when selection mode is active; no implicit "all filtered" action.
- Selection count and select-all for visible results only.
- Filtering by metadata, GPS/city, time, payment provider/direction, and smart tags is unchanged.
- Original URI sharing and native move with user confirmation remain unchanged. No copy/delete fallback.

## QA required on real devices
1. Scan folder with 30+ photos; results show 3-column grid and compact search header.
2. Categories with zero matches do not appear in quick chips.
3. Search and filter without selecting anything: no move/share actions are enabled.
4. Long-press one photo: bottom action bar appears with 1 selected.
5. Clear/Done leaves all originals in their previous folders.
6. Inspect photo details: OCR, EXIF and custom tags remain available.
7. In advanced filters, combine city + month + payment provider; reset restores all.
8. Test Android font scaling, TalkBack and 360dp-width screens.
9. Confirm there are no ANRs while scrolling image-heavy galleries.

## Known gaps
- This is inspired by general visual-first gallery patterns, not a pixel copy of Google Photos.
- Current local index remains in memory; full text and semantic LLM search are separate roadmap work.
- App has no automated Compose UI screenshots; device-level visual accessibility and performance testing remain pending.
