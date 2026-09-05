# Security Model

SSHomcat's entire job is bridging an untrusted browser to a real SSH server. Treat every
decision in this file as load-bearing, not optional polish.

## Core constraints (do not relax without re-reading this file)

1. **The target host/port is fixed, server-side configuration — never client-supplied.** The
   frontend never sends, and the backend never accepts, a target host from the browser. If this
   changes, SSHomcat stops being "a gateway to one specific server" and becomes an open SSH
   relay/proxy that can be pointed at anything reachable from the backend — a completely
   different (and much more dangerous) threat model.
2. **Tomcat is never directly internet-reachable.** Its connector binds to `127.0.0.1` only.
   Apache is the sole public entry point and does TLS termination — nothing reaches the backend
   except through Apache.
3. **All traffic is TLS** (`https://` / `wss://`). Plain HTTP should redirect to HTTPS; there is
   no legitimate reason to let the login form or terminal data travel unencrypted.

## Credential handling

- Username/password are collected once, client-side, and sent over the already-TLS-protected
  WebSocket as the first message.
- The backend uses them **exactly once**, to attempt the outbound SSH authentication — it does
  not persist them (no disk, no long-lived in-memory cache, no logs).
- SSH's own authentication is the only auth boundary in V1. There is intentionally no separate
  app-level user database — don't add one without updating this doc, since it changes the trust
  model (now there are two credentials to manage instead of one).
- Never log passwords, even at debug level. Logging the *fact* of an auth attempt (timestamp,
  username, success/failure) is fine and useful; logging the password itself is not.

## Abuse considerations

- **Brute force**: every WebSocket connect attempt carrying an `auth` message is effectively an
  SSH login attempt against the real target. The backend should track failed attempts (e.g., by
  source IP) and apply backoff/lockout after a threshold, so SSHomcat doesn't become a
  convenient way to brute-force the target's SSH server faster than talking to it directly
  would allow. (Required before any real-world exposure — see [roadmap.md](roadmap.md) M2 — not
  blocking for a first local milestone.)
- **Resource exhaustion**: cap concurrent sessions, enforce idle timeout and max session
  duration (see [architecture.md](architecture.md)), and enforce a max WebSocket message size.
  An attacker (or just a buggy client) should not be able to hold open unlimited unauthenticated
  sockets or unbounded buffers.
- **Unauthenticated sockets**: a WebSocket that never sends a valid `auth` message within a
  short grace period must be closed by the server, not held open indefinitely.

## Transport & deployment assumptions

- Use real TLS certificates in any environment beyond local dev (Let's Encrypt or an internal
  CA — self-signed is fine for local testing only; document it clearly wherever it's used).
- Backend config (`target.host`, `target.port`, timeouts, etc.) lives in a config file that is
  **not** checked into version control with real values. Ship a `*.example` version, document
  the required keys, keep the real deployment config out of git (see `.gitignore`).

## Explicitly deferred (not a V1 requirement, but the design should not preclude adding these later)

- Private-key-based SSH auth (removes password transit entirely — strictly better than password
  auth, worth prioritizing early in a post-V1 pass).
- MFA / an app-level auth layer in front of the SSH auth.
- Session audit logging/recording (what was typed/shown, for compliance-style use cases).
