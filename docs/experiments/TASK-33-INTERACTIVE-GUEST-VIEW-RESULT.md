# TASK-33 Interactive Guest View Result

日期：2026-09-26

## Contract

Contract v2 extends the existing metadata contract without replacing v1:

- `CONTRACT_VERSION=2`
- `VIEW_LAYOUT=<layout resource name>`
- `ACTION_SPEC=<raw resource name>`

The raw specification is bounded to 4096 characters and accepts only:

```text
schema=1
state=<text-view-id>|counter
action=<button-id>|counter.increment|counter
action=<button-id>|counter.reset|counter
action=<button-id>|counter.toggle|counter
```

Unknown versions, fields, actions, state keys, malformed or oversized identifiers, duplicate controls/actions, missing resources, missing views, and wrong control types fail closed. The model contains only strings/enums; it cannot name classes, reflection targets, intents, URIs, paths, scripts, or network requests.

## Runtime

`GuestTestApp` is v2 and declares three Guest-layout buttons plus a counter TextView. `IndependentGuest` remains v1 and continues static rendering with the existing Host counter controls. Host import validates the v2 raw resource, persists the contract version, and workspace revalidates the artifact and contract before inflation.

The binder resolves every declared ID and control type before installing any listener. Actions use `GuestViewSession` and the current instance root only, then refresh the Guest TextView and Host state summary. Binding failure enables no partial action set; execution failure disables the bound buttons and displays a concise error.

## Tests

JVM tests cover v1/v2 versions, valid parsing, unknown/malicious actions, unknown/oversized fields, invalid IDs/state keys, duplicate bindings/actions, missing or wrong control types, increment/reset/toggle persistence, restart, cross-instance/revision isolation, and deletion isolation.

## Device results

| API | Serial | Result |
| --- | --- | --- |
| 31 | `7b670025` | PASS: v2 Guest buttons, increment/reset/toggle, two-instance isolation, force-stop restore, delete isolation, v1 rendering/action, Guests not installed |
| 36 | `emulator-5554` | PASS: same workflow and assertions |

Raw UI/ADB output is ignored under `build/reports/task33/<serial>/`; no tracked device evidence was added.

## Verification and boundary

`:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, and `:test-guests:GuestTestApp:assembleDebug` passed. Guest Activity/Application lifecycle remains unexecuted; no attach, framework transaction, Binder interception, hidden API, hook, or native injection is used.

Known limitation: v2 supports only one integer counter state key and three Host-implemented actions. It is a declarative View contract, not arbitrary APK or component virtualization.
