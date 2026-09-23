# EXP-002 Result: Uninstalled APK Resources Loading

## CURRENT STATUS

```text
Legacy = INVALIDATED / REJECTED
Option A = REJECTED (SHARED ASSETMANAGER MUTATION)
Option B API31 = CONFIRMED (FRESH PROCESS, TESTED DEVICE)
```

Only Option B is a formal candidate. The remainder of this file preserves
historical evidence and must not be read as approval of Legacy or Option A.

## HISTORICAL RESULTS

## A. EXP-002 Historical Status

PARTIALLY CONFIRMED

The public `ResourcesLoader` / `ResourcesProvider` path successfully resolves
Guest numeric resource IDs and reads string, raw, asset, color, drawable, and
configuration-qualified values from an uninstalled APK. Layout XML access is
not available on the tested Xiaomi Mi 10 / Android 12 / API 31 path:
`getLayout()` and `getXml()` both reject the Guest layout ID with
`Resource ID #0x7f040000 type #0x12 is not valid`.

## B. Latest Guest APK

```text
Build APK path = D:\WorkSpace\Android\MySelf\AppSandBox\test-guests\GuestTestApp\build\outputs\apk\debug\GuestTestApp-debug.apk
Build size = 13347 bytes
Build SHA256 = 125c7c57d575f1d7d8bb84a9d83a6cd26679bae696d6ffad91e75226b2f109b5
Imported APK path = /data/user/0/com.example.appsandbox/files/guests/b238c693-816e-48c9-bcb3-116fe72b8378/base.apk
Imported SHA256 = 125c7c57d575f1d7d8bb84a9d83a6cd26679bae696d6ffad91e75226b2f109b5
SHA_MATCH = true
versionCode = 1
```

## C. Guest Package State

```text
adb shell pm path com.example.appsandbox.testguest
```

No output. The Guest package remained uninstalled.

## D. Resource API

| API | Result |
|---|---|
| `ResourcesProvider.loadFromApk(ParcelFileDescriptor)` | PUBLIC, API 30+, works |
| `ResourcesLoader` | PUBLIC, API 30+, works |
| `ResourcesLoader.addProvider` | PUBLIC, API 30+, works |
| `Resources.addLoaders` | PUBLIC, API 30+, works |
| `Resources` | PUBLIC, works |
| `AssetManager` | PUBLIC object returned by `Resources`, works for assets |
| `DexClassLoader` | PUBLIC, runtime Guest ID bridge |

No hidden API, hidden `AssetManager` path, hook, Binder interception, JNI,
native code, Context virtualization, or LayoutInflater was used.

## E. Resource IDs

`aapt2 dump resources` and the Guest runtime ID bridge returned matching IDs:

| Resource | APK table ID | Guest bridge ID | Match |
|---|---:|---:|---|
| `exp002_color` | `0x7f010000` | `0x7f010000` | true |
| `exp002_drawable` | `0x7f020000` | `0x7f020000` | true |
| `exp002_test_layout` | `0x7f040000` | `0x7f040000` | true |
| `exp002_payload` | `0x7f050000` | `0x7f050000` | true |
| `exp002_collision` | `0x7f060000` | `0x7f060000` | true |
| `exp002_config_value` | `0x7f060001` | `0x7f060001` | true |
| `exp002_string` | `0x7f060002` | `0x7f060002` | true |

The Host only loads `Exp002ResourceIds` through a Guest `DexClassLoader` and
reflection. It does not compile against Guest `R`.

## F. Resource Metadata

For `guestStringId = 0x7f060002`:

```text
getResourcePackageName = com.example.appsandbox.testguest
getResourceTypeName = string
getResourceEntryName = exp002_string
```

Equivalent package/type/entry metadata was confirmed for raw, color, drawable,
layout, configuration, and collision IDs.

## G-K. Resource Values

```text
String:
expected = EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10
actual   = EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10
match    = true

Raw:
expected = EXP002_RAW_5c9a8e21-6f34-4b7d-91c2-8e0f3a6d4b11
actual   = EXP002_RAW_5c9a8e21-6f34-4b7d-91c2-8e0f3a6d4b11
match    = true

Asset:
expected = EXP002_ASSET_b7e3d902-1a64-4c8f-95d0-2e6b4a1c8f33
actual   = EXP002_ASSET_b7e3d902-1a64-4c8f-95d0-2e6b4a1c8f33
match    = true

Color:
ID       = 0x7f010000
expected = 0xff12ab34
actual   = 0xff12ab34
match    = true

Drawable:
class  = android.graphics.drawable.GradientDrawable
loaded = true
```

## L. Layout

```text
resource ID = 0x7f040000
metadata = package=com.example.appsandbox.testguest,type=layout,entry=exp002_test_layout
getLayout = failed: Resource ID #0x7f040000 type #0x12 is not valid
getXml = failed with the same exception
XML parser obtained = false
root tag = unavailable
inflate attempted = false
```

Inflation was not attempted because it is outside the Context boundary. This
is a real limitation of the tested Provider path, not a Host crash.

## M. Configuration

```text
same ID = 0x7f060001
default value = EXP002_DEFAULT
alternate configuration = landscape
alternate value = EXP002_LANDSCAPE
qualifier matched = true
```

## N. Collision

```text
Host ID = 0x7f0e0010
Guest ID = 0x7f060000
Host value = HOST_COLLISION
Guest value = GUEST_COLLISION
isolated = true
```

## O. `getIdentifier()` Investigation

```text
Form 1: getIdentifier("exp002_string", "string", "com.example.appsandbox.testguest") = 0
Form 2: getIdentifier("com.example.appsandbox.testguest:string/exp002_string", null, null) = 0
Form 3: getIdentifier("string/exp002_string", null, "com.example.appsandbox.testguest") = 0
```

```text
Numeric ID access works = YES
Name-to-ID lookup works = NO
```

The loaded provider understands the numeric ID and resolves its value and
metadata, but does not provide name-to-ID lookup through `getIdentifier()` in
this configuration.

## P. Error Tests

```text
Missing = Resources.NotFoundException: String resource ID #0x0
Wrong type = Resources.NotFoundException for Drawable using string ID
Corrupt APK = IOException during ResourcesProvider.loadFromApk
App survived = true
```

## Q. Two Resources

```text
resourcesA == resourcesB = false
assetManagerA == assetManagerB = true
A value = EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10
B value = EXP002_STRING_1d1c4b6a-87e2-4f31-a9d0-3a6b2e7c9f10
```

## R. Android 9/10 Status

```text
Android 9/10 compatibility path = RESEARCH NEEDED
```

`ResourcesLoader` / `ResourcesProvider` is an API 30+ path. No API 28/29
fallback was implemented.

## S. Architecture Interpretation

The confirmed core chain is:

```text
Guest code R.xxx
    -> compiled numeric resource ID
    -> Guest Resources loaded from private base.apk
    -> resources.arsc resolution
    -> correct value / metadata
```

This is more important than `getIdentifier()` for normal compiled Guest code.
The experiment does not confirm Guest Context, Theme semantics, Activity
resources, LayoutInflater compatibility, Application, or automatic
configuration propagation.

## T. ADR

ADR-0006 is not created because layout XML access is still failing on the
tested API 31 Provider path.

## U. Next Step

Ready for EXP-003: NO

Reasons:

1. Guest layout XML access remains unresolved.
2. API 28/29 compatibility remains unresearched.
3. `getIdentifier()` name lookup is unavailable for this provider configuration.
4. Guest Context and LayoutInflater are outside EXP-002.

## Post-experiment audit and re-verification

The prior result used:

```text
Resources(activity.resources.assets, ...)
+ ResourcesLoader
```

That was a Host-based merged resource space, not an isolated Guest resource
space. Its namespace and layout conclusions are invalidated by experiment
design.

Current APK table collision evidence:

```text
Guest 0x7f040000 = layout/exp002_test_layout
Host  0x7f0e0030 = string/exp002_collision
Host  0x7f0e0031 = string/exp002_host_only
Guest 0x7f060000 = string/exp002_collision
```

The legacy diagnostic was rerun. It confirmed:

```text
base AssetManager = Host activity.resources.assets
guestAssets === hostAssets = true
Guest layout metadata = com.example.appsandbox.testguest/layout/exp002_test_layout
getLayout/getXml = failed
Guest sees Host-only resource = true
Guest sees Host-only asset = true
Host sees Guest-only asset = true
```

The exact legacy layout failure was not reported as a Host `bool` metadata
collision on this build; it remained a type validation failure for
`0x7f040000`. Therefore the specific collision attribution is **not confirmed**,
but the legacy isolation design is definitively invalid.

## Reverified Options

### Option A: System-only base plus ResourcesLoader (REJECTED)

```text
guestAssets === Host assets = false
guestAssets === System assets = true
String/raw/asset/color/drawable = PASS
Layout getLayout = PASS, root=LinearLayout, child=TextView
Configuration = PASS
Guest sees Host resource/asset = false/false
Host sees Guest resource/asset = false/false
getIdentifier forms 1/2/3 = Guest numeric ID / Guest numeric ID / Guest numeric ID
cleanup = SUCCESS
```

The historical values were reproducible, but the architecture is rejected:
`Resources.getSystem().assets` is shared process System state and
`Resources.addLoaders()` mutates that shared AssetManager. Do not use this
path for Guest runtime construction or as a production fallback.

### Option B: PackageManager archive ApplicationInfo

```text
getPackageArchiveInfo = WORKS
sourceDir/publicSourceDir = private Guest base.apk
getResourcesForApplication = WORKS
String/raw/asset/drawable/layout = PASS
Layout root/child = LinearLayout/TextView
Guest sees Host resource/asset = false/false
Host sees Guest resource/asset = false/false
getIdentifier forms 1/2/3 = Guest numeric ID / Guest numeric ID / Guest numeric ID
```

Option B is a working API 31 candidate and does not require the deprecated
`Resources` constructor. API 28/29 remain **RESEARCH NEEDED**.

## Corrected Final Status

```text
Legacy = REJECTED
Option A = REJECTED
Option B API31 = CONFIRMED (fresh-process result recorded below)
API28/29 = RESEARCH NEEDED
```

## Task-11 Fresh Option B API31 Verification

```text
Process PID = 23101
Option B run count in process = 1
Legacy executed = false
Option A executed = false
Guest installed = false
Build/import SHA match = true
HOST_CHANGED = false
SYSTEM_CHANGED = false
Option B result = true
Conclusion = EXP-002 OPTION B API31 CONFIRMED
```

The Xiaomi Mi 10 / Android 12 / API31 run passed string, raw, asset, color,
drawable, layout XML, metadata, all three `getIdentifier()` forms, default
and landscape configuration, two-Resources behavior, missing ID, wrong type,
corrupt APK, Guest-to-Host and Host-to-Guest checks. `aapt2 dump resources`
listed `0x7f060002` but did not list the tested missing ID `0x7f06ffff`.
The corrupt APK was created and removed under Host `cacheDir/experiments/exp002`.

Pollution detection compared Host resource collision/Host-only resource and
asset observations plus System resource/asset Guest visibility before and
after the run. This supports only: `NO OBSERVED HOST/SYSTEM RESOURCE LEAKAGE
ON TESTED API31 PATH`.

The prior `PARTIALLY CONFIRMED` status was caused by the Host-based resource
construction and is superseded by this corrected experiment. No Guest Context,
Application, Activity, or EXP-003 work was performed.

## Deferred item resolved: layout inflation via C1

Task-15 WS-3 validated ordinary Guest layout and Guest theme-attribute inflation
on API31 through C1. See [C1 layout result](EXP-003C1-LAYOUT-INFLATE-RESULT.md).
This is a subsequent experiment, not a change to the historical EXP-002 run.
