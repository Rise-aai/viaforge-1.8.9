/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaforge.common.ViaForgeCommon;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerPlayer.class)
public class MixinContainerPlayer {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void viaforge$addOffhandSlot(
            InventoryPlayer inventory,
            boolean localWorld,
            EntityPlayer player,
            CallbackInfo ci
    ) {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        if (manager != null
                && manager.getTargetVersion() == ProtocolVersion.v1_20_5
                && player.worldObj.isRemote) {
            ((ContainerAccessor) this).viaforge$addSlotToContainer(
                    new Slot(inventory, 45, 77, 62)
            );
        }
    }
}
