# Identity Model

| Identity | Meaning | Can AppSandbox control? |
|---|---|---|
| Host packageName | installed application identity | no, except its own declared identity |
| Host UID/appId | Linux/kernel identity | no |
| Host PID | current process identity | no |
| Guest logical packageName | registry namespace | yes, logically |
| Guest instance ID | independent data/runtime identity | yes |
| Guest processName | logical label | yes, not kernel process name |
| Android userId | platform user/profile | no |
| signingInfo | APK evidence/metadata | inspect and validate; cannot install-trust it |
| attributionSource | framework attribution chain | only through public host APIs |
| install source | local provenance | record locally; cannot impersonate system installer |
| package path | guest archive path | yes, within host private storage |

`CONFIRMED`: UID and SELinux are kernel/framework-enforced; a host app cannot safely fabricate them. `PROPOSED`: expose a typed logical identity to guest code and keep Android identity in diagnostics. Never claim logical identity equals OS identity.

This project is not for bypassing security detection, DRM, banking controls, anti-cheat, account restrictions, or third-party authentication.
