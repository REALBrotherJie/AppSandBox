# Activity Runtime Decision Matrix

状态：PROPOSED，非最终 ADR。

## Preferred experimental route

`PROPOSED: Route G staged as C -> E -> G`

先由 Manifest Stub 获得真实 Host token/task/window，再由 API-specific client adapter 观察 Host transaction，恢复逻辑 Guest record 并实例化 Guest Activity，最后逐项增加 Intent/result/lifecycle/task translation。

## Secondary route

`PROPOSED: Route A Host Activity + Guest View`，作为稳定 fallback、negative control 和不要求 Activity identity 的产品路径。

## Rejected for first experiment

- Public-only direct Guest launch：PMS 没有 Guest ActivityInfo。
- Binder-only translation：可翻译请求，但不能 attach Guest Java object。
- Native/seccomp-only：不解决 ActivityInfo、LoadedApk 和 attach。
- One universal Stub：launchMode、theme、orientation、window flags 不同，契约不足。

## Deferred

权限、external/implicit resolver、split Activity、WebView/native SDK、predictive-back fidelity、shared-element、PiP/multi-window、recents polish、OEM adapters、process pool isolation。

| Dimension | A | C/E/G | H/I | J |
|---|---|---|---|---|
| Public API | HIGH | LOW | LOW | LOW |
| Token/task fidelity | LOW | MEDIUM/HIGH candidate | HIGH candidate | LOW |
| Guest object fidelity | LOW | HIGH candidate | HIGH candidate | LOW |
| API31-36 maintainability | HIGH | MEDIUM/LOW | LOW | LOW |
| Arbitrary APK suitability | LOW | MEDIUM candidate | MEDIUM candidate | LOW |
| First experiment value | HIGH control | HIGH | MEDIUM | LOW |

不使用虚假数值评分；candidate 必须以实验确认。
