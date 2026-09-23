# ADR-0003: Immutable package revisions and separate instances

Status: Proposed

## Context

Multiple instances need independent data while package code/resources should be shareable only when immutable and verified.

## Decision

Store imported APKs as immutable, digest-addressed package revisions. Store each Guest Instance's data and runtime state separately. PackageName is not an instance identifier.

## Consequences

Updates create revisions and do not silently mutate active instances. Registry migration and cleanup are required.

## Risks

Split APKs, signing rotation, native libraries, and writable code caches require experiments before sharing derived artifacts.
