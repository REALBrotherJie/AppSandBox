# ADR-0005: Broad SAF Selection with Structural APK Validation

Status: Accepted

## Context

Some DocumentsProviders or ROM DocumentsUI implementations do not expose an APK under `application/vnd.android.package-archive` even when the file is a valid APK.

## Decision

Use `ACTION_OPEN_DOCUMENT` with `CATEGORY_OPENABLE`, `type="*/*"`, and APK/octet-stream MIME hints. Treat provider MIME and filename as non-authoritative. Copy the selected URI to a temporary private file, validate it with `PackageManager.getPackageArchiveInfo()`, and publish it to GuestStore only after successful parsing.

## Consequences

Valid APKs with inaccurate provider MIME can be selected. Text files, renamed ZIPs, and corrupt APKs are rejected before formal GuestStore publication. Temporary files are deleted on both validation failure and completion.

## Risks

Broad selectors expose more user files in the picker. The app must continue to avoid logging file contents and must retain clear user-facing rejection errors.

## References

- `app/src/main/java/com/example/appsandbox/MainActivity.kt`
- `app/src/main/java/com/example/appsandbox/packageinfo/GuestPackageReader.kt`
- `docs/experiments/EXP-001-RESULT.md`
