# Permission Model

The guest declares permissions, but Android grants permissions to the host package/UID. Therefore guest permission state and host permission state are different records.

## States

- declared by guest;
- allowed by AppSandbox policy;
- granted by host Android permission state;
- denied by user/policy;
- restricted by AppOps or special permission;
- unavailable because no safe mapping exists.

`PROPOSED`: require both guest policy approval and host capability. A guest request never silently causes a host permission prompt without user-visible attribution. Dangerous permissions may map to a host runtime request, but the result is host-scoped and must be filtered. Signature/privileged permissions are denied unless a documented host capability exists. Special permissions and account/location/camera attribution need service-specific policy.

Do not report a guest grant as an OS grant. Preserve a decision log and revoke on instance deletion.
