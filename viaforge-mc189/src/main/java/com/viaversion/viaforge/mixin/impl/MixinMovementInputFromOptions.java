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

import com.viaversion.viaforge.compat.ModernPlayerPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MovementInputFromOptions.class, priority = 900)
public abstract class MixinMovementInputFromOptions {

    @Inject(method = "updatePlayerMoveState", at = @At("RETURN"), require = 0)
    private void viaforge$applyModernMovementInput(CallbackInfo ci) {
        final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player instanceof ModernPlayerPhysics) {
            ((ModernPlayerPhysics) player).viaforge$updateModernMovementInput(
                    (MovementInput) (Object) this
            );
        }
    }
}
