# EXP-002: Uninstalled APK Resources

Status: Planned, not executed.

Question: can strings, drawable metadata/content, and a layout be read from an uninstalled APK with a guest-owned resource view?

Hypothesis: archive assets and resource tables can be opened independently, but construction/cache/configuration APIs may be version-sensitive.

Setup: GuestTestApp Phase B; import without installation; fixed locale/density/night configuration.

Observe: resource IDs, string value, drawable dimensions/identity, layout inflation result, host/guest resource object identity, and cache behavior.

Do not test Activity or configuration changes yet.

Exit: deterministic reads on defined API levels with no host resource leakage. Failure blocks Application/UI work.
