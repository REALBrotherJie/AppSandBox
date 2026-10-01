# AppSandbox Test Apps

| App | Package | APK/module | Purpose | Milestones |
| --- | --- | --- | --- | --- |
| Demo1 | `dev.appsandbox.demo1` | `../AppSandBoxDemo/Demo1/app` | Core runtime, VPM, Activity, Java data | M2-M5 |
| Demo2 | `com.reel.demo2` | `../AppSandBoxDemo/Demo2/app` | Components and system APIs | Future Binder/Service/Receiver/Provider/WebView |
| Demo3 | `com.reel.demo3` | `../AppSandBoxDemo/Demo3/app` | JNI, native libraries and IO | Future Native Runtime |

Demo1 is a zero-adaptation external APK. It has no AppSandbox dependency, contract,
metadata, base class, instance API, Host IPC, or package-specific compatibility branch.
Demo2 is deferred as `DEMO2_DEFERRED_FOR_COMPONENT_RUNTIME`; Demo3 is deferred as
`DEMO3_DEFERRED_FOR_NATIVE_RUNTIME`.
