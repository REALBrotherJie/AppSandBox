M8 EXECUTION RESULT = PARTIAL
M8 PLANNER REVIEW = PASS
M8 = CLOSED

remaining issue = MANIFEST_RECEIVER_TEST_STATE_VISIBILITY
classification = AUTOMATION_HARNESS_DEBT

API31/API36 independently proved:
ActivityThread.handleReceiver -> real Guest ManifestDemoReceiver.onReceive -> state mutation -> finishReceiver.
Dynamic Receiver, Ordered Broadcast, abortBroadcast, goAsync, concurrent ordered sessions, ContentProvider,
ContentProviderClient, two-instance Provider isolation, restart isolation, and M2-M7 regression passed.
