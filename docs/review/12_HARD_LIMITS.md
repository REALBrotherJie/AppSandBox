# Hard Limits

| Capability | Classification | Reason |
|---|---|---|
| independent Linux UID | Impossible without OS modification | UID assigned by platform/install model |
| independent SELinux domain | Impossible without OS/platform support | policy/domain is system enforced |
| independent kernel namespace | Impossible for ordinary app | requires privileged kernel/container support |
| complete PMS installation state | Impossible without system integration | PMS owns package records/indexes |
| signature-level permission | Impossible to grant locally | signature and system policy are platform-owned |
| some system-only APIs | Impossible or blocked | caller identity/privilege required |
| hardware-backed independent identity | Unknown/blocked | Keystore/attestation are system/security-bound |
| per-instance files/preferences | Possible with degraded semantics | host-private path mapping |
| logical package queries | Possible with degraded semantics | runtime-owned registry |
| selected notifications/storage APIs | Possible through host capability | host attribution remains |
| guest UI as host surface | Possible with degraded semantics | host owns task/window |
| native code loading | Possible with degraded semantics | host process/native state |
| full native Activity semantics | Unknown/likely blocked for normal host | system task/token cooperation |

Hard limits must be visible in product claims and compatibility reports.
