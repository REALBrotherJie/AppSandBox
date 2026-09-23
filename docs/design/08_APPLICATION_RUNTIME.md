# Application Runtime Design

## Normal lifecycle

The platform process receives `bindApplication`; `LoadedApk.makeApplication` and `Instrumentation.newApplication` construct the application; `ContextImpl` supplies the base context; `Application.attach` precedes `Application.onCreate`; component transactions follow.

## Proposed guest lifecycle

```text
prepare instance
 -> construct guest loader
 -> construct guest resources
 -> construct guest context
 -> instantiate declared Application
 -> attach guest context
 -> call onCreate once
 -> publish GuestApplicationHandle
 -> dispatch components
 -> stop and release instance
```

The guest Application must receive:

- guest class loader;
- guest resources and configuration;
- logical guest packageName;
- instance-specific `filesDir` and related paths;
- a package manager facade with explicit scope;
- a content resolver facade or denied capability;
- a context that cannot accidentally return host paths.

`PROPOSED`: make lifecycle transitions explicit and idempotent. Do not call guest lifecycle methods from arbitrary UI callbacks. Fail the instance if construction or `onCreate` violates policy; preserve diagnostics without corrupting registry state.

`RESEARCH NEEDED`: whether common third-party Application initialization can function without platform-created `LoadedApk`, and which public APIs can replace each needed context operation.
