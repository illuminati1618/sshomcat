package com.sshomcat.ssh;

import com.sshomcat.AppServices;
import com.sshomcat.config.AppConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.PtyChannelConfiguration;
import org.apache.sshd.common.channel.PtyChannelConfigurationHolder;

/**
 * One outbound SSH connection + PTY-backed shell channel, bridged to a listener. Owns the MINA
 * SSHD specifics (connect, password auth, PTY, stream pumping, resize, teardown) so
 * {@code SshBridgeEndpoint} only has to deal with the WebSocket side. See docs/architecture.md
 * for the responsibilities this implements.
 */
public final class SshBridge implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SshBridge.class.getName());

    /** Callbacks run on MINA SSHD worker threads / a dedicated wait thread -- never on the caller's thread. */
    public interface Listener {
        void onData(byte[] bytes);

        /** Called exactly once, when the SSH side ends for any reason (remote exit, error, or our own close()). */
        void onClosed();
    }

    private final ClientSession session;
    private final ChannelShell channel;
    // Two separate flags, deliberately not one: `notified` guards the listener callback (must
    // fire at most once), `teardownStarted` guards the actual channel/session close (must also
    // happen exactly once, but on a *different* trigger -- see close()/notifyClosedOnce()).
    // Collapsing them into one flag means whichever path runs first (our own close() call, or
    // the remote side closing) starves the other of its half of the work.
    private final AtomicBoolean notified = new AtomicBoolean(false);
    private final AtomicBoolean teardownStarted = new AtomicBoolean(false);
    private final Listener listener;

    private SshBridge(ClientSession session, ChannelShell channel, Listener listener) {
        this.session = session;
        this.channel = channel;
        this.listener = listener;
    }

    /**
     * Connects, authenticates with the given credentials, and opens a PTY shell channel against
     * the fixed, server-configured target (never client-supplied -- docs/security.md).
     *
     * @throws IOException on connect failure, SSH auth failure, or channel-open failure. The
     *         caller is responsible for turning this into a safe (non-leaky) {@code error}
     *         frame -- see docs/security.md "Password leak paths".
     */
    public static SshBridge connect(String username, String password, Listener listener) throws IOException {
        AppConfig config = AppServices.config();
        return connect(username, password, config.targetHost, config.targetPort, listener);
    }

    /**
     * Same as {@link #connect(String, String, Listener)}, but against an explicit host/port
     * rather than the default {@code AppConfig.targetHost}/{@code targetPort}. Used by
     * {@code SshBridgeEndpoint} when a loaded {@code TargetResolver} module (see
     * com.sshomcat.module) resolves a different target for the authenticating user -- that
     * target is still server-side-curated, never client-supplied (docs/modules.md), so this
     * overload doesn't weaken the docs/security.md #1 invariant, it just parameterizes it.
     *
     * @throws IOException same conditions as {@link #connect(String, String, Listener)}.
     */
    public static SshBridge connect(String username, String password, String host, int port, Listener listener)
            throws IOException {
        AppConfig config = AppServices.config();
        SshClient client = AppServices.sshClient();

        ClientSession session = client.connect(username, host, port)
                .verify(config.sshConnectTimeoutMs)
                .getSession();
        try {
            session.addPasswordIdentity(password);
            session.auth().verify(config.sshAuthTimeoutMs);

            Map<String, Object> env = new HashMap<>();
            env.put("TERM", "xterm-256color");

            // The auth message carries no cols/rows (docs/api.md), so the PTY opens at a
            // default size; the frontend sends a `resize` immediately on `connected` to correct
            // it (see docs/architecture.md).
            PtyChannelConfiguration ptyConfig = new PtyChannelConfiguration();
            ptyConfig.setPtyType("xterm-256color");
            ptyConfig.setPtyColumns(PtyChannelConfigurationHolder.DEFAULT_COLUMNS_COUNT);
            ptyConfig.setPtyLines(PtyChannelConfigurationHolder.DEFAULT_ROWS_COUNT);
            ptyConfig.setPtyWidth(PtyChannelConfigurationHolder.DEFAULT_WIDTH);
            ptyConfig.setPtyHeight(PtyChannelConfigurationHolder.DEFAULT_HEIGHT);
            ptyConfig.setPtyModes(PtyChannelConfigurationHolder.DEFAULT_PTY_MODES);

            ChannelShell channel = session.createShellChannel(ptyConfig, env);

            // Merge stdout+stderr into one stream, matching "as a PTY would present it" (docs/api.md).
            // Forward bytes to the listener as they arrive instead of polling an input stream --
            // avoids a dedicated reader thread and any buffering-induced lag.
            OutputStream forwardingSink = new OutputStream() {
                @Override
                public void write(int b) {
                    listener.onData(new byte[] {(byte) b});
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    if (len <= 0) {
                        return;
                    }
                    byte[] copy = new byte[len];
                    System.arraycopy(b, off, copy, 0, len);
                    listener.onData(copy);
                }
            };
            channel.setOut(forwardingSink);
            channel.setErr(forwardingSink);

            channel.open().verify(config.sshConnectTimeoutMs);

            SshBridge bridge = new SshBridge(session, channel, listener);
            bridge.watchForClose();
            return bridge;
        } catch (IOException | RuntimeException e) {
            session.close(true);
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }
    }

    private void watchForClose() {
        Thread t = new Thread(() -> {
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
            // The remote end (e.g. the user typing `exit`) ended the channel on its own --
            // release our side's resources too (idempotent: a no-op if close() already ran)
            // and tell the endpoint so it closes the browser's WebSocket.
            close();
            notifyClosedOnce();
        }, "sshomcat-ssh-wait-close");
        t.setDaemon(true);
        t.start();
    }

    private void notifyClosedOnce() {
        if (notified.compareAndSet(false, true)) {
            listener.onClosed();
        }
    }

    /** Browser keystrokes/pasted input -> SSH channel stdin. */
    public void writeInput(byte[] bytes) {
        try {
            channel.getInvertedIn().write(bytes);
            channel.getInvertedIn().flush();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to write to SSH channel stdin (channel likely closing)", e);
        }
    }

    /** Propagates a terminal resize as an SSH window-change request. */
    public void resize(int cols, int rows) {
        try {
            channel.sendWindowChange(cols, rows, 0, 0);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to send window-change (channel likely closing)", e);
        }
    }

    /**
     * Tears down the channel and session. Idempotent; safe to call from the WS close handler
     * regardless of whether the SSH side already ended on its own (in which case this just
     * releases resources -- {@code notifyClosedOnce()} won't fire a second time either way).
     */
    @Override
    public void close() {
        if (teardownStarted.compareAndSet(false, true)) {
            try {
                channel.close(false);
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "Error closing SSH channel", e);
            }
            session.close(true);
        }
    }
}
