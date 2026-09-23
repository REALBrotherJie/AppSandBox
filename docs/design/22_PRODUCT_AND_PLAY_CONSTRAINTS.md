# Product and Play Constraints

This is engineering guidance, not legal advice.

- SAF user selection is preferable to broad storage access for imported APKs.
- `QUERY_ALL_PACKAGES` should not be assumed; package visibility must be justified or avoided.
- Dynamically loaded code and executable guest content require security, disclosure, and policy review.
- Host runtime permissions remain host permissions; guest consent must be visible.
- Accessibility must not be a required workaround for core runtime behavior.
- Background execution, foreground services, notifications, and exact alarms remain subject to platform policy.
- The product must not provide cheating, anti-detection, DRM bypass, account abuse, automation of other apps, or server-protocol manipulation.

`RESEARCH NEEDED`: current Play policy treatment for this product shape, target SDK requirements, and distribution model. Revalidate before publication rather than freezing policy conclusions in code.
