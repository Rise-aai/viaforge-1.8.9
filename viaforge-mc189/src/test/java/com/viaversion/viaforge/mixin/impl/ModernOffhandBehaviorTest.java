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
        assertTrue(source.contains("thePlayer.inventory.getCurrentItem() != null"));
        assertTrue(source.contains("ModernOffhandInteraction.sendUseItem(thePlayer)"));
    }

    @Test
    public void offhandRemainsAvailableWhileTheMainHandIsOccupied() throws Exception {
        final String source = readCompatSource("ModernOffhandInteraction.java");
        final String method = method(source, "public static boolean hasOffhand", "public static ItemStack getOffhand");

        assertTrue(method.contains("getOffhand(player) != null"));
        assertFalse(method.contains("getCurrentItem()"));
    }

    @Test
    public void airUseFallsBackToOffhandOnlyAfterMainHandPasses() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");
        final String method = method(source, "private void viaforge$rightClickOffhandAir", "@Inject(method = \"interactWithEntitySendPacket\"");

        assertTrue(source.contains("@Inject(method = \"sendUseItem\", at = @At(\"RETURN\"), cancellable = true, require = 0)"));
        assertTrue(method.contains("Boolean.TRUE.equals(cir.getReturnValue())"));
        assertTrue(method.contains("ModernOffhandInteraction.sendUseItem((EntityPlayerSP) player)"));
    }

    @Test
    public void offhandFallbackClearsARejectedLegacyMainHandUseState() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");
        final String method = method(source, "private void viaforge$rightClickOffhandAir", "@Inject(method = \"interactWithEntitySendPacket\"");

        assertTrue(method.contains("player.getItemInUse() == stack"));
        assertTrue(method.contains("stack.getItemUseAction() != EnumAction.BLOCK"));
        assertTrue(method.contains("player.clearItemInUse()"));
    }

    @Test
    public void offhandSwapUsesTheModernActionPacketInsteadOfLegacySlotClick() throws Exception {
        final String source = readMainSource("MixinMinecraft.java");

        assertTrue(source.contains("ModernOffhandInteraction.sendSwapItemWithOffhand(thePlayer)"));
        assertFalse(source.contains("playerController.windowClick("));
    }

    @Test
    public void syntheticOffhandPacketsUseWireCompatibleOneNineFieldTypes() throws Exception {
        final String source = readCompatSource("ModernOffhandInteraction.java");
        final String swap = method(source, "public static boolean sendSwapItemWithOffhand", "public static boolean sendUseItem");
        final String block = method(source, "public static boolean sendUseItemOnBlock", "public static void sendInteract");

        assertTrue(swap.contains("wrapper.write(Types.UNSIGNED_BYTE, (short) 255)"));
        assertFalse(swap.contains("wrapper.write(Types.BYTE"));
        assertTrue(block.contains("writeCursorPosition(wrapper"));
        assertFalse(block.contains("wrapper.write(Types.FLOAT"));
    }

    @Test
    public void onlyDiggingPlayerActionsReceiveAFreshSequence() throws Exception {
        final String source = readCompatSource("ModernSequenceEncodeHandler.java");
        final String action = method(source, "} else if (packetId == ServerboundPackets1_20_5.PLAYER_ACTION.getId())", "} else {");

        assertTrue(action.contains("action != 0 && action != 2"));
        assertTrue(action.contains("Types.VAR_INT.readPrimitive(input)"));
    }

    @Test
    public void offhandUseSurvivesLegacyMainHandChecksAndFinishesInSlotFortyFive() throws Exception {
        final String source = readMainSource("MixinEntityPlayer.java");

        assertTrue(source.contains("method = \"onUpdate\""));
        assertTrue(source.contains("target = \"Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;\""));
        assertTrue(source.contains("itemInUse == offhand"));
        assertTrue(source.contains("viaforge$setOffhand"));
        assertTrue(source.contains("ci.cancel()"));
    }

    @Test
    public void activeOffhandFoodUsePreventsTheMainHandFromRestartingSwordBlock() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");
        final String method = method(source, "private void viaforge$preserveActiveOffhandUse", "@Inject(method = \"onPlayerRightClick\"");

        assertTrue(source.contains("private void viaforge$preserveActiveOffhandUse"));
        assertTrue(source.contains("@Inject(method = \"sendUseItem\", at = @At(\"HEAD\"), cancellable = true, require = 0)"));
        assertTrue(method.contains("player.getItemInUse() != ModernOffhandInteraction.getOffhand(player)"));
        assertTrue(method.contains("stack.getItemUseAction() != EnumAction.BLOCK"));
        assertTrue(method.contains("cir.setReturnValue(false)"));
    }

    @Test
    public void modernAttackQueuesMainHandAnimationImmediatelyAfterAttack() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");
        final String method = method(source, "private void viaforge$sendModernAttackThenAnimation", "@Inject(method = \"attackEntity\", at = @At(\"RETURN\"");

        assertTrue(source.contains("@Redirect("));
        assertTrue(method.contains("handler.addToSendQueue(packet)"));
        assertTrue(method.contains("player.swingItem()"));
        assertTrue(method.indexOf("handler.addToSendQueue(packet)") < method.indexOf("player.swingItem()"));
    }

    @Test
    public void offhandSlotUpdatesUseAnIgnoredLegacyWindowMarker() throws Exception {
        final String rewriter = readMainSource("MixinViaRewindBlockItemPacketRewriter.java");
        final String handler = readMainSource("MixinNetHandlerPlayClient.java");
        final String keepSlot = method(rewriter, "private void viaforge$keepOffhandSlot", "@Inject(method = \"lambda$registerPackets$2\"");

        assertTrue(keepSlot.contains("wrapper.is(Types.BYTE, 0)"));
        assertTrue(keepSlot.contains("wrapper.set(Types.BYTE, 0, ModernOffhandStorage.CLIENT_WINDOW_ID)"));
        assertTrue(handler.contains("packet.func_149175_c() == ModernOffhandStorage.CLIENT_WINDOW_ID"));
    }

    @Test
    public void offhandSlotUpdateConfirmsFoodUseCompletion() throws Exception {
        final String source = readMainSource("MixinNetHandlerPlayClient.java");
        final String method = method(source, "private void viaforge$confirmHeldItemUseFinished", "@Inject(method = \"handleJoinGame\"");

        assertTrue(method.contains("offhandUpdate"));
        assertTrue(method.contains("ModernOffhandStorage.CLIENT_WINDOW_ID"));
        assertTrue(method.contains("slot == 45"));
    }

    @Test
    public void blockFallbackContinuesIntoUsableOffhandItem() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");

        assertTrue(source.contains("ModernOffhandInteraction.shouldUseItemAfterBlock(player)"));
        assertTrue(source.contains("ModernOffhandInteraction.sendUseItem(player)"));
    }

    @Test
    public void firstPersonOffhandReusesVanillaTransformAndLighting() throws Exception {
        final String source = readMainSource("MixinItemRenderer.java");

        assertTrue(source.contains("viaforge$getOffhandSwingProgress(partialTicks)"));
        assertTrue(source.contains("GlStateManager.scale(-1.0F, 1.0F, 1.0F);"));
        assertTrue(source.contains("RenderHelper.enableStandardItemLighting();"));
        assertTrue(source.contains("viaforge$performDrinking(mc.thePlayer, partialTicks);"));
        assertTrue(source.contains("GlStateManager.disableCull();"));
        assertTrue(source.contains("GlStateManager.enableCull();"));
        assertFalse(source.contains("GlStateManager.translate("));
    }

    @Test
    public void offhandBlockActionSuppressesTheVanillaMainHandSwing() throws Exception {
        final String source = readMainSource("MixinMinecraft.java");

        assertTrue(source.contains("ModernOffhandInteraction.beginRightClick()"));
        assertTrue(source.contains("method = \"rightClickMouse\""));
        assertTrue(source.contains("ModernOffhandInteraction.wasClientOffhandAction()"));
    }

    @Test
    public void startingFoodUseAppliesTheLocalItemResultWithoutASwingPacket() throws Exception {
        final String source = readCompatSource("ModernOffhandInteraction.java");
        final String method = method(source, "public static boolean sendUseItem(EntityPlayerSP player)", "public static boolean sendUseItemOnBlock");

        assertTrue(method.contains("stack.useItemRightClick(player.worldObj, player)"));
        assertFalse(method.contains("player.setItemInUse("));
        assertFalse(method.contains("sendSwing("));
    }

    @Test
    public void simultaneousAttackStillRunsTheNormalHandUseFallback() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");
        final String method = method(source, "private void viaforge$modernEntityInteraction", "@Unique\n    private static Vec3 viaforge$clampInteractionHit");
        final int attackBranch = method.indexOf("keyBindAttack.isKeyDown()");

        assertTrue(attackBranch >= 0);
        assertTrue(method.substring(attackBranch).contains("viaforge$useItemWhileAttacking(player)"));
    }

    @Test
    public void entityInteractionTriesMainHandBeforeOffhand() throws Exception {
        final String source = readMainSource("MixinPlayerControllerMP.java");
        final String fallback = method(source, "private void viaforge$rightClickOffhandEntity", "@Unique\n    private double viaforge$motionBeforeAttackX");

        assertTrue(source.contains("@Inject(method = \"interactWithEntitySendPacket\", at = @At(\"RETURN\"), cancellable = true, require = 0)"));
        assertTrue(fallback.contains("Boolean.TRUE.equals(cir.getReturnValue())"));
        assertTrue(fallback.contains("viaforge$pendingOffhandEntityHit"));
        assertTrue(fallback.contains("ModernOffhandInteraction.sendInteractAt"));
        assertTrue(fallback.contains("ModernOffhandInteraction.sendInteract"));
    }

    private static String readMainSource(String fileName) throws Exception {
        final Path path = Paths.get(
                "src/main/java/com/viaversion/viaforge/mixin/impl",
                fileName
        );
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String readCompatSource(String fileName) throws Exception {
        final Path path = Paths.get(
                "src/main/java/com/viaversion/viaforge/compat",
                fileName
        );
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String method(String source, String startMarker, String endMarker) {
        final int start = source.indexOf(startMarker);
        final int end = source.indexOf(endMarker, start);
        assertTrue("Missing method start marker: " + startMarker, start >= 0);
        assertTrue("Missing method end marker: " + endMarker, end > start);
        return source.substring(start, end);
    }
}
