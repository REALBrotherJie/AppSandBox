# Activity Carrier Route Comparison

状态：PROPOSED，未实现、未验证。

| Route | Public only | Hidden | Hook | Binder | Native | Real token | Guest Activity class | Compatibility |
|---|---|---|---|---|---|---|---|---|
| A Host Activity + Guest View | YES | NO | NO | NO | NO | Host only | NO | L0, stable |
| B Host carrier + delegate | YES | NO | NO | NO | NO | Host only | optional non-Activity | L1, medium |
| C Manifest Stub substitution | PARTIAL | LIKELY | OPTIONAL | NO initially | NO | Host Stub | possible | L3/L4 candidate |
| D Instrumentation interception | NO | LIKELY | YES | NO | NO | Host Stub if C | YES | L2/L3, sensitive |
| E ActivityThread/ClientTransaction | NO | YES | YES | NO | NO | Host Stub | YES | L3/L4 candidate |
| F ATMS/Binder proxy | NO | MAYBE | YES | YES | OPTIONAL | Host Stub | not alone | translation only |
| G Stub + Binder + client restore | NO | YES | YES | YES | OPTIONAL | Host Stub | YES | highest candidate |
| H LoadedApk/ContextImpl integration | NO | YES | OPTIONAL | NO | NO | Host Stub | YES | high maintenance |
| I Xposed-style interception | NO | YES | YES | OPTIONAL | NO | depends on C/E | YES | deployment constrained |
| J native Binder/seccomp | NO | MAYBE | NO/YES | YES | YES | Host Stub | no, needs Java restore | specialized |

| Route | API sensitivity | OEM sensitivity | Complexity | Multi-instance | Task fidelity | Window fidelity | Play suitability |
|---|---|---|---|---|---|---|---|
| A | LOW | LOW | LOW | HIGH | LOW | LOW | HIGH |
| B | MEDIUM | LOW | MEDIUM | MEDIUM | LOW | LOW | HIGH |
| C | HIGH | MEDIUM | HIGH | HIGH if durable | MEDIUM/HIGH candidate | MEDIUM/HIGH | MEDIUM |
| D/E | HIGH | HIGH | HIGH | MEDIUM | MEDIUM | MEDIUM | LOW/MEDIUM |
| F/G | HIGH | HIGH | VERY HIGH | HIGH only with records | HIGH candidate | MEDIUM/HIGH | LOW/UNKNOWN |
| H/I | VERY HIGH | HIGH | VERY HIGH | UNKNOWN | HIGH candidate | HIGH candidate | LOW |
| J | VERY HIGH | VERY HIGH | VERY HIGH | UNKNOWN | LOW without restore | LOW without restore | LOW |

Preferred experimental route: **G staged through C then E**. Secondary route: **A** as stable fallback/control. Binder/native routes are not first experiments because they do not create the Guest Java object/context by themselves.
