# TASK-43 Bounded Implicit Intent Resolver Result

Date: 2026-09-27

## Decision

Implemented a revision-bound implicit resolver that operates only on persisted
Guest metadata. It never registers a Guest package with PMS, calls ATMS/PMS,
starts a component, instantiates Guest code, or changes runtime IPC.

The supported filter subset is:

- exact action and request-category-subset matching;
- concrete MIME requests against exact or `type/*` filter MIME;
- URI scheme, case-normalized host, and exact path;
- integer priority in `[-1000, 1000]` and `autoVerify` retention.

APK binary `AndroidManifest.xml` is parsed in user space. Unsupported attributes
such as path prefixes/patterns, ports, queries, fragments, provider filters,
multi-data URI combinations, malformed MIME/URI values, and unsafe names fail
closed. Duplicate equivalent filters are canonicalized to one filter.

Candidates sort by priority descending, specificity descending, component class,
component type, then canonical filter key. External callers still require
enabled/exported/no-permission components; internal callers may use non-exported
components, while permission-bearing components remain denied.

## Verification

- `:app:testDebugUnitTest`: PASS, 85 tests, 1 existing skip.
- Host debug/release, GuestTestApp, IndependentGuest, ResolverFixtures v1/v2:
  PASS.
- IntentFilterFixtures v1/v2: PASS.
- Release manifest has no Task-43 debug automation entry.
- JVM tests cover action/category, MIME exact/wildcard, URI normalization and
  malformed escapes, priority/specificity ordering, duplicate filters,
  unsupported patterns, policy gates, revision deletion/binding, registry
  round-trip, and binary manifest parsing from a built APK.

API36 device validation: NOT EXECUTED. `emulator-5554` never became an ADB
device after controlled restart attempts of both API36 AVDs; no device PASS is
claimed. The API36 emulator processes were stopped and released. API31
`7b670025` was not touched.

Registry schema is now version 4 so persisted filters cannot be confused with
Task-40 schema-3 records. Existing explicit resolution and v1/v2 workspace
paths remain covered by the full JVM/build matrix; device regression is pending
only because the permitted API36 target was unavailable.
