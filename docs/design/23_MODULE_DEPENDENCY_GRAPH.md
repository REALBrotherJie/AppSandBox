# Module Dependency Graph

```text
Storage -----> Package Registry -----> Resolver
   |                 |                   |
   v                 v                   v
Instance --------> Identity --------> Permission
   |                 |                   |
   +------> Code ---> Resources --------+
                  \       /
                 Application
                     |
             Component Runtime
          /       |       |       \
      Activity Service Receiver Provider
          \       |       |       /
               System Bridge
                     |
              Host / Android APIs

Process Runtime contains the above runtime state when a separate process is introduced.
Native Runtime is consumed by Code and Process.
Multi-instance coordinates Storage, Identity, Permission, and lifecycle.
```

Ordering constraint: Package/Storage -> Code/Resources -> Application -> component runtimes -> system bridges. Identity/Permission are cross-cutting prerequisites. Process placement must be decided before untrusted component execution. No Activity implementation may bypass Package Registry and Instance state.
