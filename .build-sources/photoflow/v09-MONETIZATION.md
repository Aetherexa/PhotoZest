# PhotoFlow — monetization design draft

This is **not a purchasable release**. The v0.9 code introduces a typed policy and unit tests, but does not activate paid tiers, charge customers, or silently alter existing v0.8 users.

## Tier matrix

| Tier | Images per scan | Categories | Search | Metadata and GPS | OCR | Additional tags |
|---|---:|---:|---|---|---|---|
| Free | 100 | 5 core | None | No exposed metadata editing | None | None |
| Plus | 300 | 7 | Categorized | No editing | None | None |
| Pro | 500 | 15 | Structured/category | Metadata view/edit; GPS sorting | Basic, e.g. amount/provider/payee | Up to 30 per image |
| Max | No plan cap | No plan cap | Full structured/semantic/LLM | Full | Advanced | No plan cap |

Category cap excludes the mandatory **Needs Review** safety bucket. Existing classification currently has only eight meaningful built-in primary categories; the Pro/Max custom-category feature must be added before promising 15/unlimited in the store.

The scan limits apply **per batch**; do not restrict lifetime rescans or lock users out of their own processed photos.

Prices and cadence must be finalized. User proposed **Plus at $2.50** but did not indicate monthly vs lifetime; Pro/Max are not yet priced. Proposed figures are not Google Play product prices.

## Non-negotiable engineering requirements
- One free app with in-app purchase entitlements, **not four independent installs**. Keep the compact architecture-specific APK package.
- Android: Google Play Billing, server verification of purchase tokens, acknowledge/restore, refunds, subscription cancellation, grace period and pending states. Default to Free unless a real verified entitlement is cached.
- No user-editable plan, preferences flag, query parameter, or dev switch may grant a paid entitlement.
- Cache verified entitlements for legitimate offline use, with conservative expiry and reconnection behavior.
- Perform authoritative checks in scan, search, metadata write, OCR, and selection/share actions as well as disabling locked UI; "hidden button" is not entitlement enforcement.
- On tier downgrade, preserve users' existing files, tags and virtual collections. Never move originals or discard previously tagged data simply because of billing state.
- In the Free tier, technical EXIF (orientation) can still be read internally for image correctness. Do not expose premium metadata editing/search.
- Max "unlimited" refers to usage eligibility, not unbounded memory: analyze in bounded chunks, persist progress, release bitmaps.
- Pro "more advanced AI" should improve multi-pass inference and quality decisions without shipping large separate ML models. Downloadable optional models must not break offline promises.
- A cloud LLM (if used) requires opt-in, clear data transmission disclosure, server-side metering, and a fair-use policy; don't offer unbounded paid API inference at a flat low price.
- Any edit to EXIF in the original photo requires compatible Android SAF write access and explicit user approval. Use sidecar tags by default.
- App store labels must distinguish **AI estimates** (not verified banking records) and specify what is included in "basic OCR" vs "advanced OCR".

## Remaining implementation
1. Choose the business model (monthly, one-time, annual, or trip passes) and confirm Pro/Max price points.
2. Build a polished subscription comparison screen consistent with the approved lavender home design.
3. Integrate billing and verification, restore purchase, support links and cancellation flow.
4. Wire TierCapabilities across ViewModel, all UI actions, and the underlying processing pipeline.
5. Add a shared entitlement API for the web SPA (if paid access crosses Android and web).
6. QA transitions, refund/expiry, offline entitlement handling, photo-count boundaries, performance and real-device SAF moves.
