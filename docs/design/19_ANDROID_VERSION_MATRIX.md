# Android Version Matrix

Legend: `P` public API path appears possible; `V` version-sensitive; `R` research required; `B` blocked for an ordinary host app.

| Area | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 |
|---|---|---|---|---|---|---|---|---|
| Package archive parsing | P | P | P | P | P | P | V | R |
| Activity launch | V | V | V | V | V | V | R | R |
| Class loading | V | V | V | V | V | V | R | R |
| Resources | V | V | V | V | V | V | R | R |
| Foreground service | V | V | V | V | V | V | R | R |
| Notifications | V | V | V | V | V | V | R | R |
| Storage/scoped storage | V | V | V | V | V | V | R | R |
| Provider/URI grants | V | V | V | V | V | V | R | R |
| Permissions/AppOps | V | V | V | V | V | V | R | R |
| Binder public paths | P | P | P | P | P | P | R | R |
| Hidden API availability | V | V | V | V | V | V | R | R |

Android 16 cells are intentionally `RESEARCH NEEDED`; this document does not claim unverified behavior. Every supported release requires device tests and API-specific exit criteria.
