package com.sshomcat;

import com.sshomcat.config.AppConfig;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

/**
 * Process-wide singletons: the validated config, one shared (started once) MINA SSHD
 * {@link SshClient}, and one shared {@link ScheduledExecutorService} for per-session timeout
 * timers (auth grace period, max session duration).
 *
 * <p>Jakarta WebSocket endpoints are instantiated per-connection with no built-in dependency
 * injection into a shared ServletContext, so {@code AppContextListener} populates these static
 * fields at webapp startup and {@code SshBridgeEndpoint} reads them directly. Deliberately a
 * plain static holder rather than CDI/Spring -- this webapp has no other need for a DI
 * container (see docs/architecture.md: no framework where one isn't earning its keep).
 */
public final class AppServices {

    private static volatile AppConfig config;
    private static volatile SshClient sshClient;
    private static volatile ScheduledExecutorService scheduler;

    private AppServices() {
    }

    public static void init(AppConfig cfg) {
        config = cfg;

        ServerKeyVerifier verifier;
        if ("known-hosts".equals(cfg.hostKeyVerification)) {
            // The delegate is what's consulted for a host key NOT already in the file --
            // RejectAllServerKeyVerifier here means "must already be pinned", i.e. real
            // verification. (A delegate of AcceptAllServerKeyVerifier would silently accept any
            // unknown/changed key -- trust-on-first-use, not what docs/security.md promises.)
            verifier = new KnownHostsServerKeyVerifier(
                    RejectAllServerKeyVerifier.INSTANCE, java.nio.file.Path.of(cfg.knownHostsFile));
        } else {
            // Local-dev-only default; see docs/security.md "Host key verification" note.
            verifier = AcceptAllServerKeyVerifier.INSTANCE;
        }

        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(verifier);
        client.start();
        sshClient = client;

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "sshomcat-session-timer");
            t.setDaemon(true);
            return t;
        });
    }

    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (sshClient != null) {
            sshClient.stop();
            sshClient = null;
        }
        config = null;
    }

    public static AppConfig config() {
        AppConfig c = config;
        if (c == null) {
            throw new IllegalStateException("AppServices not initialized (AppContextListener didn't run?)");
        }
        return c;
    }

    public static SshClient sshClient() {
        SshClient c = sshClient;
        if (c == null) {
            throw new IllegalStateException("AppServices not initialized (AppContextListener didn't run?)");
        }
        return c;
    }

    public static ScheduledExecutorService scheduler() {
        ScheduledExecutorService s = scheduler;
        if (s == null) {
            throw new IllegalStateException("AppServices not initialized (AppContextListener didn't run?)");
        }
        return s;
    }
}
