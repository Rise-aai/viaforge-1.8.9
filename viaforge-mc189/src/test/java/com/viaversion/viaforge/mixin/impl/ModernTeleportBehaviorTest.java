/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernTeleportBehaviorTest {

    @Test
    public void earlyTeleportAcknowledgementSuppressesTheLaterVanillaMovementPacket() throws Exception {
        final Path sourcePath = Paths.get(
                "src/main/java/com/viaversion/viaforge/mixin/impl/MixinNetHandlerPlayClient.java"
        );
        final String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        final String redirect = method(
                source,
                "private void viaforge$sendExactTeleportResponse",
                "private static boolean viaforge$isModernTarget"
        );

        assertTrue(source.contains("AtomicInteger viaforge$earlyTeleportResponses"));
        assertTrue(redirect.contains("viaforge$consumeEarlyTeleportResponse()"));
        assertFalse(source.contains("viaforge$sendRememberedTeleport"));
        assertFalse(source.contains("viaforge$flushTeleportAfterTransaction"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        final int start = source.indexOf(startMarker);
        final int end = source.indexOf(endMarker, start);
        assertTrue("Missing method start marker: " + startMarker, start >= 0);
        assertTrue("Missing method end marker: " + endMarker, end > start);
        return source.substring(start, end);
    }
}
