# TASK-36 Contract v2 Negative Compatibility Matrix

日期：2026-09-26

## Scope

This task validates the Contract v2 boundary against real APK artifacts. `GuestPackageReader` now performs the complete contract preflight while the APK is still in `GuestStore` staging:

- supported version and required layout/action metadata;
- layout/raw resource existence;
- bounded v2 action grammar with stable rejection codes;
- layout XML control IDs and Button/TextView types.

The rejection text starts with `Unsupported Guest: <reason-code>`, so host automation and UI consumers do not depend on platform parser wording. `GuestStore` already removes staging on parser failure; no Library record, committed artifact, or revision is created for rejected APKs.

## Matrix

| Fixture APK | Expected reason |
| --- | --- |
| unknownVersion | `unknown-version` |
| missingLayout | `missing-layout` |
| missingActionRaw | `missing-action-resource` |
| unknownField | `unknown-field` |
| unknownAction | `unknown-action` |
| duplicateBinding | `duplicate-binding` |
| invalidId | `invalid-id` |
| invalidStateKey | `invalid-state-key` |
| wrongButtonType | `wrong-button-type` |
| wrongTextViewType | `wrong-textview-type` |

The fixtures are independent Android APK flavors under `test-guests/ContractInvalidFixtures`; they are never installed as packages.

## Verification

Focused JVM coverage:

- `:app:testDebugUnitTest`
- stable reason mapping for metadata/resource failures;
- unknown fields/actions, duplicate bindings/actions, invalid IDs, and invalid state keys.

APK build coverage:

- all ten `ContractInvalidFixtures` debug flavors;
- existing `GuestTestApp` v2 and `IndependentGuest` v1 artifacts.

Device results:

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: all ten reason codes, no Library/revision/staging leak, v1/v2 remain usable, Guest packages not installed |
| 36 | `emulator-5554` | PASS: all ten reason codes, no Library/revision/staging leak, v1/v2 remain usable, Guest packages not installed |

Device command:

```text
.\scripts\task36-run.ps1 -Serial 7b670025
.\scripts\task36-run.ps1 -Serial emulator-5554
```

The script imports v1 and v2 first, creates one instance of each, then imports every negative fixture. After each rejection it asserts:

- the expected stable reason is visible;
- the Library still contains exactly the original v1/v2 revisions;
- no staging file remains;
- both original instances remain usable.

Raw ADB/UI output is written under `build/reports/task36/<serial>/`, which is ignored and is not part of the commit.

## Boundary

This task does not change the action/session/binder contract implementation, guest test applications, workspace launcher/activity, MainActivity, Guest Store, Instance Store, or `docs/Codex`. It validates import-time compatibility and rollback only. The result is a closed declarative contract matrix, not arbitrary APK/component virtualization.
