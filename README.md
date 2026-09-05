# SSHomcat

*(pronounced "shomcat")*

A self-hosted, browser-based SSH terminal gateway. SSHomcat lets someone open a real SSH
session to one specific server entirely from a web browser tab — no local SSH client, no
browser extension, no VPN. You enter an SSH username and password on a page, and you're in
a live shell, rendered with [xterm.js](https://xtermjs.org/).

## Status

✅ **M1 (MVP) built and verified end-to-end.** Frontend, backend, and proxy config all exist and
have been run together against a real SSH server — see [`docs/roadmap.md`](docs/roadmap.md) for
what was tested and how to reproduce it (`docker-compose.dev.yml`). **If you're an agent picking
this up next: M1's checklist is done — start with [`docs/roadmap.md`](docs/roadmap.md) M2
(hardening) unless told otherwise.** [`docs/architecture.md`](docs/architecture.md),
[`docs/api.md`](docs/api.md), and [`docs/security.md`](docs/security.md) are still the spec —
keep them in sync with any change in behavior.

## Why

Sometimes you need shell access to one known server from a machine where installing or
configuring a full SSH client isn't practical or allowed — a locked-down corporate laptop, a
shared kiosk, a training/lab environment, a quick remote-support session. SSHomcat trades "any
client, any host" flexibility for "just open a browser tab" simplicity — it is deliberately
scoped to a single, server-configured target rather than being a general-purpose SSH proxy.

## Architecture at a glance

```
Browser (xterm.js)  <--wss-->  Apache (reverse proxy, TLS)  <--ws-->  Tomcat (Java backend)  <--ssh-->  Target SSH server
```

- **Frontend** — static JS/HTML/CSS, xterm.js terminal UI, no framework, no build step.
- **Apache** — terminates TLS, serves the frontend, reverse-proxies the WebSocket to the backend.
- **Backend** — a Java webapp on Tomcat. Owns the actual SSH client connection (via Apache MINA
  SSHD) and bridges its I/O to the browser over a WebSocket.

Full detail, sequence diagram, and message protocol: [`docs/architecture.md`](docs/architecture.md).

## Repo layout

```
sshomcat/
├── docs/                   -- start here: architecture, API contract, security model, roadmap
├── frontend/               -- xterm.js client (static site)
├── backend/                -- Java/Tomcat WAR project (the SSH bridge)
├── proxy/                  -- Apache vhost config + example (production shape)
├── docker/dev/             -- dev-only Apache image/config + throwaway TLS cert for local E2E testing
├── docker-compose.dev.yml  -- spins up sshd + backend + Apache together for local testing
└── README.md
```

## Docs

- [`docs/architecture.md`](docs/architecture.md) — full system design, components, data flow, tech choices & why
- [`docs/api.md`](docs/api.md) — concrete HTTP + WebSocket contract
- [`docs/security.md`](docs/security.md) — threat model and required mitigations
- [`docs/roadmap.md`](docs/roadmap.md) — phased build plan (what's V1 vs. later)

## License

MIT — see [`LICENSE`](LICENSE).
