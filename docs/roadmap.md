# Roadmap

Phased so an agent building this can pick a milestone and know exactly what "done" means for
it, without guessing what's in scope.

## M1 — MVP (build this first)

Goal: a working end-to-end bridge, single hardcoded local target, run locally.

**Status: done.** Verified with scripted WebSocket clients driving the full
`docker-compose.dev.yml` stack (Apache with TLS → Tomcat → a real `linuxserver/openssh-server`
container):

- login, `echo`, output round-tripped, `exit` closed the SSH session and the bridge tore down
  the WebSocket with a normal close, with no lingering TCP connection on the sshd side afterward.
- resize genuinely reaches the remote PTY, not just the local xterm instance -- checked with
  `stty size` after both the initial post-connect resize and a simulated mid-session window
  drag (`frontend/js/main.js` wires `term.onResize`, not just the browser's `resize` event, to
  the outgoing `resize` message).
- a ~20KB paste survives (exercises `Session.setMaxTextMessageBufferSize`, since Tomcat's 8KB
  default is well under the 64KB protocol limit).
- a long-running command with no further client keystrokes does *not* get killed by the idle
  timeout -- confirmed the timer tracks outbound traffic too, not just inbound.
- bad password, malformed JSON, a message sent before `auth`, and the auth-grace timeout all
  close the socket correctly (1008/error-frame, no credential ever appearing in a log line).

- [x] `backend/`: Maven WAR project skeleton, Tomcat 10.1.x target, Java 17+.
- [x] `backend/`: `/api/health` endpoint.
- [x] `backend/`: `/ws/ssh` WebSocket endpoint per [api.md](api.md), using Apache MINA SSHD to
      bridge to a configured target (`sshomcat.properties`: `target.host`, `target.port`).
- [x] `backend/`: PTY-backed shell channel, resize propagation, clean teardown on either side
      closing.
- [x] `backend/`: idle timeout + max session duration (hardcode reasonable defaults, make
      configurable).
- [x] `frontend/`: login form (username/password) + xterm.js terminal, wired to the WS protocol
      in [api.md](api.md).
- [x] `proxy/`: working Apache vhost (`sshomcat.conf.example`) with TLS, static file serving,
      and `/api` + `/ws` proxying.
- [x] End-to-end manual test: browser → Apache → Tomcat → a real local SSH server, full
      interactive session (run a command, see output, resize the window, close the tab, confirm
      the SSH session on the target actually ends). Reproducible via `docker-compose.dev.yml`
      (dev-only stack, not the prescribed production shape) — see its header comment for usage.

Known gap carried forward, not blocking M1: SSH host-key verification defaults to
"accept-all" (`ssh.hostKeyVerification`), which is honest-but-insecure for anything beyond local
dev — see docs/security.md "Host key verification".

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
