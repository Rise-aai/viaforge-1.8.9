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
import com.viaversion.viaforge.compat.ModernOffhandInteraction;
import com.viaversion.viaforge.compat.ModernOffhandKeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.GameSettings;
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

    @Shadow
    public PlayerControllerMP playerController;

    @Shadow
    public GameSettings gameSettings;

    @Shadow
    public GuiScreen currentScreen;

    @Unique
    private boolean viaforge$delayedAttackSwing;

    @Inject(method = "rightClickMouse", at = @At("HEAD"), require = 0)
    private void viaforge$beginModernRightClick(CallbackInfo ci) {
        if (viaforge$isModernTarget()) {
            ModernOffhandInteraction.beginRightClick();
        }
    }

    @Redirect(
            method = "rightClickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;swingItem()V"
            ),
            require = 0
    )
    private void viaforge$swingCorrectHandAfterBlockUse(EntityPlayerSP player) {
        if (!viaforge$isModernTarget() || !ModernOffhandInteraction.wasClientOffhandAction()) {
            player.swingItem();
        }
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;fireKeyInput()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void viaforge$handleOffhandSwapAfterKeyInput(CallbackInfo ci) {
        if (!viaforge$isModernTarget()
                || thePlayer == null
                || playerController == null
                || currentScreen != null
                || !(gameSettings instanceof ModernOffhandKeyBinding)) {
            return;
        }

        final ModernOffhandKeyBinding keys = (ModernOffhandKeyBinding) gameSettings;
        if (keys.viaforge$getSwapOffhandKey() != null
                && keys.viaforge$getSwapOffhandKey().isPressed()) {
            ModernOffhandInteraction.sendSwapItemWithOffhand(thePlayer);
        }
    }

    @Inject(method = "rightClickMouse", at = @At("RETURN"), require = 0)
    private void viaforge$rightClickOffhandAir(CallbackInfo ci) {
        if (!viaforge$isModernTarget()
                || thePlayer == null
                || playerController == null
                || thePlayer.inventory.getCurrentItem() != null
                || !ModernOffhandInteraction.hasOffhand(thePlayer)) {
            return;
        }

        if (objectMouseOver == null
                || objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            ModernOffhandInteraction.sendUseItem(thePlayer);
        }
    }

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
