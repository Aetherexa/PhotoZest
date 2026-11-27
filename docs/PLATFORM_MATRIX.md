# PhotoZest cross-platform contract

| Capability | Android | iOS preview | Browser |
|---|---|---|---|
| Source photos | Android gallery/folder (SAF) | User-selected PhotosPicker or authorized PhotoKit library | Local File input or folder selection |
| Face/group and scene recognition | ML Kit on-device | Apple Vision on-device | Browser model inference (optional model downloads) |
| Needs review for ambiguous labels | Yes | Yes | Yes |
| Metadata | EXIF reader | Date available for authorized library scan | EXIF reader |
| OCR | Native screenshot text recognition | Native Vision implementation staged behind no-entitlement guard | Optional browser Tesseract OCR |
| Physical originals move | Explicit native SAF same-provider move; field QA required | **Not supported** | **Not supported** |
| Organize into albums | Android native provider folders | References to existing Photos assets, explicit confirmation | Virtual collections only |
| Paid plans | Typed policy only, no billing | Typed policy only, no StoreKit | Not activated |
| App store readiness | Requires Google Play signing/QA | Requires TestFlight signing/device QA | Independent deployment required |

No photo upload service is introduced by the migration. First-run browser model and OCR asset downloads are not the same as uploading user photos.
