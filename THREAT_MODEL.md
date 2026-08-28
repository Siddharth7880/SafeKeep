# SafeKeep — Threat Model

*Last Updated: 2026-08-28*
*Architecture Version: Phase 2 (Client-Side Zero-Knowledge Encryption)*

---

## Overview

SafeKeep is a Digital Dead Man's Switch. Users store AES-256-GCM encrypted vault items.
If a user fails to check in within their configured interval, the system automatically
releases vault contents to pre-designated trusted recipients.

This document analyzes the system's attack surface, impact of each threat scenario,
and the mitigations in place.

---

## Trust Boundaries

```
┌─────────────────────────────────────────────────────────┐
│  TRUSTED: User's Browser                                │
│  - Vault password (plaintext — never leaves this box)   │
│  - Argon2id key derivation (hash-wasm WASM)            │
│  - AES-256-GCM encryption/decryption (crypto.subtle)   │
└───────────────────┬─────────────────────────────────────┘
                    │ HTTPS only (encrypted transport)
                    │ Payload: ciphertext only (no plaintext)
┌───────────────────▼─────────────────────────────────────┐
│  PARTIALLY TRUSTED: SafeKeep Server                     │
│  - Stores encrypted blobs (never decrypts in user path) │
│  - Holds server secret (release path only)              │
│  - Authenticates users via JWT                          │
└───────────────────┬─────────────────────────────────────┘
                    │ Internal (connection string)
┌───────────────────▼─────────────────────────────────────┐
│  TRUSTED: PostgreSQL Database                           │
│  - Stores only: ciphertext, encrypted DEKs, salts       │
│  - Zero plaintext content                               │
└─────────────────────────────────────────────────────────┘
```

---

## Attack Scenarios

### Scenario 1 — Full Database Breach

**Attacker gains:** Complete PostgreSQL dump (all tables, all rows).

**What is exposed:**
- Email addresses
- Argon2id password hashes (bcrypt for login, not vault key)
- Vault item labels (unencrypted — intentional, see below)
- AES-256-GCM ciphertext blobs
- Encrypted DEKs (wrapped, not raw)
- Argon2id salts (public by design)
- Recipient names and email addresses

**What is NOT exposed:**
- Vault content (AES-256-GCM ciphertext only)
- Vault passwords (never stored, never sent to server in Phase 2)
- Raw DEKs (only wrapped copies stored)

**Attack to decrypt content:**
An attacker must brute-force: Argon2id(vault_password, salt) → unwrap DEK → decrypt content.

With Argon2id (64MB RAM cost, 4 iterations, 2 lanes):
- Time per attempt on consumer GPU (RTX 4090): ~3–5 seconds
- 10-character password from a 60-char charset: 60^10 = 6×10^17 combinations
- At 0.3 attempts/sec per GPU, 1M GPUs: ~6×10^10 GPU-seconds ≈ 190 years

**Verdict:** Database breach alone is **not sufficient** to decrypt vault content for strong passwords.

**Residual risk:** Weak vault passwords (< 8 chars, dictionary words). Mitigated by:
- Password strength indicator in UI (planned)
- Argon2id memory cost makes even weak passwords hard to crack at scale

---

### Scenario 2 — Full Server Compromise

**Attacker gains:** Root shell on the application server. Access to environment variables.

**What is exposed:**
- `JWT_SECRET` → can forge JWT tokens → impersonate any user
- `RELEASE_TOKEN_SECRET` / server secret → can derive server master key → decrypt any vault item using the server DEK copy

**What is NOT exposed:**
- Vault passwords (never sent to server in Phase 2)
- User's raw DEKs (only sent once per create/update over HTTPS, immediately wrapped and discarded)

**Impact:** **Critical.** An attacker with both `RELEASE_TOKEN_SECRET` and the DB can decrypt all vault items via the server-key release path. This is an acceptable trade-off because:

1. The release path is necessary for the core product feature (automated delivery to recipients)
2. The alternative (no server-key copy) makes automated delivery impossible
3. Compromise of the server secret + database requires simultaneous compromise of two separate systems

**Mitigations:**
- Server secret stored only in environment variables, never in code or git
- Secret rotation procedure (planned): re-wrap all server DEK copies with new secret
- Principle of least privilege: application process runs as non-root
- Audit log records all release-path decryption attempts

---

### Scenario 3 — Man-in-the-Middle (HTTPS Stripping)

**Attacker position:** Between user's browser and SafeKeep server (e.g., rogue WiFi).

**Phase 2 architecture:** The vault password is derived into a key client-side and never sent. The payload sent over the wire is already AES-256-GCM ciphertext.

**What a MITM sees (if HTTPS is stripped):**
- JWT token → can impersonate the user on the API
- Encrypted request body (ciphertext, IV, encryptedDEK, salt)
- **NOT** the vault password, **NOT** the raw DEK, **NOT** plaintext content

**Impact:** MITM can replay or delete API requests with the stolen JWT, but **cannot decrypt vault content**.

**Mitigations:**
- HTTPS enforced server-side (HSTS headers — deploy with Nginx/Caddy)
- JWT expiry: 24 hours (access token), 7 days (refresh token)
- Rate limiting on `/api/auth/login`: 10 attempts / 15 minutes per IP

---

### Scenario 4 — Malicious rawDEK Interception

**Attack surface:** During vault item creation, the browser sends `rawDEK` (the plaintext DEK bytes) over HTTPS so the server can wrap it with the server key for the release path.

**Attacker position:** Must be able to see the decrypted HTTPS payload (i.e., must have compromised TLS, which requires either the server private key or a trusted CA compromise).

**If attacker intercepts rawDEK:**
- They have the DEK for that vault item only
- They still need the ciphertext from the database
- This requires combining a TLS compromise + database access

**Mitigations:**
- rawDEK is transmitted once per create/update, over HTTPS
- Server processes rawDEK synchronously, wraps it, and immediately zeroes the byte array from JVM heap
- rawDEK is never logged, never persisted raw, never echoed back to client
- Considered future alternative: browser wraps rawDEK directly with a server public key (asymmetric) → server decrypts with private key → eliminates plaintext DEK in transit entirely. Planned for v2.

---

### Scenario 5 — Brute-Force Login Attack

**Target:** `/api/auth/login` — standard username + password login.

**Note:** Login password and vault password are separate. Compromising login password only allows account management, not vault decryption.

**Mitigations:**
- Bucket4j token-bucket rate limiting: 10 attempts / 15 minutes per IP
- Passwords hashed with BCrypt (cost factor 10) server-side
- Email verification required before login
- JWT short expiry

---

### Scenario 6 — JWT Token Theft

**Attacker gains:** A valid JWT access token (e.g., via XSS, session sniffing).

**What they can do:**
- List vault item metadata (labels, types, recipients — no content)
- Fetch encrypted blobs (useless without vault password to derive key)
- Call check-in endpoint on behalf of user
- Access any API endpoint requiring authentication

**What they CANNOT do:**
- Decrypt vault content (requires vault password, not JWT)
- Delete items without knowing vault password (browser verifies before calling delete)

**Mitigations:**
- Access token expires in 24 hours
- HTTPS-only (token cannot be sniffed in transit)
- `HttpOnly` cookie option (planned — currently localStorage)
- XSS prevention via React's default escaping

---

### Scenario 7 — Malicious Recipient Link Reuse

**Attack:** Recipient receives a vault release link. Shares it or reuse attempts.

**Mitigations:**
- Release tokens expire after 72 hours
- One-time use tokens (planned)
- Tokens are HMAC-signed with `RELEASE_TOKEN_SECRET`
- Audit log records every token use

---

## Known Limitations

| Limitation | Severity | Plan |
|---|---|---|
| Vault item labels stored unencrypted | Low | Acceptable: labels are user-chosen display names, not secrets |
| `rawDEK` sent over HTTPS (not asymmetric wrap) | Medium | Plan: use server public key for asymmetric DEK encapsulation in v2 |
| Rate limiting in-memory (not Redis) | Low | Fails over in clustered deployment — use Redis Bucket4j backend before scaling horizontally |
| LocalStorage for JWT | Low | Migrate to HttpOnly cookies before production |
| No CSP header configured | Medium | Add Content-Security-Policy header in Nginx/Spring Security config |
| Recipient email addresses stored unencrypted | Medium | Necessary for SMTP delivery; encrypt with server key in v2 |

---

## Attack Surface Summary

| Surface | Auth Required | Encryption | Rate Limited |
|---|---|---|---|
| POST /api/auth/register | ❌ | Bcrypt (password) | ✅ 5/hr |
| POST /api/auth/login | ❌ | Bcrypt verify | ✅ 10/15min |
| POST /api/auth/forgot-password | ❌ | HMAC token | ✅ 3/15min |
| GET /api/vault/items | JWT | AES-256-GCM blobs | ✅ 60/min |
| POST /api/vault/items | JWT | AES-256-GCM blobs | ✅ 60/min |
| GET /api/vault/items/{id} | JWT | AES-256-GCM blobs | ✅ 60/min |
| DELETE /api/vault/items/{id} | JWT | — | ✅ 60/min |
| GET /api/audit/logs | JWT | — | ✅ 60/min |
| Release link (recipient view) | HMAC token | AES-256-GCM | ❌ planned |

---

## Conclusion

SafeKeep's primary defense-in-depth is:

1. **Ciphertext at rest** — DB breach reveals no plaintext
2. **Browser-only key derivation** — server never sees vault passwords (Phase 2+)
3. **Dual DEK envelope** — user path and release path use independent key material
4. **Rate limiting** — brute-force attacks are throttled at the network layer
5. **Audit trail** — all state transitions logged for forensic analysis

The most credible high-impact attack remains simultaneous compromise of the server
environment (to obtain `RELEASE_TOKEN_SECRET`) AND the database — which is why
these two assets must be secured independently (separate credentials, separate access controls).
