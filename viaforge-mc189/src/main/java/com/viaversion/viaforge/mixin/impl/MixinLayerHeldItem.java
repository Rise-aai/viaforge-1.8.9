/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaforge.common.ViaForgeCommon;
import com.viaversion.viaforge.compat.ModernOffhandInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerHeldItem.class)
public abstract class MixinLayerHeldItem {

    @Shadow
    @Final
    private RendererLivingEntity<?> livingEntityRenderer;

    @Inject(method = "doRenderLayer", at = @At("RETURN"), require = 0)
    private void viaforge$renderOffhand(
            EntityLivingBase entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scale,
            CallbackInfo ci
    ) {
        if (!viaforge$isModernTarget() || !(entity instanceof EntityPlayer)) {
            return;
        }

        final ItemStack stack = ((ModernOffhandInventory) ((EntityPlayer) entity).inventory).viaforge$getOffhand();
        if (stack == null || !(livingEntityRenderer.getMainModel() instanceof ModelBiped)) {
            return;
        }

        final ModelBiped model = (ModelBiped) livingEntityRenderer.getMainModel();
        GlStateManager.pushMatrix();
        if (model.isChild) {
            GlStateManager.translate(0.0F, 0.625F, 0.0F);
            GlStateManager.rotate(-20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
        }

        model.bipedLeftArm.postRender(0.0625F);
        GlStateManager.translate(0.0625F, 0.4375F, 0.0625F);
        if (stack.getItem() instanceof ItemBlock
                && Block.getBlockFromItem(stack.getItem()).getRenderType() == 2) {
            GlStateManager.translate(0.0F, 0.1875F, -0.3125F);
            GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-45.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-0.375F, -0.375F, 0.375F);
        }
        if (entity.isSneaking()) {
            GlStateManager.translate(0.0F, 0.203125F, 0.0F);
        }

        Minecraft.getMinecraft().getItemRenderer().renderItem(
                entity,
                stack,
                ItemCameraTransforms.TransformType.THIRD_PERSON
        );
        GlStateManager.popMatrix();
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }
}
