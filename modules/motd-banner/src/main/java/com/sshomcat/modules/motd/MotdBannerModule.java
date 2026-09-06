package com.sshomcat.modules.motd;

import com.sshomcat.module.SshomcatModule;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Logs a one-line message-of-the-day banner at startup. No protocol hooks, no state. */
public final class MotdBannerModule implements SshomcatModule {

    private static final Logger LOG = Logger.getLogger(MotdBannerModule.class.getName());

    private static final List<String> DEFAULT_QUOTES = List.of(
            "SSHomcat is up.",
            "Gateway online. Mind the timeout.",
            "One target, many sessions."
    );

    @Override
    public String name() {
        return "motd-banner";
    }

    @Override
    public void init() {
        String configured = System.getProperty("motdBanner.message");
        String message = (configured != null && !configured.isBlank())
                ? configured
                : DEFAULT_QUOTES.get(ThreadLocalRandom.current().nextInt(DEFAULT_QUOTES.size()));
        LOG.info(() -> "===== " + message + " =====");
    }
}
