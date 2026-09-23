# C1 system service requests

API31 WS-2 evidence: c1.txt and c1-read.txt under evidence/task15/7b670025.

| Requested name | Routing | Observation |
|---|---|---|
| layout_inflater | cloneInContext(C1), cached per C1 | Context identity PASS for all derivatives |

Other services remain delegated to the Host base and will be recorded if
requested. No interception, service replacement or Binder changes are used.
ContentResolver and PackageManager are separate getters, not service-name calls.
