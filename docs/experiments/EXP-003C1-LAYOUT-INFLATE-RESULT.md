# C1 Guest layout inflation

API31 Mi 10: PASS. Evidence: evidence/task15/7b670025/layout.txt.
PID=23611, runCount=1. Guest uninstalled before/after.
Clean-built APK size=18840 bytes; build/imported SHA256 both:
92651aee64f0235fd3a6307e2ca14763c2dd22b8a35775ab641c74eef790c920.
New GuestStore revision=ee698d43-2c5a-4740-adcb-feb2dd4f0475.

| Test | API31 | API36 |
|---|---|---|
| Ordinary layout | LinearLayout/TextView, full Guest marker, C1 context PASS | Pending |
| Guest theme attribute | Text color ff12ab34 PASS | Pending |
| Landscape derived C1 | EXP002_LANDSCAPE, derived context identity PASS | Pending |
| Host inflater negative | InflateException: Host selector resource collision, no Guest view | Pending |

No hardcoded color was substituted into layout inflation. The expected color
is used only as the assertion. Theme is supplied by Guest Resources.
The C1 write/read matrices were rerun for this revision in fresh PID 21684/22594,
all checks PASS. Gate for ordinary layout=true.
