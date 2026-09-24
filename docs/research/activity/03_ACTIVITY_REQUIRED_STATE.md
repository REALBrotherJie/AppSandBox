# Activity Required State and Ownership

| State | Owner | Importance |
|---|---|---|
| ActivityRecord | SYSTEM_SERVER | identity, lifecycle, result and component policy |
| Task/back stack | SYSTEM_SERVER | affinity, launch modes, recents and back |
| activity/window token | HYBRID | created/validated by system_server, held by client Activity/Window |
| ActivityInfo/ApplicationInfo | system source, client copy | manifest and package behavior |
| LoadedApk | CLIENT | class loader, resources and Application |
| ContextImpl | CLIENT | token, display, override configuration and package |
| Window/WindowManager | HYBRID | visible surface and token-bound operations |
| Application/Instrumentation | CLIENT | app state and construction callbacks |
| Intent/Configuration | HYBRID | routing is system-mediated; client applies values |
| saved state/result | HYBRID | system transports, client serializes/restores |

An Activity-compatible Guest needs a system-approved ActivityInfo, accepted token, Activity-specific ContextImpl, matching LoadedApk/ClassLoader/Resources, Application, Window and recoverable logical record. A wrapper Context plus forwarded lifecycle calls is not this contract.

The normal `Activity.attach` path stores the Application and binds the Activity to framework context/window state. An uninstalled Guest cannot obtain a normal Guest LoadedApk through ordinary `createPackageContext`; replacing only resources or class loader leaves identity-bearing fields inconsistent.
