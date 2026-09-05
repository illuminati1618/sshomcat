# backend/

The SSHomcat SSH bridge: a Java WAR deployed to Tomcat. Owns the actual outbound SSH connection
(via Apache MINA SSHD) and bridges it to the browser over a WebSocket.

See [`../docs/architecture.md`](../docs/architecture.md) for the component design,
[`../docs/api.md`](../docs/api.md) for the exact protocol, and
[`../docs/security.md`](../docs/security.md) before touching auth/credential handling.

Nothing has been built here yet.
