"use strict";

/**
 * SSHomcat client. Speaks the JSON-text-frame protocol in docs/api.md over a WebSocket to
 * /ws/ssh. No target host/port is ever collected here -- that's fixed server-side config
 * (docs/security.md); this page only ever asks for SSH username/password.
 */
(() => {
  const loginView = document.getElementById("login-view");
  const terminalView = document.getElementById("terminal-view");
  const loginForm = document.getElementById("login-form");
  const usernameInput = document.getElementById("username");
  const passwordInput = document.getElementById("password");
  const loginError = document.getElementById("login-error");
  const statusEl = document.getElementById("status");
  const connectButton = loginForm.querySelector("button[type=submit]");

  let ws = null;
  let term = null;
  let fitAddon = null;

  function setStatus(text, cls) {
    statusEl.textContent = text;
    statusEl.className = "status status-" + cls;
  }

  function showLogin(errorMessage) {
    terminalView.hidden = true;
    loginView.hidden = false;
    connectButton.disabled = false;
    if (errorMessage) {
      loginError.textContent = errorMessage;
      loginError.hidden = false;
    } else {
      loginError.hidden = true;
    }
  }

  function showTerminal() {
    loginView.hidden = true;
    terminalView.hidden = false;
  }

  // -- base64 helpers -------------------------------------------------------
  // Byte-level round trip, not string-level: btoa(str) breaks on anything
  // outside Latin1. See docs/architecture.md / the base64 trap called out during build.

  function bytesToBase64(bytes) {
    let binary = "";
    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  function base64ToBytes(b64) {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  // -- terminal setup ---------------------------------------------------------

  function ensureTerminal() {
    if (term) {
      return;
    }
    term = new Terminal({
      cursorBlink: true,
      fontFamily: "Menlo, Consolas, 'Courier New', monospace",
      fontSize: 14,
      theme: { background: "#1e1e1e" },
    });
    fitAddon = new FitAddon.FitAddon();
    term.loadAddon(fitAddon);
    term.open(document.getElementById("terminal"));

    term.onData((data) => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        const bytes = new TextEncoder().encode(data);
        sendMessage({ type: "data", data: bytesToBase64(bytes) });
      }
    });

    // Fires whenever the terminal's actual column/row count changes (including as a result of
    // fitAddon.fit() below) -- this is what tells the *remote* PTY about a resize. Listening on
    // window "resize" alone would only re-fit the local xterm instance and never call
    // sendResize(), so dragging the browser window would silently desync wrapping from the
    // server's idea of the terminal size.
    term.onResize(() => sendResize());

    window.addEventListener("resize", () => {
      if (fitAddon && !terminalView.hidden) {
        fitAddon.fit();
      }
    });
  }

  function sendResize() {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      return;
    }
    sendMessage({ type: "resize", cols: term.cols, rows: term.rows });
  }

  function sendMessage(obj) {
    ws.send(JSON.stringify(obj));
  }

  // -- connection lifecycle ---------------------------------------------------

  function wsUrl() {
    const scheme = location.protocol === "https:" ? "wss" : "ws";
    return `${scheme}://${location.host}/ws/ssh`;
  }

  function connect(username, password) {
    setStatus("connecting", "connecting");
    connectButton.disabled = true;

    ws = new WebSocket(wsUrl());

    ws.addEventListener("open", () => {
      sendMessage({ type: "auth", username, password });
    });

    ws.addEventListener("message", (event) => {
      let msg;
      try {
        msg = JSON.parse(event.data);
      } catch {
        return; // ignore anything unparseable; the server won't send this
      }

      switch (msg.type) {
        case "connected": {
          ensureTerminal();
          showTerminal();
          setStatus("connected", "connected");
          // Fit to the now-visible container, then tell the server our real size --
          // the auth message carries no cols/rows (docs/api.md), so the PTY opens at a
          // default size until this arrives.
          requestAnimationFrame(() => {
            fitAddon.fit();
            sendResize();
          });
          break;
        }
        case "data": {
          if (term) {
            term.write(base64ToBytes(msg.data));
          }
          break;
        }
        case "error": {
          setStatus("error", "error");
          if (loginView.hidden) {
            // Mid-session error: surface it in the terminal itself.
            if (term) {
              term.write(`\r\n\x1b[31m[sshomcat] ${msg.message}\x1b[0m\r\n`);
            }
          } else {
            showLogin(msg.message);
          }
          break;
        }
        default:
        // Unknown message type: ignore (forward-compatible).
      }
    });

    ws.addEventListener("close", () => {
      setStatus("disconnected", "disconnected");
      ws = null;
      if (term) {
        term.dispose();
        term = null;
        fitAddon = null;
      }
      // No session resumption in V1 (docs/architecture.md): always back to a fresh login.
      showLogin();
      passwordInput.value = "";
    });

    ws.addEventListener("error", () => {
      // The 'close' event always follows; nothing extra to do here.
    });
  }

  loginForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const username = usernameInput.value;
    const password = passwordInput.value;
    connect(username, password);
  });
})();
