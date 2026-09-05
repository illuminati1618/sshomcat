# frontend/

The SSHomcat client: xterm.js terminal UI + a small login form. Static HTML/CSS/JS, no build
step, no framework.

See [`../docs/architecture.md`](../docs/architecture.md) for what this needs to do, and
[`../docs/api.md`](../docs/api.md) for the exact WebSocket protocol it speaks.

## Layout

- `index.html` — login form + terminal container.
- `css/style.css` — minimal dark theme.
- `js/main.js` — WebSocket client speaking the protocol in `docs/api.md`; owns the login →
  terminal state machine.
- `vendor/xterm/` — `@xterm/xterm` 5.5.0 and `@xterm/addon-fit` 0.10.0, vendored (not CDN) since
  the whole pitch is locked-down networks that may block a CDN. Re-vendor with:
  ```
  npm pack @xterm/xterm@<version> @xterm/addon-fit@<version>
  # then copy lib/xterm.js, lib/addon-fit.js, css/xterm.css out of the tarballs into vendor/xterm/
  ```

Served directly by Apache in production (`DocumentRoot` in `proxy/sshomcat.conf.example`); no
build tool needed.
