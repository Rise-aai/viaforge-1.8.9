/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaforge.common.ViaForgeCommon;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiInventory.class)
public abstract class MixinGuiInventory {

    @Inject(method = "drawGuiContainerBackgroundLayer", at = @At("RETURN"))
    private void viaforge$drawOffhandSlot(float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        if (!viaforge$isModernTarget()) {
            return;
        }

        final GuiContainerAccessor container = (GuiContainerAccessor) this;
        final int left = container.viaforge$getGuiLeft() + 76;
        final int top = container.viaforge$getGuiTop() + 61;
        Gui.drawRect(left, top, left + 18, top + 18, 0xFF373737);
        Gui.drawRect(left + 1, top + 1, left + 17, top + 17, 0xFF8B8B8B);
        Gui.drawRect(left + 2, top + 2, left + 16, top + 16, 0xFF373737);
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }
}
