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

public class ModernFluidSprintBehaviorTest {

    @Test
    public void flowingWaterSprintUsesModernFluidSurfaceForUnderwaterState() throws Exception {
        final Path sourcePath = Paths.get(
                "src/main/java/com/viaversion/viaforge/mixin/impl/MixinEntityPlayerSP.java"
        );
        final String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        final int start = source.indexOf("if (player.isSprinting()", source.indexOf("keepSwimmingSprint"));
        final int end = source.indexOf("if (player.isInWater()", start + 1);
        final String stopSprint = source.substring(start, end);

        assertTrue(stopSprint.contains("!viaforge$isModernEyeInWater(player)"));
        assertFalse(stopSprint.contains("isInsideOfMaterial(Material.water)"));
    }
}
