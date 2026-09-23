# EXP-001 Result: Uninstalled APK Java Class Loading

## Question

Can an APK that is not installed in Android PackageManager be loaded from AppSandbox private storage with an Android official class-loader API, then be instantiated and invoked through reflection?

## Hypothesis

`DexClassLoader` can load ordinary guest dex classes from an APK file without PMS installation. This would prove only Level 1 code loading.

## Environment

- Date: 2026-09-23
- Device: connected Android device, serial `7b670025`
- Android version: 12
- API level: 31
- ABI: `arm64-v8a`
- AppSandbox compileSdk: 36
- AppSandbox targetSdk: 36
- AppSandbox version: `0.1.0`
- GuestTestApp package: `com.example.appsandbox.testguest`
- GuestTestApp versionCode: 1

## Setup

Created an independent Java GuestTestApp module at `test-guests/GuestTestApp`.

The Host `:app` module has no Gradle dependency on the Guest module and does not import either Guest class. The Host experiment is in `app/src/debug/java` and is invoked only through a Debug UI button using reflection.

Guest classes:

- `com.example.appsandbox.testguest.runtime.GuestProbe`
- `com.example.appsandbox.testguest.runtime.GuestHelper`

The Guest marker is:

```text
EXP001_GUEST_7f2d8e91-4d42-4aa4-9a91-1d4f8c3e6b27
```

## Build evidence

Both APKs built successfully:

```text
./gradlew :test-guests:GuestTestApp:assembleDebug :app:assembleDebug
BUILD SUCCESSFUL
```

Guest APK:

```text
test-guests/GuestTestApp/build/outputs/apk/debug/GuestTestApp-debug.apk
size: 7285 bytes
sha256: CCFA0F2C5D7EE93A835F6079B059D3739BC173613629FBD974AA9902A6D9B7D3
```

Host APK:

```text
app/build/outputs/apk/debug/app-debug.apk
sha256: 8D2672CEAB709CC439F7C63DAF0289E9D86CD5083604DCFD4521E7893E0DB05A
```

The Host APK binary does not contain the unique full Guest marker literal:

```text
HOST_MARKER_LITERAL_PRESENT=False
```

The Host build graph contains no `implementation(project(":test-guests:GuestTestApp"))` or equivalent dependency.

## Installation state

## SAF Import Compatibility

The original selector used `ACTION_OPEN_DOCUMENT` with:

```text
type = application/vnd.android.package-archive
```

On Xiaomi Mi 10 / Android 12 DocumentsUI, `GuestTestApp-debug.apk` was not visible. The selector was changed to:

```text
action = ACTION_OPEN_DOCUMENT
type = */*
EXTRA_MIME_TYPES = application/vnd.android.package-archive, application/octet-stream
CATEGORY_OPENABLE = true
```

The application now treats the MIME and filename as hints only. It copies the selected URI to a cache temporary file, obtains display-name/type metadata, validates the file with `PackageManager.getPackageArchiveInfo()`, and only then creates a GuestStore record and formal `base.apk`. Validation failure deletes the temporary file and does not publish a Guest record.

With the updated Host, DocumentsUI exposed and allowed selection of:

```text
GuestTestApp-debug.apk
```

The imported package shown by the Host was:

```text
packageName=com.example.appsandbox.testguest
version=1.0 (1)
label=GuestTestApp
```

The exact URI/provider MIME was not captured in the saved logcat for this run. This is a logging evidence gap, not a validation gap.

Before installation attempt:

```text
adb shell pm path com.example.appsandbox.testguest
```

Result: no package path/output. The Guest APK was not installed.

Follow-up verification after the user-installed Host APK:

```text
adb shell pm path com.example.appsandbox
package:/data/app/~~YlUKRMHwT5We9a-UBe_BPA==/com.example.appsandbox-7x4msVtwVb-UOkCVonzkOA==/base.apk

adb shell pm path com.example.appsandbox.testguest
```

The Host package is installed and the Guest package still has no package path.

The Host was launched successfully and the existing `Import APK` button opened DocumentsUI. The Guest APK was pushed only as a selectable file:

```text
/sdcard/Download/GuestTestApp-debug.apk
```

DocumentsUI did not expose that APK in the current `application/vnd.android.package-archive` picker list on this device. The APK therefore was not selected, was not copied into AppSandbox private storage, and the Debug runner was not executed. The experiment was stopped without bypassing SAF or creating a second storage path.

The Host APK could not be installed because the device rejected ADB/package installation:

```text
Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]
```

The same result occurred with `adb install`, `adb install --no-streaming`, and device-side `pm install` after pushing the APK. The follow-up user installation removed that original blocker, but the APK picker visibility issue prevented import.

## Guest package state

The Guest remained uninstalled after import and execution:

```text
adb shell pm path com.example.appsandbox.testguest
```

Result: no output.

The final verified imported APK path used by the runner was:

```text
/data/user/0/com.example.appsandbox/files/guests/dc0fb5fe-272d-45fb-b798-05439a951ed7/base.apk
```

It had size `7285` bytes and SHA-256:

```text
ccfa0f2c5d7ee93a835f6079b059d3739bc173613629fbd974aa9902a6d9b7d3
```

## Intended procedure

The implemented Debug runner performs the following after a Guest APK has been imported by the existing Host flow:

1. Record guest ID, path, existence, readability, size, SHA-256, and package name.
2. Query PackageManager to confirm the Guest package is not installed.
3. Use the Host ClassLoader to load `GuestProbe`; expected result is `ClassNotFoundException`.
4. Create a `DexClassLoader` using the imported APK path and App private `codeCacheDir`.
5. Load `GuestProbe` by string name.
6. Verify `clazz.classLoader === guestClassLoader`.
7. Construct the class and invoke `ping(String)` by reflection.
8. Confirm `GuestHelper` resolves and the unique marker is returned.
9. Repeat with a second loader for the same APK.
10. Test a wrong class name and a deliberately corrupt APK.

## Observed results

| Check | Result |
|---|---|
| Guest module independent from Host | Confirmed by Gradle structure |
| Guest APK builds | Confirmed |
| Host APK builds | Confirmed |
| Guest marker literal absent from Host APK | Confirmed |
| Host package installed in follow-up | Confirmed |
| Guest package installed | Not installed |
| Existing Import UI launched | Confirmed |
| Guest APK selectable through current SAF filter | Not confirmed |
| Host negative class-loader check | Not executed on device |
| Guest DexClassLoader load | Not executed on device |
| GuestHelper resolution | Not executed on device |
| reflection invocation | Not executed on device |
| wrong class handling | Implemented, not device-executed |
| corrupt APK handling | Implemented, not device-executed |
| two-loader type identity | Implemented, not device-executed |

### Device execution evidence

The completed device run logged:

```text
guestInstalled=false
hostLoader=dalvik.system.PathClassLoader
hostParent=java.lang.BootClassLoader
hostClassLoader->...GuestProbe=NOT FOUND
guestLoaderA=dalvik.system.DexClassLoader
guestParentA=dalvik.system.PathClassLoader
clazzLoaderMatchesA=true
constructorA=OK
methodLookupA=ping
invocationA=guest:exp001-A:EXP001_GUEST_7f2d8e91-4d42-4aa4-9a91-1d4f8c3e6b27
guestHelperA=RESOLVED
guestLoaderB=dalvik.system.DexClassLoader
clazzLoaderMatchesB=true
constructorB=OK
methodLookupB=ping
invocationB=guest:exp001-B:EXP001_GUEST_7f2d8e91-4d42-4aa4-9a91-1d4f8c3e6b27
guestHelperB=RESOLVED
classA==classB=false
classA.classLoader=dalvik.system.DexClassLoader
classB.classLoader=dalvik.system.DexClassLoader
classA.isAssignableFrom(classB)=false
classLoaderA==classLoaderB=false
wrongClass=ClassNotFoundException
corruptApk=java.lang.ClassNotFoundException
conclusion=CONFIRMED
```

## Logs

The Debug runner uses:

```text
AppSandbox.Exp001
```

It records APK evidence, loader classes/parents, class-loader ownership, reflection stages, marker result, negative controls, and failure exceptions.

## Conclusion

**CONFIRMED**

Confirmed:

- An independent Guest APK can be built without a Host module dependency.
- The Host APK does not contain the Guest class or unique marker literal.
- The Guest package remained uninstalled.
- The Host-side EXP-001 implementation uses public `DexClassLoader` and reflection only.

The run confirmed the Level 1 code-loading hypothesis. The current evidence does not extend beyond ordinary dex class loading and reflection.

## Architecture impact

The Level 1 Code Runtime ADR is [ADR-0004](../adr/ADR-0004-GUEST-DEX-CLASS-LOADING.md). EXP-002 remains a separate, unexecuted experiment.
