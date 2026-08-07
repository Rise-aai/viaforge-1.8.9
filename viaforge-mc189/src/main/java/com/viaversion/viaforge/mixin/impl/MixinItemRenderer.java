/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaforge.common.ViaForgeCommon;
import com.viaversion.viaforge.compat.ModernOffhandInventory;
import com.viaversion.viaforge.compat.ModernOffhandPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

    @Shadow
    private Minecraft mc;

    @Shadow
    private ItemStack itemToRender;

    @Invoker("transformFirstPersonItem")
    protected abstract void viaforge$transformFirstPersonItem(float equipProgress, float swingProgress);

    @Invoker("performDrinking")
    protected abstract void viaforge$performDrinking(AbstractClientPlayer player, float partialTicks);

    @Invoker("doBlockTransformations")
    protected abstract void viaforge$doBlockTransformations();

    @Invoker("doBowTransformations")
    protected abstract void viaforge$doBowTransformations(float partialTicks, AbstractClientPlayer player);

    @Inject(method = "renderItemInFirstPerson", at = @At("RETURN"), require = 0)
    private void viaforge$renderOffhand(float partialTicks, CallbackInfo ci) {
        if (!viaforge$isModernTarget()
                || mc == null
                || mc.thePlayer == null
                || mc.gameSettings.thirdPersonView != 0) {
            return;
        }

        final ItemStack stack = ((ModernOffhandInventory) mc.thePlayer.inventory).viaforge$getOffhand();
        if (stack == null) {
            return;
        }

        final ItemStack previousItemToRender = itemToRender;
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.pushMatrix();
        try {
            itemToRender = stack;
            GlStateManager.disableCull();
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
            viaforge$applyOffhandUseTransform(stack, partialTicks);
            ((ItemRenderer) (Object) this).renderItem(
                    mc.thePlayer,
                    stack,
                    ItemCameraTransforms.TransformType.FIRST_PERSON
            );
        } finally {
            itemToRender = previousItemToRender;
            GlStateManager.popMatrix();
            GlStateManager.enableCull();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        }
    }

    private void viaforge$applyOffhandUseTransform(ItemStack stack, float partialTicks) {
        final float swingProgress = ((ModernOffhandPlayer) mc.thePlayer)
                .viaforge$getOffhandSwingProgress(partialTicks);
        if (!mc.thePlayer.isUsingItem()
                || mc.thePlayer.getItemInUse() != stack
                || mc.thePlayer.getItemInUseCount() <= 0) {
            viaforge$transformFirstPersonItem(0.0F, swingProgress);
            return;
        }

        final EnumAction action = stack.getItemUseAction();
        if (action == EnumAction.EAT || action == EnumAction.DRINK) {
            viaforge$performDrinking(mc.thePlayer, partialTicks);
        }
        viaforge$transformFirstPersonItem(0.0F, swingProgress);
        if (action == EnumAction.BLOCK) {
            viaforge$doBlockTransformations();
        } else if (action == EnumAction.BOW) {
            viaforge$doBowTransformations(partialTicks, mc.thePlayer);
        }
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }
}
