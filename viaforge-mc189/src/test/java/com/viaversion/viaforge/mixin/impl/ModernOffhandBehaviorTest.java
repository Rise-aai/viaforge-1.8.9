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

public class ModernOffhandBehaviorTest {

    @Test
    public void blockOffhandRunsOnlyAfterMainHandDidNotHandleTheBlock() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");

        assertTrue(source.contains(
                "@Inject(method = \"onPlayerRightClick\", at = @At(\"RETURN\"), cancellable = true, require = 0)"
        ));
        assertTrue(source.contains("if (Boolean.TRUE.equals(cir.getReturnValue()))"));
    }

    @Test
    public void emptyMainHandAirClickCanUseOffhand() throws Exception {
        final String source = readMainSource("MixinMinecraft.java");

        assertTrue(source.contains("viaforge$rightClickOffhandAir"));
        assertTrue(source.contains("ModernOffhandInteraction.sendUseItem(thePlayer)"));
    }

    @Test
    public void firstPersonOffhandUsesCompactMirroredTransform() throws Exception {
        final String source = readMainSource("MixinItemRenderer.java");

        assertTrue(source.contains("GlStateManager.translate(-0.56F, -0.52F, -0.72F);"));
        assertTrue(source.contains("GlStateManager.scale(-0.4F, 0.4F, 0.4F);"));
        assertFalse(source.contains("GlStateManager.rotate("));
    }

    private static String readMainSource(String fileName) throws Exception {
        final Path path = Paths.get(
                "src/main/java/com/viaversion/viaforge/mixin/impl",
                fileName
        );
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
