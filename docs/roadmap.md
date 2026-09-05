# Roadmap

Phased so an agent building this can pick a milestone and know exactly what "done" means for
it, without guessing what's in scope.

## M1 — MVP (build this first)

Goal: a working end-to-end bridge, single hardcoded local target, run locally.

- [ ] `backend/`: Maven WAR project skeleton, Tomcat 10.1.x target, Java 17+.
- [ ] `backend/`: `/api/health` endpoint.
- [ ] `backend/`: `/ws/ssh` WebSocket endpoint per [api.md](api.md), using Apache MINA SSHD to
      bridge to a configured target (`sshomcat.properties`: `target.host`, `target.port`).
- [ ] `backend/`: PTY-backed shell channel, resize propagation, clean teardown on either side
      closing.
- [ ] `backend/`: idle timeout + max session duration (hardcode reasonable defaults, make
      configurable).
- [ ] `frontend/`: login form (username/password) + xterm.js terminal, wired to the WS protocol
      in [api.md](api.md).
- [ ] `proxy/`: working Apache vhost (`sshomcat.conf.example`) with TLS, static file serving,
      and `/api` + `/ws` proxying.
- [ ] End-to-end manual test: browser → Apache → Tomcat → a real local SSH server, full
      interactive session (run a command, see output, resize the window, close the tab, confirm
      the SSH session on the target actually ends).

## M2 — Hardening (do before any real-world / multi-user exposure)

- [ ] Failed-auth rate limiting / backoff per source IP (see [security.md](security.md)).
- [ ] Structured logging (connection attempts, errors, session start/end — never credentials).
- [ ] Config validation on startup (fail fast with a clear error if `target.host` etc. are
      missing/invalid, rather than failing confusingly on first connection).
- [ ] Max concurrent session limit.
- [ ] Message size limit enforcement on the WebSocket (reject oversized frames).
- [ ] Basic automated tests: at minimum, the WS protocol state machine (auth timeout, malformed
      message handling, resize handling) and a mocked-SSH-backend integration test.

## M3 — Stretch (explicitly not required, don't build unless asked)

- [ ] Private-key-based SSH auth as an alternative to password auth.
- [ ] Support more than one configured target (still no arbitrary client-supplied targets — a
      small server-side allowlist, not an open relay).
- [ ] Session audit logging.
- [ ] Binary WebSocket framing instead of JSON+base64, if profiling shows it matters.
- [ ] MFA / app-level auth layer.
