# M2 RESULT

STATUS = BLOCKED
branch = main; start HEAD = cf404336eef3e90af7220e4f63b47a320dafaa93
final HEAD = see focused commits below; Task-58 stash remains untouched
commits = M2 fixture/models/bootstrap/interceptor implementation; M2 evidence/result
git status = existing untracked historical task texts plus this authority/result until commit
major files = `test-guests/ZeroAdaptActivityApp`, `virtual/*`, `platform/*`, `runtime/*`, `StubComponents.kt`
architecture = installed package snapshot -> instance/dataRoot -> stub pN -> ActivityThread Handler transaction -> Guest DexClassLoader/Instrumentation -> native ActivityThread lifecycle
ZeroAdaptActivityApp = no AppSandbox metadata/SDK/contract; build PASS
API36 = Guest Application.onCreate and real Guest Activity.onCreate reached; failed in PhoneWindow/SettingsProvider attribution validation before Window/first frame
API31 = StubProvider/interceptor initialized; fixture reinstall was blocked by `INSTALL_FAILED_USER_RESTRICTED`, so no valid Guest run
real ordinary APK = not attempted after ZeroAdapt blocker; no false PASS
Guest Application = API36 real `com.example.zeroadapt.ZeroAdaptApplication`, onCreate executed
Guest Activity = API36 real `com.example.zeroadapt.LauncherActivity`, onCreate executed; onStart/onResume not reached
Window = not proven; failure occurs in Activity.attach/PhoneWindow before Window completion
stub mapping = logs show original `com.example.zeroadapt/.LauncherActivity` -> Host `.stub.P5StandardActivity` -> restored Guest component
hidden/non-SDK = VMRuntime hidden exemptions, ActivityThread.currentActivityThread/mH, Handler.mCallback, ClientTransaction fields, ActivityManager singleton, AssetManager.addAssetPath, ActivityThread/LoadedApk reflection
Binder = minimal IActivityManager caller-package proxy introduced; SettingsProvider ContentProvider attribution still rejects Guest package vs Host UID
risk tests = model tests and full JVM suite PASS; device R1 partial, R2-R7 not complete
limitations = LoadedApk `getPackageInfoNoCheck` signature differs on API36; full package/UID/Attribution/Resources bridge unresolved; native/filesystem/service/etc. out of scope
M3 blockers = finish API31 fixture installation, versioned LoadedApk bridge, ContentProvider/Attribution caller identity, Guest Resources/Window/ViewRoot, then rerun both devices
ZERO_ADAPTATION_ACTIVITY_PROVEN = NO
evidence = `build/reports/m2/7b670025-logcat-final.txt`, `build/reports/m2/emulator-5554-logcat-r6.txt`
