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
import com.viaversion.viaforge.compat.ModernFluidPhysics;
import com.viaversion.viaforge.compat.ModernOffhandPlayer;
import com.viaversion.viaforge.compat.ModernPlayerPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * FDPClient overwrites onLivingUpdate at the default priority (1000).
 * Apply after that overwrite so our injections target FDP's merged method
 * instead of competing with it at the same priority.
 */
@Mixin(value = EntityPlayerSP.class, priority = 900)
public abstract class MixinEntityPlayerSP implements ModernPlayerPhysics, ModernOffhandPlayer {

    @Unique
    private static final int viaforge$offhandSwingDuration = 6;

    @Unique
    private boolean viaforge$offhandSwinging;

    @Unique
    private int viaforge$offhandSwingTicks;

    @Unique
    private float viaforge$offhandSwingProgress;

    @Unique
    private float viaforge$previousOffhandSwingProgress;

    @Unique
    private boolean viaforge$modernSwimming;

    @Unique
    private boolean viaforge$wasEyeInWater;

    @Unique
    private boolean viaforge$wasSprintingBeforeInput;

    @Unique
    private BlockPos viaforge$mainSupportingBlock;

    @Unique
    private boolean viaforge$supportingBlockOnGround;

    @Unique
    private boolean viaforge$minorHorizontalCollision;

    @Unique
    private float viaforge$modernEyeHeight = 1.62F;

    @Unique
    private boolean viaforge$slowMovementFromPreviousPose;

    @Unique
    private double viaforge$modernWaterHeight;

    @Unique
    private double viaforge$modernLavaHeight;

    @Unique
    private boolean viaforge$touchingModernLava;

    @Unique
    private boolean viaforge$usingItemAtPreviousTick;

    @Unique
    private boolean viaforge$usingItemAtTickStart;

    @Unique
    private boolean viaforge$carryItemUseSlowdown;

    @Unique
    private boolean viaforge$localItemUseFinished;

    @Unique
    private boolean viaforge$serverItemUseFinished;

    @Unique
    private int viaforge$itemUseFinishGraceTicks;

    /**
     * Modern swimming is a persistent state. It starts from the previous
     * tick's sprint/eye-fluid state and remains active at the water surface.
     */
    @Inject(method = "onLivingUpdate", at = @At("HEAD"), require = 0)
    private void viaforge$updateModernSwimmingState(CallbackInfo ci) {
        if (!viaforge$isModernTarget()) {
            viaforge$modernSwimming = false;
            viaforge$wasEyeInWater = false;
            viaforge$wasSprintingBeforeInput = false;
            viaforge$mainSupportingBlock = null;
            viaforge$supportingBlockOnGround = false;
            viaforge$minorHorizontalCollision = false;
            viaforge$modernEyeHeight = 1.62F;
            viaforge$slowMovementFromPreviousPose = false;
            viaforge$modernWaterHeight = 0.0D;
            viaforge$modernLavaHeight = 0.0D;
            viaforge$touchingModernLava = false;
            viaforge$usingItemAtPreviousTick = false;
            viaforge$usingItemAtTickStart = false;
            viaforge$carryItemUseSlowdown = false;
            viaforge$localItemUseFinished = false;
            viaforge$serverItemUseFinished = false;
            viaforge$itemUseFinishGraceTicks = 0;
            return;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        viaforge$wasSprintingBeforeInput = player.isSprinting();
        viaforge$usingItemAtTickStart = player.isUsingItem();
        if (viaforge$localItemUseFinished) {
            // Status 9 normally arrives in this window. Bound the fallback so
            // a dropped/reordered status cannot leave movement slowed forever.
            viaforge$itemUseFinishGraceTicks = viaforge$serverItemUseFinished ? 0 : 2;
            viaforge$localItemUseFinished = false;
        }
        if (viaforge$serverItemUseFinished) {
            viaforge$itemUseFinishGraceTicks = 0;
            viaforge$serverItemUseFinished = false;
        }
        // A restarted use is already slowed by vanilla below this hook. The
        // completion grace only fills a tick where no item is currently active;
        // applying both multipliers would reduce input to 0.04 instead of 0.2.
        viaforge$carryItemUseSlowdown = !viaforge$usingItemAtTickStart
                && (viaforge$itemUseFinishGraceTicks > 0
                || viaforge$usingItemAtPreviousTick
                && Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown());
        if (viaforge$itemUseFinishGraceTicks > 0) {
            viaforge$itemUseFinishGraceTicks--;
        }
    }

    @Inject(method = "onLivingUpdate", at = @At("RETURN"), require = 0)
    private void viaforge$rememberModernItemUseState(CallbackInfo ci) {
        if (viaforge$isModernTarget()) {
            viaforge$usingItemAtPreviousTick = viaforge$usingItemAtTickStart;
        }
    }

    @Inject(method = "onLivingUpdate", at = @At("RETURN"), require = 0)
    private void viaforge$updateOffhandSwing(CallbackInfo ci) {
        viaforge$previousOffhandSwingProgress = viaforge$offhandSwingProgress;
        if (!viaforge$isModernTarget()) {
            viaforge$offhandSwinging = false;
            viaforge$offhandSwingTicks = 0;
            viaforge$offhandSwingProgress = 0.0F;
            viaforge$previousOffhandSwingProgress = 0.0F;
            return;
        }

        if (viaforge$offhandSwinging) {
            viaforge$offhandSwingTicks++;
            if (viaforge$offhandSwingTicks >= viaforge$offhandSwingDuration) {
                viaforge$offhandSwingTicks = 0;
                viaforge$offhandSwinging = false;
            }
        } else {
            viaforge$offhandSwingTicks = 0;
        }
        viaforge$offhandSwingProgress = (float) viaforge$offhandSwingTicks
                / (float) viaforge$offhandSwingDuration;
    }

    @Override
    public void viaforge$swingOffhand() {
        if (!viaforge$offhandSwinging
                || viaforge$offhandSwingTicks >= viaforge$offhandSwingDuration / 2) {
            viaforge$offhandSwingTicks = 0;
            viaforge$offhandSwingProgress = 0.0F;
            viaforge$previousOffhandSwingProgress = 0.0F;
            viaforge$offhandSwinging = true;
        }
    }

    @Override
    public float viaforge$getOffhandSwingProgress(float partialTicks) {
        float delta = viaforge$offhandSwingProgress - viaforge$previousOffhandSwingProgress;
        if (delta < 0.0F) {
            delta += 1.0F;
        }
        return viaforge$previousOffhandSwingProgress + delta * partialTicks;
    }

    /** Called after both vanilla and FDP have populated this tick's input. */
    @Override
    public void viaforge$updateModernMovementInput(MovementInput input) {
        if (!viaforge$isModernTarget()) {
            return;
        }

        // 1.8 applies 0.3 immediately from the current sneak key. Undo that
        // and reapply it from the pose selected during the previous tick.
        if (input.sneak && !viaforge$slowMovementFromPreviousPose) {
            input.moveStrafe /= 0.3F;
            input.moveForward /= 0.3F;
        } else if (!input.sneak && viaforge$slowMovementFromPreviousPose) {
            input.moveStrafe *= 0.3F;
            input.moveForward *= 0.3F;
        }

        // Holding use immediately starts the next stack after food finishes.
        // Keep the completion-gap tick slowed while the replacement use packet
        // is sent later in the same client tick.
        if (viaforge$carryItemUseSlowdown) {
            input.moveStrafe *= 0.2F;
            input.moveForward *= 0.2F;
        }
        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        viaforge$updateSwimmingAndPose(player);

        final boolean hasFood = player.getFoodStats().getFoodLevel() > 6
                || player.capabilities.allowFlying;
        final boolean keepSwimmingSprint = viaforge$wasSprintingBeforeInput
                && viaforge$modernSwimming
                && player.isInWater()
                && !player.isUsingItem()
                && (player.onGround
                || input.sneak
                || input.moveForward > 1.0E-5F && hasFood);
        if (!player.isSprinting() && keepSwimmingSprint) {
            player.setSprinting(true);
        }

        if ((player.isUsingItem() || viaforge$carryItemUseSlowdown) && !player.isRiding()) {
            player.setSprinting(false);
        }

        if (player.isSprinting()
                && player.isInWater()
                && !viaforge$isModernEyeInWater(player)
                && !viaforge$modernSwimming) {
            player.setSprinting(false);
        }

        if (player.isInWater()
                && input.sneak
                && !player.capabilities.isFlying
                && !player.isRiding()) {
            player.motionY -= 0.04F;
        }

        player.jumpMovementFactor = player.isSprinting() ? 0.025999999F : 0.02F;
    }

    /** Modern clients keep sprinting through collisions aligned with their input. */
    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isCollidedHorizontally:Z"
            ),
            require = 0
    )
    private boolean viaforge$modernBlockingHorizontalCollision(EntityPlayerSP player) {
        return player.isCollidedHorizontally
                && (!viaforge$isModernTarget() || !viaforge$minorHorizontalCollision);
    }

    /**
     * Modern clients report small movements instead of waiting for the 1.8
     * 0.03-block position threshold. ViaVersion presents this connection as
     * 1.20.5 to the server, so keep the wire movement cadence consistent.
     */
    @ModifyConstant(
            method = "onUpdateWalkingPlayer",
            constant = @Constant(doubleValue = 9.0E-4D),
            require = 0
    )
    private double viaforge$modernPositionThreshold(double original) {
        return viaforge$isModernTarget() ? 4.0E-8D : original;
    }

    /**
     * 1.8 checks the position reminder before incrementing it, while modern
     * clients increment first. Using 19 here therefore produces the modern
     * limit of at most 19 movement packets without a position update.
     */
    @ModifyConstant(
            method = "onUpdateWalkingPlayer",
            constant = @Constant(intValue = 20),
            require = 0
    )
    private int viaforge$modernPositionReminder(int original) {
        return viaforge$isModernTarget() ? 19 : original;
    }

    @Unique
    private void viaforge$updateSwimmingAndPose(EntityPlayerSP player) {
        final boolean touchingWater = player.isInWater();
        final boolean eyeInWater = viaforge$isModernEyeInWater(player);
        if (player.capabilities.isFlying || player.isRiding()) {
            viaforge$modernSwimming = false;
        } else if (viaforge$modernSwimming) {
            viaforge$modernSwimming = player.isSprinting() && touchingWater;
        } else {
            viaforge$modernSwimming = player.isSprinting()
                    && (eyeInWater || viaforge$wasEyeInWater)
                    && touchingWater
                    && viaforge$areFeetInWater(player);
        }
        viaforge$wasEyeInWater = eyeInWater;

        final float desiredHeight;
        final boolean canCrouch = viaforge$canUseHeight(player, 1.5F);
        final boolean canStand = viaforge$canUseHeight(player, 1.8F);
        if (viaforge$modernSwimming) {
            desiredHeight = 0.6F;
            viaforge$modernEyeHeight = 0.4F;
        } else if (player.isSneaking() || !canStand) {
            desiredHeight = 1.5F;
            viaforge$modernEyeHeight = 1.27F;
        } else {
            desiredHeight = 1.8F;
            viaforge$modernEyeHeight = 1.62F;
        }
        viaforge$setModernHeight(player, desiredHeight);

        viaforge$slowMovementFromPreviousPose = !player.capabilities.isFlying
                && !player.isRiding()
                && !viaforge$modernSwimming
                && canCrouch
                && (player.isSneaking() || !canStand);
    }

    @Override
    public boolean viaforge$isModernSwimming() {
        return viaforge$modernSwimming;
    }

    @Override
    public float viaforge$getModernEyeHeight() {
        return viaforge$modernEyeHeight;
    }

    @Override
    public double viaforge$getModernWaterHeight() {
        return viaforge$modernWaterHeight;
    }

    @Override
    public void viaforge$setModernWaterHeight(double height) {
        viaforge$modernWaterHeight = height;
    }

    @Override
    public double viaforge$getModernLavaHeight() {
        return viaforge$modernLavaHeight;
    }

    @Override
    public void viaforge$setModernLavaHeight(double height) {
        viaforge$modernLavaHeight = height;
    }

    @Override
    public boolean viaforge$isTouchingModernLava() {
        return viaforge$touchingModernLava;
    }

    @Override
    public void viaforge$setTouchingModernLava(boolean touching) {
        viaforge$touchingModernLava = touching;
    }

    @Override
    public BlockPos viaforge$getMainSupportingBlock() {
        return viaforge$mainSupportingBlock;
    }

    @Override
    public boolean viaforge$wasSupportingBlockOnGround() {
        return viaforge$supportingBlockOnGround;
    }

    @Override
    public void viaforge$setMainSupportingBlock(BlockPos position, boolean onGround) {
        viaforge$mainSupportingBlock = position;
        viaforge$supportingBlockOnGround = onGround;
    }

    @Override
    public boolean viaforge$isMinorHorizontalCollision() {
        return viaforge$minorHorizontalCollision;
    }

    @Override
    public void viaforge$setMinorHorizontalCollision(boolean minor) {
        viaforge$minorHorizontalCollision = minor;
    }

    @Override
    public void viaforge$markLocalItemUseFinished() {
        viaforge$localItemUseFinished = true;
    }

    @Override
    public void viaforge$confirmServerItemUseFinished() {
        viaforge$serverItemUseFinished = true;
    }

    @Unique
    private static boolean viaforge$canUseHeight(EntityPlayerSP player, float height) {
        final AxisAlignedBB box = player.getEntityBoundingBox();
        final AxisAlignedBB requested = new AxisAlignedBB(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.minY + height,
                box.maxZ
        );
        return player.worldObj.getCollidingBoundingBoxes(player, requested).isEmpty();
    }

    @Unique
    private static void viaforge$setModernHeight(EntityPlayerSP player, float height) {
        if (player.height == height) {
            return;
        }

        final AxisAlignedBB box = player.getEntityBoundingBox();
        player.height = height;
        player.setEntityBoundingBox(new AxisAlignedBB(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.minY + height,
                box.maxZ
        ));
    }

    @Unique
    private static boolean viaforge$areFeetInWater(EntityPlayerSP player) {
        return ModernFluidPhysics.getWaterHeight(
                player.worldObj,
                new BlockPos(player.posX, player.posY, player.posZ)
        ) > 0.0F;
    }

    @Unique
    private static boolean viaforge$isModernEyeInWater(EntityPlayerSP player) {
        final double eyeY = player.posY
                + ((ModernPlayerPhysics) player).viaforge$getModernEyeHeight()
                - 0.1111111119389534D;
        final BlockPos eyePosition = new BlockPos(player.posX, eyeY, player.posZ);
        return eyePosition.getY() + (double) ModernFluidPhysics.getWaterHeight(
                player.worldObj,
                eyePosition
        ) > eyeY;
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }

}
