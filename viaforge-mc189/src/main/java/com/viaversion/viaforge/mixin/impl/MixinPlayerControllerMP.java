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
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.network.Packet;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {

    @Unique
    private Vec3 viaforge$pendingOffhandEntityHit;

    /**
     * 1.8 repeatedly reuses the selected item while right-click is held. If
     * the selected item is a sword, that restarts its block action and clears
     * the active offhand food use before the food timer can finish.
     */
    @Inject(method = "sendUseItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$preserveActiveOffhandUse(
            EntityPlayer player,
            net.minecraft.world.World world,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!viaforge$isModernTarget()
                || !(player instanceof EntityPlayerSP)
                || player.getItemInUse() != ModernOffhandInteraction.getOffhand(player)
                || stack.getItemUseAction() != EnumAction.BLOCK) {
            return;
        }

        cir.setReturnValue(false);
    }

    @Inject(method = "onPlayerRightClick", at = @At("RETURN"), cancellable = true, require = 0)
    private void viaforge$rightClickOffhandBlock(
            EntityPlayerSP player,
            WorldClient world,
            ItemStack stack,
            BlockPos pos,
            EnumFacing face,
            Vec3 hitVec,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        if (!viaforge$isModernTarget()
                || !ModernOffhandInteraction.hasOffhand(player)
                || !ModernOffhandInteraction.sendUseItemOnBlock(player, pos, face, hitVec)) {
            return;
        }

        if (ModernOffhandInteraction.shouldUseItemAfterBlock(player)) {
            ModernOffhandInteraction.sendUseItem(player);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "sendUseItem", at = @At("RETURN"), cancellable = true, require = 0)
    private void viaforge$rightClickOffhandAir(
            EntityPlayer player,
            net.minecraft.world.World world,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                || !viaforge$isModernTarget()
                || !(player instanceof EntityPlayerSP)
                || !ModernOffhandInteraction.hasOffhand(player)) {
            return;
        }

        if (player.getItemInUse() == stack
                && stack.getItemUseAction() != EnumAction.BLOCK) {
            return;
        }
        if (player.getItemInUse() == stack) {
            player.clearItemInUse();
        }
        if (ModernOffhandInteraction.sendUseItem((EntityPlayerSP) player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "interactWithEntitySendPacket", at = @At("RETURN"), cancellable = true, require = 0)
    private void viaforge$rightClickOffhandEntity(
            EntityPlayer player,
            Entity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        final Vec3 hit = viaforge$pendingOffhandEntityHit;
        viaforge$pendingOffhandEntityHit = null;
        if (Boolean.TRUE.equals(cir.getReturnValue())
                || !viaforge$isModernTarget()
                || hit == null
                || !ModernOffhandInteraction.hasOffhand(player)) {
            return;
        }

        ModernOffhandInteraction.sendInteractAt(player, target, hit);
        ModernOffhandInteraction.sendInteract(player, target);
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

    /** 1.9+ requires ATTACK -> ANIMATION with no packet between them. */
    @Redirect(
            method = "attackEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/NetHandlerPlayClient;addToSendQueue(Lnet/minecraft/network/Packet;)V"
            ),
            require = 0
    )
    private void viaforge$sendModernAttackThenAnimation(
            NetHandlerPlayClient handler,
            Packet packet,
            EntityPlayer player,
            Entity target
    ) {
        handler.addToSendQueue(packet);
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
        viaforge$pendingOffhandEntityHit = null;

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
            viaforge$useItemWhileAttacking(player);
            cir.setReturnValue(true);
            return;
        }

        final boolean consumed = target.interactAt(player, hit);
        Minecraft.getMinecraft().getNetHandler().addToSendQueue(
                new C02PacketUseEntity(target, hit)
        );
        if (!consumed && ModernOffhandInteraction.hasOffhand(player)) {
            viaforge$pendingOffhandEntityHit = hit;
        }
        cir.setReturnValue(consumed);
    }

    @Unique
    private void viaforge$useItemWhileAttacking(EntityPlayer player) {
        final ItemStack mainHand = player.inventory.getCurrentItem();
        if (mainHand != null) {
            ((PlayerControllerMP) (Object) this).sendUseItem(player, player.worldObj, mainHand);
        } else if (player instanceof EntityPlayerSP) {
            ModernOffhandInteraction.sendUseItem((EntityPlayerSP) player);
        }
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
