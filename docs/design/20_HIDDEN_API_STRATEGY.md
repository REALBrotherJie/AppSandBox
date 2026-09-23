# Hidden API Strategy

The default policy is no hidden API access and no bypass library.

For each requirement, evaluate in order:

1. public SDK API;
2. documented AndroidX/API-compatible abstraction;
3. redesign that removes the dependency;
4. public Binder path used through its supported manager;
5. platform/system-app integration if the product scope changes;
6. only then record the requirement as blocked.

Potentially sensitive areas include `LoadedApk` construction, internal resource constructors, `ActivityThread` transactions, internal package records, and linker/class-loader internals. These are not assumed available merely because they exist in AOSP.

`PROPOSED`: maintain a per-release dependency ledger with class, method, reason, public alternative, test, and decision. No hidden API bypass is permitted as an implementation shortcut.
