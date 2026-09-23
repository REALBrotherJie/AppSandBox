# ADR-0002: Logical identity does not impersonate Android identity

Status: Accepted

## Context

An ordinary host application cannot assign a new kernel UID or SELinux domain to an uninstalled guest.

## Decision

Represent package and instance identity inside AppSandbox. Preserve host UID/PID/package attribution at Android boundaries. Never claim that logical guest identity is an OS identity.

## Consequences

Storage and selected API behavior can be isolated logically. System services, permissions, notifications, and security enforcement require explicit limitations or host-mediated support.

## Risks

Guest code may assume installed-app identity. Such behavior is unsupported unless a public, testable compatibility path exists.
