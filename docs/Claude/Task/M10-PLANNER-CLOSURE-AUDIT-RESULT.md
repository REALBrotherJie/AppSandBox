# M10 Planner Closure Audit Result
AUDIT_STATUS = FAIL; START_BRANCH = main; START_HEAD = 119fea60cfe2a1d0b850d1b55c3846c9975eb5fe
FINAL_IMPLEMENTATION_HEAD = 119fea60cfe2a1d0b850d1b55c3846c9975eb5fe; COMMITS = RESULT_COMMIT_ONLY
DEVICES = API31 7b670025 device; API36 emulator-5554 restored, boot_completed=1, SDK36, PackageManager responsive
EXACTLY_ONCE API31 = tx 0ce8f59e-bf1b-46eb-8347-a445fecded80; g5/g6 each queue=1 dispatch=1 completion=1 Guest counter=1; stale g5 rejected by g6
EXACTLY_ONCE API36 = tx 5676e43f-e1a4-4f9b-ae00-edb84849dde7; g5/g6 each queue=1 dispatch=1 completion=1 Guest counter=1; stale g5 rejected by g6
TRANSACTION_EXACTLY_ONCE_PROVEN = YES
WEBVIEW_INSTANCE0 = API36 audit-wv-i0 suffix 0a6a3e987427923b10cfd452; g30 pid4163 wrote INSTANCE0; g31 pid4230 read INSTANCE0
DELETE_SEQUENCE = FAIL: production deletion replied ok=false; VPROCESS_DEAD arrived exactly after the 5s termination wait expired
DEFECT = M10_IMPLEMENTATION_DEFECT: terminateInstance blocks CoordinatorProvider main thread while Binder death markDead is posted to that same main Handler
WEBVIEW_DELETE_CROSS_INSTANCE_NON_INTERFERENCE = FAIL; WEBVIEW_INSTANCE_LIFECYCLE_PROVEN = NO; WEBVIEW_STICKY_SLOT_LIFECYCLE_PROVEN = NO
M9_WEBVIEW_IO_NON_INTERFERENCE = FAIL (NOT RUN after mandatory STOP)
M2 = FAIL; M3 = FAIL; M4_RUNTIME = FAIL; M4_AUTOMATION_HARNESS = BLOCKED
M5 = FAIL; M6 = FAIL; M7 = FAIL; M8_RUNTIME = FAIL; M9 = FAIL (final matrix NOT RUN after mandatory STOP)
M2_M3_M4_M5_M6_M7_M8_M9_RUNTIME_REGRESSION = FAIL; M10_FINAL_SMOKE = NOT RUN
API31 = FAIL; API36 = FAIL; BUILD_GATES = NOT RUN after mandatory STOP
REMAINING = fix Coordinator termination wait off main callback path, then rerun WebView delete/recreate, M9 trace, full dual-device regression
M10_READY_TO_CLOSE = NO; M11_STARTED = NO; STOPPED_AFTER_M10_PLANNER_CLOSURE_AUDIT = YES
