# backend/

The SSHomcat SSH bridge: a Java WAR deployed to Tomcat. Owns the actual outbound SSH connection
(via Apache MINA SSHD) and bridges it to the browser over a WebSocket.

See [`../docs/architecture.md`](../docs/architecture.md) for the component design,
[`../docs/api.md`](../docs/api.md) for the exact protocol, and
[`../docs/security.md`](../docs/security.md) before touching auth/credential handling.

## Layout

- `pom.xml` — Maven WAR project. Java 17, Tomcat 10.1 / Jakarta EE (Servlet 6 + WebSocket 2.1
  specs only — Tomcat is not a full EE app server, see the comment in `pom.xml`).
- `src/main/java/com/sshomcat/`
  - `config/AppConfig.java` — loads + validates `sshomcat.properties` (fails fast on startup).
  - `listener/AppContextListener.java` — starts/stops the shared SSH client and timer pool.
  - `AppServices.java` — process-wide singletons (config, `SshClient`, scheduler).
  - `http/HealthServlet.java` — `GET /api/health`.
  - `ws/` — the WebSocket bridge endpoint (`SshBridgeEndpoint`) and the protocol codec
    (`ProtocolCodec`/`InboundMessage`), which is unit-tested (`src/test/java/...`).
  - `ssh/SshBridge.java` — the MINA SSHD side: connect, password auth, PTY shell channel,
    stream pumping, resize, teardown.
- `sshomcat.properties.example` — copy to `src/main/resources/sshomcat.properties` (gitignored)
  for a local build, or deploy separately and point `-Dsshomcat.config=<path>` at it.

## Building

The backend is part of the repo-root Maven build (it depends on `../module-api`). No local Maven
install required — from the repo root, build in a container:

```sh
docker run --rm -v "$PWD:/workspace" -w /workspace maven:3.9-eclipse-temurin-17 mvn -q -B package
```

Produces `backend/target/sshomcat.war`. Run the unit tests with `mvn test` (same image).

## Running it end-to-end locally

See `../docker-compose.dev.yml` at the repo root — spins up this backend, a throwaway SSH
server, and Apache together, TLS included, for the full manual E2E test in `docs/roadmap.md` M1.
