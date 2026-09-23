# EXP-003B0-P Public-only Host LoadedApk Impact Result

## CURRENT STATUS

```text
PUBLIC-ONLY OBSERVATION COMPLETE
EXP-003B FULL APPLICATION GATE = NOT READY
HIDDEN OBSERVATION = DEFERRED (NOT APPROVED IN TASK-14)
```

No framework fields were read. No hidden API, Hook, Binder interception, JNI,
or native code was used. `Application.onCreate()` was not called.

## Process and Guest APK

```text
PID = 27406
run count = 1
Guest installed = false (pm path had no output before and after)
Build APK = test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk
Build SHA256 = 85f1cf6590b97b60ab09b4e55952e3df7a493e193a59a779756b24890cc33e0e
Imported SHA256 = 85f1cf6590b97b60ab09b4e55952e3df7a493e193a59a779756b24890cc33e0e
SHA match = true
Guest Application className = com.example.appsandbox.testguest.runtime.Exp003GuestApplication
```

The APK was imported as a new GuestStore revision:
`ecb25729-ecec-455b-9089-4b2c63be8521`.

## Creation result

Two Applications were created in one fresh process: one with Host
`applicationContext` as the Controlled Context base and one with Host
`MainActivity` as the comparison base.

```text
newApplication = PASS for both groups
constructor count = 2
attachBaseContext base = Exp003aControlledContext
onCreateCalled = false
factoryUsed = false
```

Logcat contained the expected public Instrumentation warning twice:

```text
Uninitialized ActivityThread, likely app-created Instrumentation,
disabling AppComponentFactory
```

This confirms the Guest manifest `appComponentFactory` was not used by the
app-created `Instrumentation`.

## Observation matrix

| Observation | base=Host Application Context | base=Host Activity | Classification |
|---|---|---|---|
| Guest Application class/loader | Guest class, DexClassLoader | Same | BASE_CONTEXT_DOMINANT |
| baseContext identity | Controlled Context | Controlled Context | BASE_CONTEXT_DOMINANT |
| packageName | Guest package | Guest package | BASE_CONTEXT_DOMINANT |
| Resources/assets | Guest resources/assets | Guest resources/assets | BASE_CONTEXT_DOMINANT |
| ApplicationInfo | Guest logical copy/data path | Same | BASE_CONTEXT_DOMINANT |
| filesDir | Guest instance path | Same | BASE_CONTEXT_DOMINANT |
| opPackageName | Host package | Host package | HOST_IDENTITY_REALITY |
| AttributionSource | Host package/UID | Host package/UID | HOST_IDENTITY_REALITY |
| applicationContext | Controlled Context | Controlled Context | BASE_CONTEXT_DOMINANT |
| PackageManager Guest query | NameNotFoundException | Same | DEFERRED |
| LayoutInflater context | Host Application | Host MainActivity | C0_DELEGATION_GAP |
| ActivityManager class | Host manager | Host manager | HOST_IDENTITY_REALITY |
| createConfigurationContext | Host ContextImpl/package | Host ContextImpl/package | C0_DELEGATION_GAP |
| component callbacks | Registration no exception | Registration no exception | DEFERRED |
| activity lifecycle callbacks | Registration no exception | Registration no exception | DEFERRED |

## Guest perspective versus Host perspective

- Guest code observed Guest package, Guest base Context class, Guest loader,
  Guest resources, Guest ApplicationInfo data path and Guest files path.
- Guest code observed Host op-package, Host AttributionSource, Host UID and
  Host process name.
- Guest code observed `applicationContextIsThis=false`; the application
  context was the Controlled Context, not the Application object.
- Host-side observation agreed with Guest-side observation for these values.
- LayoutInflater differed: application-context base produced an inflater whose
  context class was `android.app.Application`; activity base produced
  `com.example.appsandbox.MainActivity`.

## Host unaffected

```text
Host ApplicationContext/package = PASS
Host resource marker = PASS
Host loader cannot load Guest Application = PASS
Host Resources exact Guest marker visibility = false
Host Assets Guest visibility = false
System Resources Guest visibility = false
System Assets Guest visibility = false
Host survived missing-class/null-base/constructor errors = PASS
```

## Error tests

```text
Missing class = ClassNotFoundException
Null base = ClassCastException: ContextWrapper cannot be cast to ContextImpl
Constructor throws = IllegalStateException: EXP003B0 constructor failure
App survived = true
```

## Hidden items

All are:

```text
HIDDEN OBSERVATION = DEFERRED (NOT APPROVED IN TASK-14)
```

This includes `Application.mLoadedApk`, `LoadedApk.*`,
`ContextImpl.mPackageInfo`, ActivityThread package caches, and
`getPackageInfoNoCheck`. Public observations support the hypothesis that
base-context getters remain Guest-routed while framework-created managers and
derived contexts can expose Host semantics. They do not confirm the hidden
field values.

## API classification

All framework APIs used for creation and observation were public SDK APIs.
Reflection was used only to invoke Guest-owned `observe()` and read Guest-owned
test flags/classes; it did not access framework internals.

## Interpretation

Task-15 audit supersedes the original attribution: C0 does not override
getSystemService or createConfigurationContext. These observations demonstrate
delegation to the Host base, not evidence of LoadedApk state. Returning the
Controlled Context from getApplicationContext after Application attachment is
also a semantic gap: a Guest Application cast fails. C1 must bind the Guest
Application and propagate that binding to derived contexts. Hidden observation
is NOT NEEDED NOW; public wrapper behavior explains these observations.

Route A, accepting Host LoadedApk with a Controlled Context, is `LIMITED`, not
Rejected: simple Guest Application getters worked, but LayoutInflater,
derived Contexts, PackageManager, identity and service behavior remain
Host-sensitive. EXP-003B full gate is not ready, and EXP-003C must not start.
