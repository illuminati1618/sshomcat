package com.sshomcat.ws;

/** Client -> server messages, per docs/api.md. */
public sealed interface InboundMessage {

    /** Must be the first message on the socket. Never logged (carries {@code password}). */
    record Auth(String username, String password) implements InboundMessage {
        // Records auto-generate toString() from all components; override so an accidental
        // log.info(authMessage) or debugger watch never prints the password (docs/security.md).
        @Override
        public String toString() {
            return "Auth[username=" + username + ", password=<redacted>]";
        }
    }

    /** Terminal input (keystrokes, pasted text). {@code base64Data} is the raw base64 text. */
    record Data(String base64Data) implements InboundMessage {
    }

    /** Terminal viewport resize. */
    record Resize(int cols, int rows) implements InboundMessage {
    }
}
