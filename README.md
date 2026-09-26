# SSHomcat

*(pronounced "shomcat")*

A self-hosted, browser-based SSH terminal gateway. SSHomcat lets someone open a real SSH
session to one specific server entirely from a web browser tab — no local SSH client, no
browser extension, no VPN. You enter an SSH username and password on a page, and you're in
a live shell, rendered with [xterm.js](https://xtermjs.org/).

## Notice
**This is a completely vibecoded project.** I made it simply because I came up with an interesting idea and architecture, and published it in case someone finds it useful :)

## Status

**1.0.0.** The MVP (M1) is built and verified end-to-end: frontend, backend, and proxy config
have been run together against a real SSH server — see [`docs/roadmap.md`](docs/roadmap.md) for
what was tested and how to reproduce it (`docker-compose.dev.yml`). The M2 hardening items in the
roadmap are still open, so read [`docs/security.md`](docs/security.md) before exposing it to
anyone you don't trust.

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
├── module-api/             -- the add-on module contract (SshomcatModule, TargetResolver)
├── modules/                -- bundled add-on modules (multi-target-router, motd-banner)
├── proxy/                  -- Apache vhost config + example (production shape)
├── docker/dev/             -- dev-only Apache image/config + throwaway TLS cert for local E2E testing
├── docker-compose.dev.yml  -- spins up sshd + backend + Apache together for local testing
├── pom.xml                 -- root Maven build (module-api, backend, modules/*)
└── README.md
```

## Building

Requires Java 25 and Maven 3.9+ (or use the `maven:3.9-eclipse-temurin-25` image, see
[`backend/README.md`](backend/README.md)). From the repo root:

```sh
mvn clean verify
```

This produces `backend/target/sshomcat.war` plus one JAR per module under `modules/*/target/`.
Prebuilt copies of all of them are attached to each
[GitHub release](https://github.com/illuminati1618/sshomcat/releases).

## Modules

SSHomcat runs fine with no modules. Optional add-ons are plain JARs dropped into the directory
named by `modules.directory`; see [`docs/modules.md`](docs/modules.md) for the contract and how
to write one. Bundled here:

- [`multi-target-router`](modules/multi-target-router) — routes each authenticated user to a
  per-user SSH target from a small, admin-curated allowlist file, instead of the single default
  target.
- [`motd-banner`](modules/motd-banner) — logs a startup banner; the smallest complete example to
  copy from.

## Docs

- [`docs/architecture.md`](docs/architecture.md) — full system design, components, data flow, tech choices & why
- [`docs/api.md`](docs/api.md) — concrete HTTP + WebSocket contract
- [`docs/security.md`](docs/security.md) — threat model and required mitigations
- [`docs/modules.md`](docs/modules.md) — add-on module contract, discovery, and packaging
- [`docs/roadmap.md`](docs/roadmap.md) — phased build plan (what's V1 vs. later)

## License

MIT — see [`LICENSE`](LICENSE).
