# Broadcast Runtime Design

## Categories

1. Static manifest receivers: normally indexed by PMS and delivered by AMS.
2. Dynamic receivers: registered at runtime through a process/system boundary.
3. System broadcasts: controlled by platform restrictions and permissions.
4. Guest-internal broadcasts: can be delivered entirely by the guest controller.
5. Host broadcasts: belong to the host identity and must not be silently exposed to guests.

`PROPOSED`: support guest-internal broadcasts first. For selected system events, the host may receive an allowed broadcast and translate a sanitized event into the guest namespace. Static guest receivers should not be registered as host receivers automatically because this changes host exposure and permission semantics.

Required policy: exported status, receiver permission, ordered/sticky behavior, caller identity, user/instance scope, delivery timeout, duplicate suppression, and process startup. System broadcast eligibility is `RESEARCH NEEDED` per API level.
