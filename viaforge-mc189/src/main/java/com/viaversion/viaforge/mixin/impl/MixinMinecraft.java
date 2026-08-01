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
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    public EntityPlayerSP thePlayer;

    @Shadow
    public MovingObjectPosition objectMouseOver;

    @Unique
    private boolean viaforge$delayedAttackSwing;

    @Inject(method = "clickMouse", at = @At("HEAD"), require = 0)
    private void viaforge$resetDelayedAttackSwing(CallbackInfo ci) {
        viaforge$delayedAttackSwing = false;
    }

    /** Modern clients perform the selected attack/dig action before swinging. */
    @Redirect(
            method = "clickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;swingItem()V"
            ),
            require = 0
    )
    private void viaforge$delayModernAttackSwing(EntityPlayerSP player) {
        if (viaforge$isModernTarget()) {
            // Entity attacks send their animation from PlayerControllerMP,
            // immediately after ATTACK and before local sprint/attack slowing.
            viaforge$delayedAttackSwing = objectMouseOver == null
                    || objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY;
        } else {
            player.swingItem();
        }
    }

    @Inject(method = "clickMouse", at = @At("RETURN"), require = 0)
    private void viaforge$sendModernAttackSwing(CallbackInfo ci) {
        if (viaforge$isModernTarget() && viaforge$delayedAttackSwing && thePlayer != null) {
            thePlayer.swingItem();
        }
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }

}
