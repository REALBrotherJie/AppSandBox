# EXP-003C Application.onCreate Plan

## Preconditions

EXP-003A and EXP-003B must both be independently `CONFIRMED`.

## Question

Can a minimal Guest Application execute `onCreate` on the Host main thread
using only the accepted C0 contract?

## Allowed Actions

Read the seven C0 getters, write one file in the Guest Instance files path,
and return a JSON report with the unique Guest marker.

## Forbidden Actions

No PackageManager query, ContentResolver, system service, Activity, Service,
Receiver, Provider, network, WebView, native library, database, preferences,
third-party SDK, or Host Application access.

## Tests

Verify `Looper.myLooper() == Looper.getMainLooper()`, exactly one `onCreate`,
marker transport, Guest paths, Guest resources/assets, and Host survival.
Inject constructor and `onCreate` failures separately and record the runtime
state as `FAILED` without silently swallowing the exception.

## Success

The minimal lifecycle is deterministic, single-shot, main-thread-bound, and
does not leak Host semantics.

## Failure

Capture the exception and mark the Guest Instance failed. Do not claim Android
system `bindApplication` semantics.

