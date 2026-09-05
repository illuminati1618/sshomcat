package com.sshomcat.ws;

/**
 * A client message failed to parse or didn't match the protocol in docs/api.md. Callers should
 * treat this as a policy violation: send an {@code error} frame (if appropriate) and close the
 * WebSocket -- never let a malformed message crash the connection silently or get echoed back
 * verbatim (it may contain attacker-controlled content).
 */
public class ProtocolException extends Exception {
    public ProtocolException(String message) {
        super(message);
    }
}
