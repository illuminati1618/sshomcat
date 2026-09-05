# API Contract

All endpoints are reached through Apache; Tomcat itself is never directly reachable (see
[security.md](security.md)).

## `GET /api/health`

Plain HTTP, proxied to Tomcat.

**Response** `200 OK`
```json
{ "status": "ok" }
```

## `WS /ws/ssh`

WebSocket, upgraded through Apache (`mod_proxy_wstunnel`) to Tomcat's
`@ServerEndpoint("/ws/ssh")`.

All messages, both directions, are **JSON text frames** with a `type` field. Byte-oriented
payloads (terminal I/O) are **base64-encoded** inside a JSON string field.

### Client → Server

**`auth`** — must be the first message sent, immediately after the socket opens. If the server
doesn't receive this within a few seconds, it closes the connection.
```json
{ "type": "auth", "username": "string", "password": "string" }
```

**`data`** — terminal input (keystrokes, pasted text).
```json
{ "type": "data", "data": "<base64>" }
```

**`resize`** — terminal viewport changed size.
```json
{ "type": "resize", "cols": 120, "rows": 34 }
```

### Server → Client

**`connected`** — SSH session + shell channel established successfully. Safe to show the
terminal now.
```json
{ "type": "connected" }
```

**`data`** — output from the SSH channel (stdout/stderr combined, as a PTY would present it).
```json
{ "type": "data", "data": "<base64>" }
```

**`error`** — something failed (bad auth, connection refused, timeout, internal error). Always
followed by the server closing the WebSocket.
```json
{ "type": "error", "message": "human-readable reason" }
```

## Close codes

Use standard WebSocket close codes where they fit (`1000` normal, `1008` policy violation — e.g.
malformed/oversized message, `1011` internal error). Document any custom codes here as they're
introduced.

## Message size limits

The backend must enforce a maximum frame size (start at 64 KB) and reject/close on anything
larger, to avoid a malformed or hostile client causing unbounded buffering.
