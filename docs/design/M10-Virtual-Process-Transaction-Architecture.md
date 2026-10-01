# M10 Virtual Process Transaction Architecture

## Decision

Adopt `CENTRAL_COORDINATOR_PLUS_PROCESS_AGENT`.

Android AMS remains the owner of physical Host component/process lifecycle. A single coordinator in the Host main/control process owns logical Guest process lifecycle. Every manifest slot process exposes one internal Binder ProcessAgent which owns only that process's current Guest runtime and component execution.

## Authoritative Model

`VirtualProcessKey(packageRevision, packageName, instanceId, logicalProcessName)` is the durable logical identity. The coordinator owns `VirtualProcessRecord(key, slot, pid, generation, state, agentBinder, nativeState, webViewState, pendingTransactions, activeComponents)`. Slot and pid are replaceable runtime attributes.

States are `FREE -> ALLOCATING -> STARTING_PHYSICAL_PROCESS -> AGENT_CONNECTED -> BINDING_GUEST -> READY -> DYING -> DEAD`. A transaction may dispatch only when its record is `READY` and its expected generation matches. Generation is monotonic at coordinator scope.

Transactions use `QUEUED -> DISPATCHED -> ACKNOWLEDGED -> COMPLETED|FAILED`. The coordinator assigns the transaction id, queues before READY, dispatches once, and never replays an acknowledged non-idempotent lifecycle operation. The agent rejects every request whose key, slot, or generation differs from its current binding.

## Bootstrap And Death

The coordinator selects a free slot and binds its Host ProcessAgent service. It validates the manifest-selected slot, physical pid, bootstrap token, and generation before sending `BindVirtualProcess`. The agent binds M9 native state, configures the deterministic WebView suffix, creates the Guest Context/ClassLoader/Application, installs process providers, calls Application.onCreate, then reports READY.

The coordinator links to the agent Binder death. Death atomically marks the record DEAD, invalidates endpoints and pending generation-bound work, clears process-owned component records, and releases the slot. Reuse for a different key always kills/recreates the Linux process; live reuse is permitted only for the same key. Coordinator death invalidates/kills agents and rebuilds from a clean registry after Host restart.

## Component Mapping

Service resolution creates a transaction for the ServiceInfo logical process. After READY, AMS starts/binds the slot StubService and the target agent reuses M7's ActivityThread transaction restoration. Logical started/bound metadata belongs to the coordinator; the Service object, token, startId and returned Binder belong to the agent generation.

Receiver resolution calls `ensureProcess(key)` before emitting the physical StubReceiver delivery. The existing M8 session/delivery id carries slot and generation; the target Agent validates them before `H.RECEIVER` restoration. AMS continues to own ordered propagation, abort and `goAsync` barriers.

Provider acquisition calls `ensureProcess(key)`, then `ensureProvider` on the Agent. The Agent uses the existing real `ActivityThread.installProvider` path and returns the real `IContentProvider.Transport` Binder tagged with generation. Binder death or generation change invalidates the client handle.

WebView state (`NOT_CONFIGURED`, `CONFIGURED`, `INITIALIZED`) is part of the coordinator record and is reported by the Agent. `INITIALIZED` forbids live rebinding to another key. M9 binding and WebView suffix configuration both occur before Guest Application or component code.

## Evidence And Alternatives

API31 and API36 probes queued work before Agent READY, ran the real Demo2 remote Service through `CREATE_SERVICE`, `onCreate`, `START_COMMAND`, `BIND`, returned its Messenger Binder across different pids, detected Agent death, created a new pid/generation, and rejected the old generation before repeating the lifecycle.

Distributed component managers are rejected because they split slot, generation, death and WebView ownership. Agent self-ownership is rejected because it cannot serialize cold allocation or prevent collisions. AMS physical components alone are rejected because AMS has no VirtualProcessKey, Guest generation, instance storage, M9 binding or WebView ownership semantics.

The architecture probe is not production routing. Production work must generalize the one-slot prototype, persist no pid as identity, add concurrency/pool exhaustion policy, authenticate bootstrap tokens, and connect Service, Receiver and Provider managers to the coordinator contract.
