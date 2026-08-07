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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {

    @Inject(method = "onPlayerRightClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$rightClickOffhandBlock(
            EntityPlayerSP player,
            WorldClient world,
            ItemStack stack,
            BlockPos pos,
            EnumFacing face,
            Vec3 hitVec,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (viaforge$isModernTarget() && ModernOffhandInteraction.hasOffhand(player)
                && ModernOffhandInteraction.sendUseItemOnBlock(player, pos, face, hitVec)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "sendUseItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$rightClickOffhandAir(
            EntityPlayer player,
            net.minecraft.world.World world,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (viaforge$isModernTarget() && player instanceof EntityPlayerSP
                && ModernOffhandInteraction.hasOffhand(player)
                && ModernOffhandInteraction.sendUseItem((EntityPlayerSP) player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "interactWithEntitySendPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$rightClickOffhandEntity(
            EntityPlayer player,
            Entity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (viaforge$isModernTarget() && ModernOffhandInteraction.hasOffhand(player)) {
            ModernOffhandInteraction.sendInteract(player, target);
            cir.setReturnValue(target.interactFirst(player));
        }
    }

    @Unique
    private double viaforge$motionBeforeAttackX;

    @Unique
    private double viaforge$motionBeforeAttackZ;

    @Unique
    private boolean viaforge$knockbackAttackSlow;

    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void viaforge$captureModernAttackSlow(
            EntityPlayer player,
            Entity target,
            CallbackInfo ci
    ) {
        if (!viaforge$isModernTarget()) {
            viaforge$knockbackAttackSlow = false;
            return;
        }

        viaforge$motionBeforeAttackX = player.motionX;
        viaforge$motionBeforeAttackZ = player.motionZ;
        viaforge$knockbackAttackSlow = EnchantmentHelper.getKnockbackModifier(player) > 0;
    }

    /** 1.9+ requires ATTACK -> ANIMATION before any sprint-state packet. */
    @Inject(
            method = "attackEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/NetHandlerPlayClient;addToSendQueue(Lnet/minecraft/network/Packet;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void viaforge$sendModernAttackAnimation(
            EntityPlayer player,
            Entity target,
            CallbackInfo ci
    ) {
        if (viaforge$isModernTarget()) {
            player.swingItem();
        }
    }

    /**
     * The remote target can reject the client-side damage call while Grim has
     * already observed a valid knockback-enchanted attack. Preserve the modern
     * attack slowdown even in that desynchronised target state.
     */
    @Inject(method = "attackEntity", at = @At("RETURN"), require = 0)
    private void viaforge$finishModernAttackSlow(
            EntityPlayer player,
            Entity target,
            CallbackInfo ci
    ) {
        if (!viaforge$isModernTarget() || !viaforge$knockbackAttackSlow) {
            return;
        }

        final double expectedX = viaforge$motionBeforeAttackX * 0.6D;
        final double expectedZ = viaforge$motionBeforeAttackZ * 0.6D;
        if (Math.abs(player.motionX - expectedX) > 1.0E-12D
                || Math.abs(player.motionZ - expectedZ) > 1.0E-12D) {
            player.motionX *= 0.6D;
            player.motionZ *= 0.6D;
        }
        player.setSprinting(false);
    }

    /**
     * Modern clients always emit INTERACT_AT before testing the local result,
     * then emit INTERACT when it was not consumed. Legacy 1.8 only sends the
     * first packet when interactAt succeeds, which violates the modern order.
     */
    @Inject(
            method = "isPlayerRightClickingOnEntity",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void viaforge$modernEntityInteraction(
            EntityPlayer player,
            Entity target,
            MovingObjectPosition hitResult,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!viaforge$isModernTarget()
                || Minecraft.getMinecraft().playerController.isSpectatorMode()) {
            return;
        }

        if (ModernOffhandInteraction.hasOffhand(player)) {
            final Vec3 relativeHit = new Vec3(
                    hitResult.hitVec.xCoord - target.posX,
                    hitResult.hitVec.yCoord - target.posY,
                    hitResult.hitVec.zCoord - target.posZ
            );
            final Vec3 clamped = viaforge$clampInteractionHit(target, target.getEntityBoundingBox(), relativeHit);
            if (!Minecraft.getMinecraft().gameSettings.keyBindAttack.isKeyDown()) {
                ModernOffhandInteraction.sendInteractAt(player, target, clamped);
                final boolean consumed = target.interactAt(player, clamped);
                cir.setReturnValue(consumed);
            } else {
                cir.setReturnValue(true);
            }
            return;
        }

        /*
         * 1.8 ray tracing expands entity hitboxes by collisionBorderSize
         * (0.1 for players). Modern INTERACT_AT packets use the actual
         * bounding box, and Grim validates that exact range for modern
         * protocol clients. Do not let the legacy expansion escape into the
         * translated packet.
         */
        final AxisAlignedBB bounds = target.getEntityBoundingBox();
        final Vec3 hit = viaforge$clampInteractionHit(target, bounds, new Vec3(
                hitResult.hitVec.xCoord - target.posX,
                hitResult.hitVec.yCoord - target.posY,
                hitResult.hitVec.zCoord - target.posZ
        ));

        // A simultaneous attack wins over a right-click interaction in 1.8.
        // Sending both produces an INTERACT_AT between ATTACK and ANIMATION,
        // a sequence modern clients never emit.
        if (Minecraft.getMinecraft().gameSettings.keyBindAttack.isKeyDown()) {
            cir.setReturnValue(true);
            return;
        }

        final boolean consumed = target.interactAt(player, hit);
        Minecraft.getMinecraft().getNetHandler().addToSendQueue(
                new C02PacketUseEntity(target, hit)
        );
        cir.setReturnValue(consumed);
    }

    @Unique
    private static Vec3 viaforge$clampInteractionHit(Entity target, AxisAlignedBB bounds, Vec3 hit) {
        final double epsilon = 1.0E-5D;
        final double minX = bounds.minX - target.posX + epsilon;
        final double maxX = bounds.maxX - target.posX - epsilon;
        final double minY = bounds.minY - target.posY + epsilon;
        final double maxY = bounds.maxY - target.posY - epsilon;
        final double minZ = bounds.minZ - target.posZ + epsilon;
        final double maxZ = bounds.maxZ - target.posZ - epsilon;

        return new Vec3(
                viaforge$clamp(hit.xCoord, minX, maxX),
                viaforge$clamp(hit.yCoord, minY, maxY),
                viaforge$clamp(hit.zCoord, minZ, maxZ)
        );
    }

    @Unique
    private static double viaforge$clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Unique
    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }

}
