# Application Architecture Review

## Two different claims

1. **Java object claim:** instantiate a class extending `Application`.
2. **Android application claim:** provide a context, resources, paths, package behavior, lifecycle, services, providers, and process semantics that the Application expects.

The first is much weaker than the second.

## Common `onCreate` dependencies

| Operation | Likely dependency | Initial classification |
|---|---|---|
| SharedPreferences | instance storage/context | possible with adapter |
| database | instance storage/context | possible with adapter |
| PackageManager | guest registry/system bridge | compatibility required |
| ContentProvider | provider runtime/system | later |
| getSystemService | per-service policy | service-specific |
| registerReceiver | receiver runtime/host policy | later |
| startService | service runtime/AMS policy | later |
| native library | native/process model | later |
| WorkManager | installed package/background constraints | likely unsupported initially |
| Firebase | package identity, services, network/install assumptions | separate compatibility case |
| WebView | process/resources/provider/platform integration | high risk |

## Compatibility levels

- A0: constructor only.
- A1: `onCreate` with storage/resources and no external service assumptions.
- A2: common local persistence and package queries.
- A3: selected host-mediated services.
- A4: provider/network/native frameworks.
- A5: lifecycle parity with system components.

`PROPOSED`: target A1 before claiming Application Runtime. A HelloWorld success must not be generalized to third-party Apps.

