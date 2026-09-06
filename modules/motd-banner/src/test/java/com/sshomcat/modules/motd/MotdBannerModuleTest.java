package com.sshomcat.modules.motd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MotdBannerModuleTest {

    @AfterEach
    void clear() {
        System.clearProperty("motdBanner.message");
    }

    @Test
    void nameIsStable() {
        assertEquals("motd-banner", new MotdBannerModule().name());
    }

    @Test
    void initDoesNotThrowWithoutConfiguredMessage() {
        new MotdBannerModule().init();
    }

    @Test
    void initDoesNotThrowWithConfiguredMessage() {
        System.setProperty("motdBanner.message", "custom banner");
        new MotdBannerModule().init();
    }
}
