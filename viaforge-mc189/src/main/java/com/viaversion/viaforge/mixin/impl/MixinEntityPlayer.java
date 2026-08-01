/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 * Copyright (C) 2021-2026 Florian Reuth <git@florianreuth.de> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaforge.common.ViaForgeCommon;
import com.viaversion.viaforge.compat.ModernPlayerPhysics;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer {

    @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$modernPoseEyeHeight(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof EntityPlayerSP && viaforge$isModernTarget()) {
            cir.setReturnValue(((ModernPlayerPhysics) this).viaforge$getModernEyeHeight());
        }
    }

    @Inject(method = "onItemUseFinish", at = @At("HEAD"), require = 0)
    private void viaforge$markLocalItemUseFinished(CallbackInfo ci) {
        if ((Object) this instanceof EntityPlayerSP && viaforge$isModernTarget()) {
            ((ModernPlayerPhysics) this).viaforge$markLocalItemUseFinished();
        }
    }

    @Inject(method = "handleStatusUpdate", at = @At("HEAD"), require = 0)
    private void viaforge$confirmServerItemUseFinished(byte id, CallbackInfo ci) {
        if (id == 9
                && (Object) this instanceof EntityPlayerSP
                && viaforge$isModernTarget()) {
            ((ModernPlayerPhysics) this).viaforge$confirmServerItemUseFinished();
        }
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }
}
