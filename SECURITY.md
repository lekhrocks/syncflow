# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✅ Active development |
| < 0.1   | ❌ Not released |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue in SyncFlow, please follow responsible disclosure:

1. **Do not** file a public GitHub issue.
2. Email details to `security@syncflow.dev`.
3. Include as much context as possible:
   - Affected version(s)
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if available)

We aim to:
- Acknowledge receipt within **24 hours**
- Provide a preliminary assessment within **72 hours**
- Release a fix within **30 days** for critical vulnerabilities

## Security Contacts

- **Security team:** `security@syncflow.dev`
- **PGP key:** Available on request

## Bug Bounty

At this time, SyncFlow does not operate a formal bug bounty program. Security researchers who report valid vulnerabilities will be credited in release notes and acknowledged in our security hall of fame.

## Security Testing

SyncFlow undergoes the following security testing:

| Test | Frequency | Method |
|------|-----------|--------|
| Dependency scanning | Every CI run | Trivy |
| SAST | Every CI run | CodeQL |
| Container scanning | Every Docker build | Trivy |
| Secret scanning | Every push | GitHub secret scanning |
| Dependency review | Every PR | GitHub dependency review |
| SBOM generation | Every release | CycloneDX |

## Threat Model

A comprehensive threat model is maintained at `docs/security/threat-model.md`, covering 11 threat categories with risk ratings, mitigations, and test coverage.
