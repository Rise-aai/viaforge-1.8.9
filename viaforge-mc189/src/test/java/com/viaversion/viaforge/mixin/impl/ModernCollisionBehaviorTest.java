/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModernCollisionBehaviorTest {

    @Test
    public void horizontalCollisionUsesTheLargerRequestedAxisFirst() throws Exception {
        final Class<?> resolver;
        try {
            resolver = Class.forName("com.viaversion.viaforge.compat.ModernHorizontalCollision");
        } catch (ClassNotFoundException missing) {
            assertTrue("Modern horizontal collision resolver is missing", false);
            return;
        }
        final Class<?> operations = Class.forName(
                "com.viaversion.viaforge.compat.ModernHorizontalCollision$BoxOperations"
        );
        final Method resolve = resolver.getDeclaredMethod(
                "resolve",
                Object.class,
                Iterable.class,
                double.class,
                double.class,
                operations
        );
        resolve.setAccessible(true);

        final TestBox moving = new TestBox(0.0D, 0.0D, 1.0D, 1.0D);
        final TestBox corner = new TestBox(1.5D, 1.5D, 2.5D, 2.5D);
        final Object boxOperations = Proxy.newProxyInstance(
                operations.getClassLoader(),
                new Class<?>[]{operations},
                (proxy, method, args) -> {
                    if (method.getName().equals("offset")) {
                        return ((TestBox) args[0]).offset((double) args[1], (double) args[2]);
                    }
                    final TestBox obstacle = (TestBox) args[0];
                    final TestBox box = (TestBox) args[1];
                    final double movement = (double) args[2];
                    if (method.getName().equals("calculateXOffset")) {
                        return obstacle.calculateXOffset(box, movement);
                    }
                    if (method.getName().equals("calculateZOffset")) {
                        return obstacle.calculateZOffset(box, movement);
                    }
                    throw new AssertionError(method.getName());
                }
        );

        final double[] zFirst = result(resolve.invoke(
                null, moving, Collections.singletonList(corner), 1.0D, 2.0D, boxOperations));
        assertEquals(0.5D, zFirst[0], 0.0D);
        assertEquals(2.0D, zFirst[1], 0.0D);

        final double[] xFirst = result(resolve.invoke(
                null, moving, Collections.singletonList(corner), 2.0D, 1.0D, boxOperations));
        assertEquals(2.0D, xFirst[0], 0.0D);
        assertEquals(0.5D, xFirst[1], 0.0D);
    }

    @Test
    public void grazingCollisionUsesTheModernEightDegreeRule() throws Exception {
        final Class<?> resolver = Class.forName(
                "com.viaversion.viaforge.compat.ModernHorizontalCollision"
        );
        final Method isMinor = resolver.getDeclaredMethod(
                "isMinorCollision",
                double.class,
                double.class,
                float.class,
                float.class,
                double.class,
                double.class
        );
        isMinor.setAccessible(true);

        assertTrue((boolean) isMinor.invoke(null, 0.0D, 1.0D, 0.0F, 1.0F, 0.0D, 0.2D));
        assertTrue((boolean) isMinor.invoke(null, 1.0D, 0.0D, 0.0F, 1.0F, -0.2D, 0.0D));
        assertFalse((boolean) isMinor.invoke(null, 0.0D, 1.0D, 0.0F, 1.0F, 0.05D, 0.2D));
        assertFalse((boolean) isMinor.invoke(null, 0.0D, 1.0D, 0.0F, 0.0F, 0.0D, 0.2D));
        assertFalse((boolean) isMinor.invoke(null, 0.0D, 1.0D, 0.0F, 1.0F, 0.0D, 0.0D));
    }

    @Test
    public void sprintStopIgnoresMinorHorizontalCollisionForModernTargets() throws Exception {
        final String source = read("MixinEntityPlayerSP.java");

        assertTrue(source.contains("viaforge$modernBlockingHorizontalCollision"));
        assertTrue(source.contains("viaforge$isMinorHorizontalCollision"));
    }

    @Test
    public void minorCollisionUsesTheResolvedMovementVector() throws Exception {
        final String source = read("MixinEntity.java");
        final String update = method(
                source,
                "private void viaforge$updateMainSupportingBlock",
                "    @Unique\n    private static void viaforge$applyModernSoulSandFactor"
        );

        assertTrue(update.contains("player.posX - viaforge$moveStartX"));
        assertTrue(update.contains("player.posZ - viaforge$moveStartZ"));
        assertFalse(update.contains("viaforge$baseRequestedX"));
        assertFalse(update.contains("viaforge$baseRequestedZ"));
    }

    private static double[] result(Object result) throws Exception {
        return new double[]{
                (double) result.getClass().getMethod("getX").invoke(result),
                (double) result.getClass().getMethod("getZ").invoke(result)
        };
    }

    private static final class TestBox {
        private final double minX;
        private final double minZ;
        private final double maxX;
        private final double maxZ;

        private TestBox(double minX, double minZ, double maxX, double maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        private double calculateXOffset(TestBox moving, double movement) {
            if (moving.maxZ <= minZ || moving.minZ >= maxZ) {
                return movement;
            }
            if (movement > 0.0D && moving.maxX <= minX) {
                return Math.min(minX - moving.maxX, movement);
            }
            if (movement < 0.0D && moving.minX >= maxX) {
                return Math.max(maxX - moving.minX, movement);
            }
            return movement;
        }

        private double calculateZOffset(TestBox moving, double movement) {
            if (moving.maxX <= minX || moving.minX >= maxX) {
                return movement;
            }
            if (movement > 0.0D && moving.maxZ <= minZ) {
                return Math.min(minZ - moving.maxZ, movement);
            }
            if (movement < 0.0D && moving.minZ >= maxZ) {
                return Math.max(maxZ - moving.minZ, movement);
            }
            return movement;
        }

        private TestBox offset(double x, double z) {
            return new TestBox(minX + x, minZ + z, maxX + x, maxZ + z);
        }
    }

    @Test
    public void tiedSupportingBlocksUseModernYZXOrdering() throws Exception {
        final String source = read("MixinEntity.java");
        final String method = method(source, "private static boolean viaforge$hasSupportingPriority", "    /** 1.14+");

        assertTrue(method.contains("first.getY() > second.getY()"));
        assertTrue(method.contains("first.getZ() > second.getZ()"));
        assertTrue(method.contains("first.getX() > second.getX()"));
        assertFalse(method.contains("horizontalSum"));
    }

    @Test
    public void soulSandLegacyCallbackIsReplacedByOneModernSpeedFactor() throws Exception {
        final String entity = read("MixinEntity.java");
        final String soulSand = read("MixinBlockSoulSand.java");
        final String mixins = new String(Files.readAllBytes(Paths.get("src/main/resources/mixins.viaforge.json")), StandardCharsets.UTF_8);

        assertTrue(soulSand.contains("ci.cancel()"));
        assertTrue(entity.contains("if (modernApplies)"));
        assertFalse(entity.contains("legacyAlreadyApplied"));
        assertTrue(mixins.contains("\"MixinBlockSoulSand\""));
    }

    @Test
    public void lilyPadUsesTheModernInsetAndHeight() throws Exception {
        final String lily = read("MixinBlockLilyPad.java");
        final String mixins = new String(Files.readAllBytes(Paths.get("src/main/resources/mixins.viaforge.json")), StandardCharsets.UTF_8);

        assertTrue(lily.contains("1.0D / 16.0D"));
        assertTrue(lily.contains("15.0D / 16.0D"));
        assertTrue(lily.contains("1.5D / 16.0D"));
        assertTrue(mixins.contains("\"MixinBlockLilyPad\""));
    }

    @Test
    public void fluidPushUsesModernHeightBasedFlow() throws Exception {
        final String source = read("MixinEntity.java");
        final String fluidLoop = method(source, "if (block instanceof BlockLiquid", "viaforge$applyModernFluidPush(player");

        assertTrue(fluidLoop.contains("ModernFluidPhysics.getFlow"));
        assertFalse(fluidLoop.contains("modifyAcceleration"));
    }

    private static String read(String file) throws Exception {
        final Path path = Paths.get("src/main/java/com/viaversion/viaforge/mixin/impl", file);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String method(String source, String start, String end) {
        final int startIndex = source.indexOf(start);
        final int endIndex = source.indexOf(end, startIndex);
        return source.substring(startIndex, endIndex);
    }
}
