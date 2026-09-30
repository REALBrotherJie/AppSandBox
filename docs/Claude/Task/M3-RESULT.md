# M3 RESULT
STATUS = PASS
start HEAD = `d7c844d`; final HEAD = result commit (see `git log -1`)
commits = `09c0107` runtime isolation/VPM; `2e0bd34` zero-adaptation PM probes; result commit
git status = only pre-existing untracked task/history docs; Task-58 stash retained
ClassLoader = SYSTEM parent-only; exact AppSandbox RUNTIME_BRIDGE parent-only; application/AndroidX/Kotlin/SDK GUEST-first with controlled fallback
splits/diagnostics = base+split dex path and native path supported; debug `CLASSLOAD` records domain, loader, source, decision
Momo before = mixed Guest `androidx.activity.a` + Host `ComponentActivity$3` caused `IllegalAccessError`
Momo after = both classes loaded guest-first from Momo APK; next blocker `UnsatisfiedLinkError: libmahoshojo.so`
Registry = persistent package metadata owner initialized in `:vs`; package/components/source/splits/signing/permissions separate from instance/UID/slot/dataRoot
VPM = per-instance sanitized query view with stable negative Virtual UID and explicit VIRTUAL/SYSTEM/HOST/DENY visibility routing
Guest PM path = `ApplicationPackageManager -> ActivityThread.sPackageManager -> IPackageManager proxy -> VirtualPackageManagerService`
supported = package/application/activity/service/receiver/provider info, resolve/query activities, packagesForUid, permission, installed package/app visibility
API31 = PASS: self PM/signing/implicit/not-found/visibility; Application/Activity/ViewRoot/first frame/click/Back PASS
API36 = PASS: same PM matrix and full M2 Activity regression PASS
two instances = same package metadata/source; distinct negative Virtual UID, slot/process/envelope/dataRoot; registry survived `:vs` restart
hidden/Binder = new hidden `ActivityThread.sPackageManager`; new dynamic `IPackageManager` proxy; existing IAM/IContentProvider bridges retained
platform = reflection routes API31/API36 IPM signatures; framework flag overloads selected by API; no fixture/runtime SDK coupling
risks = R1-R7 PASS; protected runtime namespace cannot be guest-overridden; unknown/Host packages denied, required system packages passthrough
GUEST_CLASSLOADER_ISOLATION_PROVEN = YES; VIRTUAL_PACKAGE_MANAGER_FOUNDATION_PROVEN = YES; M2_REGRESSION = PASS; M3_READY_TO_CLOSE = YES
