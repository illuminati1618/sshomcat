package com.sshomcat.module;

import java.util.Optional;

/**
 * A module that can resolve the outbound SSH target for an authenticating user, instead of
 * always using the one fixed {@code target.host}/{@code target.port} from {@code AppConfig}.
 *
 * <p><b>This does not reintroduce a client-controlled-target (SSRF) risk.</b> {@code resolveTarget} only ever receives the username from an
 * already-parsed {@code auth} message -- never a client-supplied host or port -- and looks it up
 * against whatever server-side data the module itself owns (a config file, a small database,
 * whatever it implements). Docs/security.md's constraint #1 ("the target is fixed, server-side
 * configuration -- never client-supplied") still holds; this only changes "fixed" from "exactly
 * one value" to "one of several values a server-side admin curated ahead of time," matching
 * docs/roadmap.md M3's own "small server-side allowlist, not an open relay" framing.
 *
 * <p>{@code SshBridgeEndpoint} tries every loaded {@code TargetResolver}, in load order, and uses
 * the first {@link Optional#isPresent()} result. If none resolve anything for this user (no
 * resolver modules loaded, or every one returns {@link Optional#empty()}), it falls back to
 * {@code AppConfig.targetHost}/{@code targetPort} exactly as before this interface existed.
 */
public interface TargetResolver extends SshomcatModule {

    Optional<Target> resolveTarget(String username);

    /** An SSH target. Validated at construction so a bad resolver can't hand back garbage. */
    record Target(String host, int port) {
        public Target {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Target host must not be blank");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Target port out of range: " + port);
            }
        }
    }
}
