# Experiment Plan

Every experiment records Question, Hypothesis, Setup, Expected, Observed, Conclusion, and Architecture Impact. Results belong in a dated file under `docs/experiments/`; this milestone defines plans only.

| ID | Question | Gate |
|---|---|---|
| 001 | What metadata does `getPackageArchiveInfo` expose for uninstalled APKs across API 28-36? | Package schema |
| 002 | Can guest dex code load with a controlled public class-loader arrangement? | Code Runtime |
| 003 | Can guest assets/resources be isolated from host configuration/cache? | Resource Runtime |
| 004 | Which public APIs are needed to construct an Application-like lifecycle? | Application Runtime |
| 005 | Can a host-declared Activity boundary preserve task/back/result semantics? | Activity ADR |
| 006 | What service lifecycle restrictions apply on API 26, 31, and 34+? | Service support |
| 007 | Which broadcasts can be safely translated? | Receiver policy |
| 008 | Can logical provider calls enforce authority and URI grants? | Provider policy |
| 009 | Which manager APIs can be adapted without hidden APIs? | Bridge matrix |
| 010 | What process boundary contains guest crashes and static state? | Process ADR |
| 011 | How do ABI/native loading and JNI class-loader identity behave? | Native support |
| 012 | What storage and registry failures occur under interruption/concurrency? | Storage exit |

No experiment may alter system framework, use hook frameworks, bypass hidden APIs, or use a production guest without authorization.
