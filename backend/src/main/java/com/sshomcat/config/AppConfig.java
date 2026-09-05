package com.sshomcat.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * SSHomcat backend configuration.
 *
 * <p>Loaded once at startup by {@code AppContextListener}. Source, in priority order:
 * <ol>
 *   <li>the file named by the {@code sshomcat.config} system property, if set</li>
 *   <li>{@code /sshomcat.properties} on the classpath (i.e. {@code WEB-INF/classes/sshomcat.properties})</li>
 * </ol>
 *
 * <p>Per docs/security.md, real deployment config is never checked into version control --
 * only {@code sshomcat.properties.example} is. {@link #load()} fails fast (throws) if required
 * keys are missing/invalid, per docs/roadmap.md M2 (pulled forward into M1 since it's cheap and
 * avoids a confusing failure on first WebSocket connection).
 */
public final class AppConfig {

    public final String targetHost;
    public final int targetPort;

    public final long idleTimeoutMs;
    public final long maxSessionDurationMs;
    public final long authGraceMs;

    public final long sshConnectTimeoutMs;
    public final long sshAuthTimeoutMs;

    public final String hostKeyVerification; // "accept-all" | "known-hosts"
    public final String knownHostsFile; // used only when hostKeyVerification == known-hosts

    private AppConfig(Properties p) {
        this.targetHost = requireNonBlank(p, "target.host");
        this.targetPort = parseIntInRange(p, "target.port", 22, 1, 65535);

        this.idleTimeoutMs = parseLongMin(p, "session.idleTimeoutMs", 600_000L, 1_000L);
        this.maxSessionDurationMs = parseLongMin(p, "session.maxDurationMs", 3_600_000L, 1_000L);
        this.authGraceMs = parseLongMin(p, "session.authGraceMs", 5_000L, 500L);

        this.sshConnectTimeoutMs = parseLongMin(p, "ssh.connectTimeoutMs", 10_000L, 500L);
        this.sshAuthTimeoutMs = parseLongMin(p, "ssh.authTimeoutMs", 10_000L, 500L);

        String verification = p.getProperty("ssh.hostKeyVerification", "accept-all").trim().toLowerCase();
        if (!verification.equals("accept-all") && !verification.equals("known-hosts")) {
            throw new IllegalStateException(
                    "Invalid ssh.hostKeyVerification '" + verification + "': must be 'accept-all' or 'known-hosts'");
        }
        this.hostKeyVerification = verification;
        this.knownHostsFile = p.getProperty("ssh.knownHostsFile", "").trim();
        if (verification.equals("known-hosts") && knownHostsFile.isEmpty()) {
            throw new IllegalStateException(
                    "ssh.hostKeyVerification=known-hosts requires ssh.knownHostsFile to be set");
        }
    }

    /** Loads and validates configuration. Throws {@link IllegalStateException} on any problem. */
    public static AppConfig load() {
        Properties p = new Properties();
        String explicitPath = System.getProperty("sshomcat.config");
        try {
            if (explicitPath != null && !explicitPath.isBlank()) {
                Path path = Path.of(explicitPath);
                if (!Files.isReadable(path)) {
                    throw new IllegalStateException(
                            "sshomcat.config system property points at an unreadable file: " + explicitPath);
                }
                try (InputStream in = Files.newInputStream(path)) {
                    p.load(in);
                }
            } else {
                try (InputStream in = AppConfig.class.getResourceAsStream("/sshomcat.properties")) {
                    if (in == null) {
                        throw new IllegalStateException(
                                "No configuration found. Provide either -Dsshomcat.config=<path> or "
                                        + "WEB-INF/classes/sshomcat.properties (see sshomcat.properties.example).");
                    }
                    p.load(in);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sshomcat configuration: " + e.getMessage(), e);
        }
        return new AppConfig(p);
    }

    private static String requireNonBlank(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return v.trim();
    }

    private static int parseIntInRange(Properties p, String key, int def, int min, int max) {
        String raw = p.getProperty(key);
        int v;
        if (raw == null || raw.isBlank()) {
            v = def;
        } else {
            try {
                v = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Config key '" + key + "' is not a valid integer: " + raw);
            }
        }
        if (v < min || v > max) {
            throw new IllegalStateException("Config key '" + key + "' must be between " + min + " and " + max
                    + ", got: " + v);
        }
        return v;
    }

    private static long parseLongMin(Properties p, String key, long def, long min) {
        String raw = p.getProperty(key);
        long v;
        if (raw == null || raw.isBlank()) {
            v = def;
        } else {
            try {
                v = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Config key '" + key + "' is not a valid number: " + raw);
            }
        }
        if (v < min) {
            throw new IllegalStateException("Config key '" + key + "' must be >= " + min + ", got: " + v);
        }
        return v;
    }
}
