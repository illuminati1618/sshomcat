package com.sshomcat.ws;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serializes all outbound text frames on a {@link Session}.
 *
 * <p>{@code Session.getBasicRemote().sendText()} is not safe to call concurrently. SSH channel
 * output arrives on a MINA SSHD worker thread while errors/close notifications can originate on
 * a container thread; without this, two overlapping sends throw
 * {@code IllegalStateException: ... TEXT_FULL_WRITING}. Every outbound frame for a given
 * connection must go through one instance of this class.
 */
public final class SafeSender {

    private static final Logger LOG = Logger.getLogger(SafeSender.class.getName());

    private final Session session;
    private final Object lock = new Object();

    public SafeSender(Session session) {
        this.session = session;
    }

    /** Best-effort send: logs and swallows failures (the session may already be closing). */
    public void send(String text) {
        synchronized (lock) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.getBasicRemote().sendText(text);
            } catch (IOException | IllegalStateException e) {
                LOG.log(Level.FINE, "Failed to send WS frame (session likely closing)", e);
            }
        }
    }
}
