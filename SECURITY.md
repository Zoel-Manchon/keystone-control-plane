# Security policy

## Reporting a vulnerability

Please do not open a public issue for an exploitable vulnerability. Use GitHub's
private vulnerability reporting feature for this repository when available. Include
the affected component, reproduction steps, impact and any suggested mitigation.

## Scope

Keystone is a portfolio/reference control plane, not a managed production PKI. The
local Compose topology deliberately binds services to loopback. Production deployment
requires TLS for the HTTP control plane, external secret management, database network
isolation and offline/HSM-backed custody for high-value signing keys.

## Cryptographic material

Generated CA keystores, firmware signing keys, broker private keys and demo artifacts
must never be committed. CI rejects common private-key material and `.gitignore` also
excludes generated key paths. If such material is ever pushed, treat the key as
compromised and rotate it; deleting it in a later commit is not sufficient.
