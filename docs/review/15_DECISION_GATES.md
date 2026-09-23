# Decision Gates

## Gate 1: Code

Input: EXP-001. Pass: Level 1 class loading is repeatable. Fail: stop Runtime work and redesign loader/path assumptions.

## Gate 2: Resources

Input: EXP-002. Pass: isolated basic resource reads. Fail: do not build Application/UI around unproven resources.

## Gate 3: Application

Input: EXP-003. Pass: controlled Application subset with no leakage. Fail: narrow Context contract or stop.

## Gate 4: UI

Input: Level 6 host Activity plus guest View. Pass: resource/theme/event behavior. Fail: defer Activity.

## Gate 5: Activity

Input: strategy-specific system cooperation experiment. Pass: only the tested subset of task/back/result/configuration/process-death semantics. Fail: retain host-surface UI only.

## Gate 6: System compatibility

Input: service-by-service matrix. Pass: explicit policy and tests. Fail: deny unsupported calls; never replace uncertainty with interception.

No gate authorizes the next gate automatically. Each result requires an ADR or an update to an existing ADR.
