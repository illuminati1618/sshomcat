package com.sshomcat.ws;

import com.sshomcat.AppServices;
import com.sshomcat.config.AppConfig;
import com.sshomcat.module.TargetResolver;
import com.sshomcat.ssh.SshBridge;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The SSH bridge, per docs/architecture.md and the state machine in docs/api.md. One instance
 * per WebSocket connection (default Jakarta WebSocket lifecycle for an annotated endpoint), so
 * instance fields are connection-scoped -- no shared mutable state between browsers.
 */
@ServerEndpoint("/ws/ssh")
public class SshBridgeEndpoint {

    private static final Logger LOG = Logger.getLogger(SshBridgeEndpoint.class.getName());

    /** Matches docs/api.md "Message size limits": start at 64 KB. */
    private static final int MAX_MESSAGE_BYTES = 65536;

    private Session wsSession;
    private SafeSender sender;
    private volatile SshBridge sshBridge;
    private volatile boolean authenticated = false;
    private volatile ScheduledFuture<?> authGraceTask;
    private volatile ScheduledFuture<?> maxDurationTask;

    @OnOpen
    public void onOpen(Session session) {
        this.wsSession = session;
        this.sender = new SafeSender(session);

        AppConfig config = AppServices.config();
        session.setMaxIdleTimeout(config.idleTimeoutMs);
        // Tomcat's default text-message buffer (8KB) is well under our 64KB protocol limit
        // (docs/api.md) -- a base64'd paste bigger than 8KB would otherwise die with a
        // MessageTooBigException the @OnMessage annotation's maxMessageSize alone doesn't stop.
        session.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);

        // "If nothing valid arrives within a short grace period, close" (docs/architecture.md).
        authGraceTask = AppServices.scheduler().schedule(() -> {
            if (!authenticated) {
                closeSession(CloseCodes.VIOLATED_POLICY, "auth timeout");
            }
        }, config.authGraceMs, TimeUnit.MILLISECONDS);
    }

    @OnMessage(maxMessageSize = MAX_MESSAGE_BYTES)
    public void onMessage(String raw) {
        InboundMessage msg;
        try {
            msg = ProtocolCodec.parseInbound(raw);
        } catch (ProtocolException e) {
            closeSession(CloseCodes.VIOLATED_POLICY, "malformed message");
            return;
        }

        if (msg instanceof InboundMessage.Auth auth) {
            handleAuth(auth);
            return;
        }
        if (!authenticated) {
            closeSession(CloseCodes.VIOLATED_POLICY, "message before auth");
            return;
        }
        if (msg instanceof InboundMessage.Data data) {
            handleData(data);
        } else if (msg instanceof InboundMessage.Resize resize) {
            SshBridge bridge = this.sshBridge;
            if (bridge != null) {
                bridge.resize(resize.cols(), resize.rows());
            }
        }
    }

    private void handleAuth(InboundMessage.Auth auth) {
        if (authenticated) {
            closeSession(CloseCodes.VIOLATED_POLICY, "auth already sent");
            return;
        }
        // Mark as received before the SSH attempt even starts: the grace period is about
        // *receiving a valid auth message*, not about the eventual SSH outcome.
        authenticated = true;
        cancelAuthGrace();

        // Give any loaded TargetResolver module (com.sshomcat.module -- see docs/modules.md) a
        // chance to pick a different, still server-side-curated target for this user; fall back
        // to the single configured target exactly as before if none resolve anything. The
        // username here comes from the parsed `auth` message, never a client-supplied host/port,
        // so this doesn't touch the docs/security.md #1 invariant.
        AppConfig config = AppServices.config();
        String targetHost = config.targetHost;
        int targetPort = config.targetPort;
        for (TargetResolver resolver : AppServices.modules().modulesOfType(TargetResolver.class)) {
            Optional<TargetResolver.Target> resolved = resolver.resolveTarget(auth.username());
            if (resolved.isPresent()) {
                targetHost = resolved.get().host();
                targetPort = resolved.get().port();
                break;
            }
        }

        try {
            SshBridge.Listener listener = new SshBridge.Listener() {
                @Override
                public void onData(byte[] bytes) {
                    sender.send(ProtocolCodec.data(bytes));
                }

                @Override
                public void onClosed() {
                    closeSession(CloseCodes.NORMAL_CLOSURE, "ssh session ended");
                }
            };
            SshBridge bridge = SshBridge.connect(auth.username(), auth.password(), targetHost, targetPort, listener);
            this.sshBridge = bridge;
            sender.send(ProtocolCodec.connected());
            scheduleMaxDuration();
            LOG.info(() -> "SSH session established for user=" + auth.username());
        } catch (IOException e) {
            // Never forward library/exception text to the browser (docs/security.md); log only
            // the exception type, not getMessage(), as an extra guard against any incidental
            // credential leakage in a dependency's error message.
            LOG.log(Level.WARNING, () -> "SSH connect/auth failed for user=" + auth.username()
                    + ": " + e.getClass().getSimpleName());
            sender.send(ProtocolCodec.error("Authentication failed or target unreachable."));
            closeSession(CloseCodes.VIOLATED_POLICY, "ssh connect/auth failed");
        }
    }

    private void handleData(InboundMessage.Data data) {
        try {
            byte[] bytes = ProtocolCodec.decodeData(data);
            SshBridge bridge = this.sshBridge;
            if (bridge != null) {
                bridge.writeInput(bytes);
            }
        } catch (ProtocolException e) {
            closeSession(CloseCodes.VIOLATED_POLICY, "invalid data payload");
        }
    }

    private void scheduleMaxDuration() {
        AppConfig config = AppServices.config();
        maxDurationTask = AppServices.scheduler().schedule(() -> {
            sender.send(ProtocolCodec.error("Maximum session duration exceeded."));
            closeSession(CloseCodes.NORMAL_CLOSURE, "max session duration exceeded");
        }, config.maxSessionDurationMs, TimeUnit.MILLISECONDS);
    }

    @OnClose
    public void onClose() {
        cancelAuthGrace();
        cancelMaxDuration();
        SshBridge bridge = this.sshBridge;
        if (bridge != null) {
            bridge.close();
        }
    }

    @OnError
    public void onError(Session session, Throwable t) {
        LOG.log(Level.FINE, "WebSocket error", t);
        closeSession(CloseCodes.VIOLATED_POLICY, "protocol error");
    }

    private void cancelAuthGrace() {
        ScheduledFuture<?> f = authGraceTask;
        if (f != null) {
            f.cancel(false);
        }
    }

    private void cancelMaxDuration() {
        ScheduledFuture<?> f = maxDurationTask;
        if (f != null) {
            f.cancel(false);
        }
    }

    private void closeSession(CloseCodes code, String reason) {
        try {
            if (wsSession.isOpen()) {
                wsSession.close(new CloseReason(code, reason));
            }
        } catch (IOException ignored) {
            // Session is going away regardless; nothing meaningful to do with this.
        }
    }
}
