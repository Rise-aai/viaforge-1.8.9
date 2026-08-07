/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaforge.common.ViaForgeCommon;
import com.viaversion.viaforge.compat.ModernOffhandInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

    @Shadow
    private Minecraft mc;

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

        GlStateManager.pushMatrix();
        GlStateManager.translate(-0.56F, -0.52F, -0.72F);
        GlStateManager.scale(-0.4F, 0.4F, 0.4F);
        GlStateManager.disableCull();
        ((ItemRenderer) (Object) this).renderItem(
                mc.thePlayer,
                stack,
                ItemCameraTransforms.TransformType.FIRST_PERSON
        );
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }
}
