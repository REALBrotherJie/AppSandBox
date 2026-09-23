# ContentProvider Runtime Design

Providers combine authority resolution, process startup, Binder interfaces, URI permissions, lifecycle, and caller identity. `ActivityThread.installProvider`, AMS provider management, and `ContentResolver` are central platform paths.

## Authority

`PROPOSED`: keep guest authorities in an internal namespace:

```text
guest://<instance-id>/<declared-authority>/...
```

External Android clients must never reach a guest provider by simply claiming its original authority. Host-mediated crossing requires explicit grants and URI translation.

## Options

- A: logical provider inside the guest controller; calls stay in-process or sandbox-process.
- B: host-declared provider proxy with URI and Binder translation.
- C: real platform authority registration; requires system-visible package/provider state.

Recommend A first, B only for a narrowly defined compatibility boundary, and treat C as unavailable to a normal host app.

Risks include authority collisions, confused-deputy access, URI grant leakage, Binder object lifetime, provider startup before Application, and transaction identity. Each must be an experiment before implementation.
