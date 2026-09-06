package com.sshomcat.modules.multitarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sshomcat.module.TargetResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiTargetRouterModuleTest {

    @TempDir
    Path tempDir;

    private String previousSystemProperty;

    @BeforeEach
    void save() {
        previousSystemProperty = System.getProperty("multiTargetRouter.allowlistFile");
    }

    @AfterEach
    void restore() {
        if (previousSystemProperty == null) {
            System.clearProperty("multiTargetRouter.allowlistFile");
        } else {
            System.setProperty("multiTargetRouter.allowlistFile", previousSystemProperty);
        }
    }

    @Test
    void resolvesConfiguredUser() throws IOException {
        Path allowlist = tempDir.resolve("allowlist.properties");
        Files.writeString(allowlist, "alice=10.0.0.5:2222\n");
        System.setProperty("multiTargetRouter.allowlistFile", allowlist.toString());

        MultiTargetRouterModule module = new MultiTargetRouterModule();
        module.init();

        Optional<TargetResolver.Target> resolved = module.resolveTarget("alice");
        assertTrue(resolved.isPresent());
        assertEquals("10.0.0.5", resolved.get().host());
        assertEquals(2222, resolved.get().port());
    }

    @Test
    void returnsEmptyForUnknownUser() throws IOException {
        Path allowlist = tempDir.resolve("allowlist.properties");
        Files.writeString(allowlist, "alice=10.0.0.5:2222\n");
        System.setProperty("multiTargetRouter.allowlistFile", allowlist.toString());

        MultiTargetRouterModule module = new MultiTargetRouterModule();
        module.init();

        assertFalse(module.resolveTarget("nobody").isPresent());
    }

    @Test
    void missingAllowlistFileResolvesNothingRatherThanThrowing() {
        System.setProperty("multiTargetRouter.allowlistFile", tempDir.resolve("does-not-exist.properties").toString());

        MultiTargetRouterModule module = new MultiTargetRouterModule();
        module.init();

        assertFalse(module.resolveTarget("anyone").isPresent());
    }
}
