package com.sshomcat.modules.multitarget;

import com.sshomcat.module.TargetResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves an SSH target per authenticated username against a small, admin-curated allowlist
 * file.
 *
 * <p>Allowlist format: a Java properties file, one line per user, {@code username=host:port}.
 * A username with no entry falls through to sshomcat's own configured default target (this
 * module returns {@link Optional#empty()} for it, per the TargetResolver contract).
 */
public final class MultiTargetRouterModule implements TargetResolver {

    private static final Logger LOG = Logger.getLogger(MultiTargetRouterModule.class.getName());

    private final Map<String, TargetResolver.Target> allowlist = new HashMap<>();

    @Override
    public String name() {
        return "multi-target-router";
    }

    @Override
    public void init() {
        String path = System.getProperty("multiTargetRouter.allowlistFile",
                "/opt/sshomcat/modules/multi-target-router.properties");
        Path file = Path.of(path);
        if (!Files.isReadable(file)) {
            LOG.warning(() -> "multi-target-router: allowlist file not readable, module will resolve nothing: " + path);
            return;
        }
        loadAllowlistFrom(file);
    }

    private void loadAllowlistFrom(Path file) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "multi-target-router: failed to read allowlist file: " + file, e);
            return;
        }
        for (String user : p.stringPropertyNames()) {
            String raw = p.getProperty(user).trim();
            int colon = raw.lastIndexOf(':');
            if (colon <= 0) {
                LOG.warning(() -> "multi-target-router: skipping malformed entry for user '" + user + "': " + raw);
                continue;
            }
            try {
                String host = raw.substring(0, colon);
                int port = Integer.parseInt(raw.substring(colon + 1));
                allowlist.put(user, new TargetResolver.Target(host, port));
            } catch (RuntimeException e) {
                LOG.warning(() -> "multi-target-router: skipping malformed entry for user '" + user + "': " + raw);
            }
        }
        LOG.info(() -> "multi-target-router: loaded " + allowlist.size() + " user->target mapping(s) from " + file);
    }

    @Override
    public Optional<Target> resolveTarget(String username) {
        return Optional.ofNullable(allowlist.get(username));
    }
}
