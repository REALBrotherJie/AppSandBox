# Multi-instance Design

```text
GuestPackage: com.example.app
  revision-1: shared immutable APK/resources/code
  instance-A: data/runtime state
  instance-B: data/runtime state
  instance-C: data/runtime state
```

Package identity describes code and declared components. Instance identity describes user data, logical process state, permission decisions, task state, and runtime lifecycle.

`PROPOSED`: share immutable APK bytes and read-only derived artifacts only after digest verification. Do not share mutable code caches or native extraction directories until class-loader/native experiments prove it safe. Every instance receives separate files, databases, shared preferences, cache, state, logical authorities, and resolver scope.

Open issues: account/token separation, external URI grants, notification channels, backup, encryption, and host resource limits.
