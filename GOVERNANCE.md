# SyncFlow Governance

> **Version:** 1.0  
> **Last Updated:** 2026-07-17  
> **License:** Apache 2.0  

## Project Maintainers

SyncFlow is maintained by a group of core contributors who guide the project's technical direction, review contributions, and manage releases.

| Role | Responsibilities |
|------|-----------------|
| **Lead Maintainer** | Overall technical direction, release management, final decision on contentious issues |
| **Core Maintainers** | Area ownership (CDC engine, snapshot, sync, AI, UI, K8s), PR review, issue triage |
| **Contributors** | Bug fixes, features, documentation, testing |

## Decision Making

### Lazy Consensus

Decisions default to consensus. If no objections are raised within 72 hours on a public discussion thread, the proposal is considered accepted.

### Voting

For contentious decisions where consensus cannot be reached:
- Core maintainers vote
- Simple majority wins
- Lead maintainer casts tie-breaking vote

### RFC Process

Significant changes (new modules, breaking API changes, architecture changes) follow an RFC process:
1. **Discussion** — GitHub Discussion thread for 5 business days
2. **RFC document** — ADR-style document describing motivation, design, trade-offs
3. **Review period** — 5 business days for maintainer review
4. **Decision** — Accepted, rejected, or changes requested

## Release Process

| Frequency | Type | Version Bump | Changelog |
|-----------|------|:------------:|:---------:|
| Monthly | Minor | 0.1 → 0.2 | Full CHANGELOG.md |
| As needed | Patch | 0.1.0 → 0.1.1 | Bug fixes only |
| Yearly | Major | 0.x → 1.0 | Breaking changes |

## Contribution Tiers

| Tier | Description | Review Requirements |
|:----:|-------------|:------------------:|
| 🟢 Trivial | Typo fixes, docs, test additions | Single maintainer |
| 🔵 Standard | Bug fixes, small features | 1 maintainer approval |
| 🟠 Significant | New modules, API changes, performance work | 2 maintainers + 72h RFC |
| 🔴 Architecture | Architecture changes, breaking API changes | Full RFC process |

## Code of Conduct

All contributors must adhere to the [Code of Conduct](CODE_OF_CONDUCT.md). Violations should be reported to `conduct@syncflow.dev`.

## Licensing

SyncFlow is licensed under the Apache License 2.0. All contributions are made under the same license.
