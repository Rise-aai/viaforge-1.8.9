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
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    /** Reproduce the 1.20.6 water and lava travel order instead of 1.8. */
    @Inject(method = "moveEntityWithHeading", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$modernFluidTravel(float strafe, float forward, CallbackInfo ci) {
        if (!((Object) this instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (player.capabilities.isFlying) {
            return;
        }

        if (!player.isInWater()) {
            if (player.isInLava()) {
                viaforge$modernLavaTravel(player, strafe, forward);
                ci.cancel();
            }
            return;
        }

        if (((ModernPlayerPhysics) player).viaforge$isModernSwimming() && !player.isRiding()) {
            final double lookY = player.getLookVec().yCoord;
            final Material fluidAbove = player.worldObj.getBlockState(new BlockPos(
                    player.posX,
                    player.posY + 0.9D,
                    player.posZ
            )).getBlock().getMaterial();
            if (lookY <= 0.0D
                    || player.movementInput.jump
                    || fluidAbove == Material.water
                    || fluidAbove == Material.lava) {
                final double steering = lookY < -0.2D ? 0.085D : 0.06D;
                player.motionY += (lookY - player.motionY) * steering;
            }
        }

        final boolean falling = player.motionY <= 0.0D;
        final double oldY = player.posY;
        float swimmingFriction = player.isSprinting() ? 0.9F : 0.8F;
        float swimmingSpeed = 0.02F;
        float depthStrider = EnchantmentHelper.getDepthStriderModifier(player);
        if (depthStrider > 3.0F) {
            depthStrider = 3.0F;
        }
        if (!player.onGround) {
            depthStrider *= 0.5F;
        }
        if (depthStrider > 0.0F) {
            swimmingFriction += (0.54600006F - swimmingFriction) * depthStrider / 3.0F;
            swimmingSpeed += (viaforge$getCurrentMovementSpeed(player) - swimmingSpeed)
                    * depthStrider / 3.0F;
        }

        player.moveFlying(strafe, forward, swimmingSpeed);
        player.moveEntity(player.motionX, player.motionY, player.motionZ);
        if (player.isCollidedHorizontally && player.isOnLadder()) {
            player.motionY = 0.2D;
        }

        player.motionX *= swimmingFriction;
        player.motionY *= 0.8F;
        player.motionZ *= swimmingFriction;
        if (!player.isSprinting()) {
            final double gravityStep = 0.08D / 16.0D;
            player.motionY = falling
                    && Math.abs(player.motionY - 0.005D) >= 0.003D
                    && Math.abs(player.motionY - gravityStep) < 0.003D
                    ? -0.003D
                    : player.motionY - gravityStep;
        }

        if (player.isCollidedHorizontally && player.isOffsetPositionInLiquid(
                player.motionX,
                player.motionY + 0.6F - player.posY + oldY,
                player.motionZ
        )) {
            player.motionY = 0.3F;
        }

        viaforge$updateLimbSwing(player);
        ci.cancel();
    }

    @Unique
    private static void viaforge$modernLavaTravel(
            EntityPlayerSP player,
            float strafe,
            float forward
    ) {
        final ModernPlayerPhysics physics = (ModernPlayerPhysics) player;
        final boolean falling = player.motionY <= 0.0D;
        final double oldY = player.posY;

        player.moveFlying(strafe, forward, 0.02F);
        player.moveEntity(player.motionX, player.motionY, player.motionZ);

        if (physics.viaforge$getModernLavaHeight() <= 0.4D) {
            player.motionX *= 0.5D;
            player.motionY *= 0.800000011920929D;
            player.motionZ *= 0.5D;
            if (!player.isSprinting()) {
                final double gravityStep = 0.08D / 16.0D;
                player.motionY = falling
                        && Math.abs(player.motionY - 0.005D) >= 0.003D
                        && Math.abs(player.motionY - gravityStep) < 0.003D
                        ? -0.003D
                        : player.motionY - gravityStep;
            }
        } else {
            player.motionX *= 0.5D;
            player.motionY *= 0.5D;
            player.motionZ *= 0.5D;
        }

        player.motionY -= 0.08D / 4.0D;
        if (player.isCollidedHorizontally && player.isOffsetPositionInLiquid(
                player.motionX,
                player.motionY + 0.6F - player.posY + oldY,
                player.motionZ
        )) {
            player.motionY = 0.3F;
        }
        viaforge$updateLimbSwing(player);
    }

    /** Shallow water uses a ground jump; deeper water adds the 0.04 swim impulse. */
    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isInWater()Z"
            ),
            require = 0
    )
    private boolean viaforge$modernWaterJumpBranch(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return entity.isInWater();
        }

        final EntityPlayerSP player = (EntityPlayerSP) entity;
        return player.isInWater()
                && (!player.onGround
                || ((ModernPlayerPhysics) player).viaforge$getModernWaterHeight() > 0.4D);
    }

    /** Shallow lava uses a ground jump; deeper lava uses the fluid jump impulse. */
    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isInLava()Z"
            ),
            require = 0
    )
    private boolean viaforge$modernLavaJumpBranch(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return entity.isInLava();
        }

        final EntityPlayerSP player = (EntityPlayerSP) entity;
        return player.isInLava()
                && (!player.onGround
                || ((ModernPlayerPhysics) player).viaforge$getModernLavaHeight() > 0.4D);
    }

    /**
     * 1.20.5+ computes sprint-jump impulse by multiplying the float trig
     * result as a double. 1.8 rounds the multiplication back to float first.
     */
    @Inject(method = "jump", at = @At("RETURN"), require = 0)
    private void viaforge$modernSprintJumpPrecision(CallbackInfo ci) {
        if (!((Object) this instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (!player.isSprinting()) {
            return;
        }

        final float yaw = player.rotationYaw * ((float) Math.PI / 180.0F);
        final float sin = MathHelper.sin(yaw);
        final float cos = MathHelper.cos(yaw);
        player.motionX += (double) (-sin) * 0.2D - (double) (-sin * 0.2F);
        player.motionZ += (double) cos * 0.2D - (double) (cos * 0.2F);
    }

    /** 1.9+ treats a matching open trapdoor above a ladder as climbable. */
    @Inject(method = "isOnLadder", at = @At("RETURN"), cancellable = true, require = 0)
    private void viaforge$modernTrapdoorLadder(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()
                || !((Object) this instanceof EntityPlayerSP)
                || !viaforge$isModernTarget()) {
            return;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        final BlockPos position = new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.getEntityBoundingBox().minY),
                MathHelper.floor_double(player.posZ)
        );
        final IBlockState trapdoor = player.worldObj.getBlockState(position);
        if (!(trapdoor.getBlock() instanceof BlockTrapDoor)
                || !trapdoor.getValue(BlockTrapDoor.OPEN)) {
            return;
        }

        final IBlockState ladder = player.worldObj.getBlockState(position.down());
        if (ladder.getBlock() instanceof BlockLadder
                && trapdoor.getValue(BlockTrapDoor.FACING)
                == ladder.getValue(BlockLadder.FACING)) {
            cir.setReturnValue(true);
        }
    }

    /** Modern clients use the actual supporting block's X/Z at block edges. */
    @Redirect(
            method = "moveEntityWithHeading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;"
            ),
            require = 0
    )
    private IBlockState viaforge$modernSupportingBlock(World world, BlockPos originalPos) {
        if (!((Object) this instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return world.getBlockState(originalPos);
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        return world.getBlockState(viaforge$findFrictionBlockPos(player, originalPos));
    }

    /** Use the 1.14+ ground acceleration formula and its float operation order. */
    @ModifyArg(
            method = "moveEntityWithHeading",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;moveFlying(FFF)V",
                    ordinal = 0
            ),
            index = 2,
            require = 0
    )
    private float viaforge$modernGroundAcceleration(float original) {
        if (!((Object) this instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return original;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (!player.onGround) {
            return original;
        }

        final BlockPos fallback = new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1,
                MathHelper.floor_double(player.posZ)
        );
        final float friction = player.worldObj.getBlockState(
                viaforge$findFrictionBlockPos(player, fallback)
        ).getBlock().slipperiness;
        return viaforge$getCurrentMovementSpeed(player)
                * (0.21600002F / (friction * friction * friction));
    }

    /** Modern clients discard velocity components below 0.003 instead of 0.005. */
    @ModifyConstant(
            method = "onLivingUpdate",
            constant = @Constant(doubleValue = 0.005D),
            require = 0
    )
    private double viaforge$modernVelocityEpsilon(double original) {
        return (Object) this instanceof EntityPlayerSP && viaforge$isModernTarget() ? 0.003D : original;
    }

    /** Modern local players do not receive the legacy client-side 0.98 drag. */
    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isServerWorld()Z",
                    ordinal = 0
            ),
            require = 0
    )
    private boolean viaforge$modernLocalWorld(EntityLivingBase entity) {
        if (entity instanceof EntityPlayerSP && viaforge$isModernTarget()) {
            return true;
        }
        return entity.isServerWorld();
    }

    @Unique
    private static BlockPos viaforge$findFrictionBlockPos(
            EntityPlayerSP player,
            BlockPos fallback
    ) {
        if (!player.onGround) {
            return fallback;
        }

        final BlockPos support = ((ModernPlayerPhysics) player).viaforge$getMainSupportingBlock();
        if (support == null) {
            return fallback;
        }

        final net.minecraft.block.Block block = player.worldObj.getBlockState(support).getBlock();
        if (block instanceof BlockFence || block instanceof BlockWall || block instanceof BlockFenceGate) {
            return support;
        }
        return new BlockPos(
                support.getX(),
                MathHelper.floor_double(player.posY - 0.500001D),
                support.getZ()
        );
    }

    @Unique
    private static void viaforge$updateLimbSwing(EntityPlayerSP player) {
        player.prevLimbSwingAmount = player.limbSwingAmount;
        final double deltaX = player.posX - player.prevPosX;
        final double deltaZ = player.posZ - player.prevPosZ;
        float amount = MathHelper.sqrt_double(deltaX * deltaX + deltaZ * deltaZ) * 4.0F;
        if (amount > 1.0F) {
            amount = 1.0F;
        }
        player.limbSwingAmount += (amount - player.limbSwingAmount) * 0.4F;
        player.limbSwing += player.limbSwingAmount;
    }

    @Unique
    private static float viaforge$getCurrentMovementSpeed(EntityPlayerSP player) {
        return (float) player.getEntityAttribute(
                SharedMonsterAttributes.movementSpeed
        ).getAttributeValue();
    }

    @Unique
    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }

}
