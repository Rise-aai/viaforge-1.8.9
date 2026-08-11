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
import com.viaversion.viaforge.compat.ModernHorizontalCollision;
import com.viaversion.viaforge.compat.ModernPlayerPhysics;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.util.MathHelper;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Unique
    private static final ModernHorizontalCollision.BoxOperations<AxisAlignedBB>
            viaforge$boxOperations = new ModernHorizontalCollision.BoxOperations<AxisAlignedBB>() {
        @Override
        public double calculateXOffset(
                AxisAlignedBB collision,
                AxisAlignedBB moving,
                double requestedX
        ) {
            if (!ModernHorizontalCollision.overlaps(
                    moving.minY, moving.maxY, collision.minY, collision.maxY
            ) || !ModernHorizontalCollision.overlaps(
                    moving.minZ, moving.maxZ, collision.minZ, collision.maxZ
            )) {
                return requestedX;
            }
            return ModernHorizontalCollision.calculateOffset(
                    collision.minX, collision.maxX,
                    moving.minX, moving.maxX,
                    moving.minY, moving.maxY,
                    collision.minY, collision.maxY,
                    requestedX
            );
        }

        @Override
        public double calculateZOffset(
                AxisAlignedBB collision,
                AxisAlignedBB moving,
                double requestedZ
        ) {
            if (!ModernHorizontalCollision.overlaps(
                    moving.minX, moving.maxX, collision.minX, collision.maxX
            ) || !ModernHorizontalCollision.overlaps(
                    moving.minY, moving.maxY, collision.minY, collision.maxY
            )) {
                return requestedZ;
            }
            return ModernHorizontalCollision.calculateOffset(
                    collision.minZ, collision.maxZ,
                    moving.minZ, moving.maxZ,
                    moving.minX, moving.maxX,
                    collision.minX, collision.maxX,
                    requestedZ
            );
        }

        @Override
        public AxisAlignedBB offset(AxisAlignedBB box, double x, double z) {
            return box.offset(x, 0.0D, z);
        }
    };

    @Shadow
    protected boolean isInWeb;

    @Shadow
    protected boolean inWater;

    @Shadow
    protected boolean firstUpdate;

    @Shadow
    private int fire;

    @Shadow
    public float fallDistance;

    @Shadow
    protected abstract void resetHeight();

    @Unique
    private double viaforge$modernStepDesiredY;

    @Unique
    private boolean viaforge$modernStepDownAdjusted;

    @Unique
    private double viaforge$moveStartX;

    @Unique
    private double viaforge$moveStartZ;

    @Unique
    private int viaforge$lastModernFluidTick = Integer.MIN_VALUE;

    @Unique
    private List<AxisAlignedBB> viaforge$baseCollisions;

    @Unique
    private List<AxisAlignedBB> viaforge$stepCollisions;

    @Unique
    private double viaforge$baseRequestedX;

    @Unique
    private double viaforge$baseRequestedZ;

    @Unique
    private double viaforge$stepRequestedX;

    @Unique
    private double viaforge$stepRequestedZ;

    @Unique
    private ModernHorizontalCollision.Result<AxisAlignedBB> viaforge$baseHorizontal;

    @Unique
    private ModernHorizontalCollision.Result<AxisAlignedBB> viaforge$firstStepHorizontal;

    @Unique
    private ModernHorizontalCollision.Result<AxisAlignedBB> viaforge$secondStepHorizontal;

    /** Replace the legacy water/lava AABBs and current-pushing behavior. */
    @Inject(method = "handleWaterMovement", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$modernFluidBaseTick(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        final ModernPlayerPhysics physics = (ModernPlayerPhysics) player;
        if (viaforge$lastModernFluidTick == player.ticksExisted) {
            cir.setReturnValue(inWater);
            return;
        }
        viaforge$lastModernFluidTick = player.ticksExisted;

        final AxisAlignedBB box = player.getEntityBoundingBox().contract(
                0.001D,
                0.001D,
                0.001D
        );
        final int minX = MathHelper.floor_double(box.minX);
        final int maxX = MathHelper.ceiling_double_int(box.maxX);
        final int minY = MathHelper.floor_double(box.minY);
        final int maxY = MathHelper.ceiling_double_int(box.maxY);
        final int minZ = MathHelper.floor_double(box.minZ);
        final int maxZ = MathHelper.ceiling_double_int(box.maxZ);

        boolean touchingWater = false;
        boolean touchingLava = false;
        double waterHeight = 0.0D;
        double lavaHeight = 0.0D;
        Vec3 waterFlow = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 lavaFlow = new Vec3(0.0D, 0.0D, 0.0D);
        int waterFlowCount = 0;
        int lavaFlowCount = 0;

        for (int blockX = minX; blockX < maxX; blockX++) {
            for (int blockY = minY; blockY < maxY; blockY++) {
                for (int blockZ = minZ; blockZ < maxZ; blockZ++) {
                    final BlockPos position = new BlockPos(blockX, blockY, blockZ);
                    final Block block = player.worldObj.getBlockState(position).getBlock();
                    final Material material = block.getMaterial();
                    if (material != Material.water && material != Material.lava) {
                        continue;
                    }

                    final float fluidHeight = ModernFluidPhysics.getFluidHeight(
                            player.worldObj, position, material);
                    final double surfaceY = blockY + (double) fluidHeight;
                    if (fluidHeight == 0.0F || surfaceY < box.minY) {
                        continue;
                    }

                    final boolean water = material == Material.water;
                    if (water) {
                        touchingWater = true;
                        waterHeight = Math.max(surfaceY - box.minY, waterHeight);
                    } else {
                        touchingLava = true;
                        lavaHeight = Math.max(surfaceY - box.minY, lavaHeight);
                    }

                    if (block instanceof BlockLiquid && player.isPushedByWater()) {
                        Vec3 blockFlow = ModernFluidPhysics.getFlow(
                                player.worldObj, position, material);
                        final double trackedHeight = water ? waterHeight : lavaHeight;
                        if (trackedHeight < 0.4D) {
                            blockFlow = new Vec3(
                                    blockFlow.xCoord * trackedHeight,
                                    blockFlow.yCoord * trackedHeight,
                                    blockFlow.zCoord * trackedHeight
                            );
                        }
                        if (water) {
                            waterFlow = waterFlow.add(blockFlow);
                            waterFlowCount++;
                        } else {
                            lavaFlow = lavaFlow.add(blockFlow);
                            lavaFlowCount++;
                        }
                    }
                }
            }
        }

        viaforge$applyModernFluidPush(player, waterFlow, waterFlowCount, 0.014D);
        viaforge$applyModernFluidPush(
                player,
                lavaFlow,
                lavaFlowCount,
                player.worldObj.provider.doesWaterVaporize() ? 0.007D : 0.0023333333333333335D
        );

        physics.viaforge$setModernWaterHeight(waterHeight);
        physics.viaforge$setModernLavaHeight(lavaHeight);
        physics.viaforge$setTouchingModernLava(touchingLava);
        if (touchingWater) {
            if (!inWater && !firstUpdate) {
                resetHeight();
            }
            fallDistance = 0.0F;
            inWater = true;
            fire = 0;
        } else {
            inWater = false;
        }
        cir.setReturnValue(inWater);
    }

    @Inject(method = "isInLava", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$modernLavaState(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EntityPlayerSP && viaforge$isModernTarget()) {
            cir.setReturnValue(((ModernPlayerPhysics) this).viaforge$isTouchingModernLava());
        }
    }

    @Unique
    private static void viaforge$applyModernFluidPush(
            EntityPlayerSP player,
            Vec3 accumulatedFlow,
            int flowCount,
            double multiplier
    ) {
        if (flowCount == 0 || accumulatedFlow.lengthVector() * accumulatedFlow.lengthVector() < 1.0E-5D) {
            return;
        }

        Vec3 flow = new Vec3(
                accumulatedFlow.xCoord / flowCount,
                accumulatedFlow.yCoord / flowCount,
                accumulatedFlow.zCoord / flowCount
        );
        flow = new Vec3(
                flow.xCoord * multiplier,
                flow.yCoord * multiplier,
                flow.zCoord * multiplier
        );
        if (Math.abs(player.motionX) < 0.003D
                && Math.abs(player.motionZ) < 0.003D
                && flow.lengthVector() < 0.0045000000000000005D) {
            final Vec3 normalized = flow.normalize();
            flow = new Vec3(
                    normalized.xCoord * 0.0045000000000000005D,
                    normalized.yCoord * 0.0045000000000000005D,
                    normalized.zCoord * 0.0045000000000000005D
            );
        }
        player.motionX += flow.xCoord;
        player.motionY += flow.yCoord;
        player.motionZ += flow.zCoord;
    }

    @Inject(method = "moveEntity", at = @At("HEAD"), require = 0)
    private void viaforge$captureModernStepMovement(
            double x,
            double y,
            double z,
            CallbackInfo ci
    ) {
        viaforge$modernStepDesiredY = isInWeb ? y * 0.05F : y;
        viaforge$modernStepDownAdjusted = false;
        viaforge$baseCollisions = null;
        viaforge$stepCollisions = null;
        viaforge$baseHorizontal = null;
        viaforge$firstStepHorizontal = null;
        viaforge$secondStepHorizontal = null;
        if ((Object) this instanceof EntityPlayerSP && viaforge$isModernTarget()) {
            final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
            viaforge$moveStartX = player.posX;
            viaforge$moveStartZ = player.posZ;
        }
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;addCoord(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 0
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$captureBaseRequestedMovement(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        viaforge$baseRequestedX = x;
        viaforge$baseRequestedZ = z;
        return box.addCoord(x, y, z);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;addCoord(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 1
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$captureStepRequestedMovement(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        viaforge$stepRequestedX = x;
        viaforge$stepRequestedZ = z;
        return box.addCoord(x, y, z);
    }

    @ModifyVariable(method = "moveEntity", at = @At(value = "STORE"), ordinal = 0, require = 0)
    private List<AxisAlignedBB> viaforge$captureBaseCollisions(List<AxisAlignedBB> collisions) {
        viaforge$baseCollisions = collisions;
        return collisions;
    }

    @ModifyVariable(method = "moveEntity", at = @At(value = "STORE"), ordinal = 1, require = 0)
    private List<AxisAlignedBB> viaforge$captureStepCollisions(List<AxisAlignedBB> collisions) {
        viaforge$stepCollisions = collisions;
        return collisions;
    }

    @Inject(method = "moveEntity", at = @At("RETURN"), require = 0)
    private void viaforge$updateMainSupportingBlock(
            double x,
            double y,
            double z,
            CallbackInfo ci
    ) {
        if (!((Object) this instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        final ModernPlayerPhysics physics = (ModernPlayerPhysics) player;
        final float yawRadians = player.rotationYaw * 0.017453292F;
        physics.viaforge$setMinorHorizontalCollision(
                player.isCollidedHorizontally
                        && ModernHorizontalCollision.isMinorCollision(
                        MathHelper.sin(yawRadians),
                        MathHelper.cos(yawRadians),
                        player.moveStrafing,
                        player.moveForward,
                        player.posX - viaforge$moveStartX,
                        player.posZ - viaforge$moveStartZ
                )
        );
        if (!player.onGround) {
            physics.viaforge$setMainSupportingBlock(null, false);
            return;
        }

        final AxisAlignedBB box = player.getEntityBoundingBox();
        final AxisAlignedBB below = new AxisAlignedBB(
                box.minX,
                box.minY - 1.0E-6D,
                box.minZ,
                box.maxX,
                box.minY,
                box.maxZ
        );
        BlockPos support = viaforge$findSupportingBlock(player, below);
        if (support == null
                && !(physics.viaforge$wasSupportingBlockOnGround()
                && physics.viaforge$getMainSupportingBlock() == null)) {
            support = viaforge$findSupportingBlock(player, below.offset(
                    viaforge$moveStartX - player.posX,
                    0.0D,
                    viaforge$moveStartZ - player.posZ
            ));
        }
        physics.viaforge$setMainSupportingBlock(support, true);
        viaforge$applyModernSoulSandFactor(player, support);
    }

    @Unique
    private static void viaforge$applyModernSoulSandFactor(
            EntityPlayerSP player,
            BlockPos support
    ) {
        final BlockPos inBlock = new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ)
        );
        boolean modernApplies = player.worldObj.getBlockState(inBlock).getBlock()
                == Blocks.soul_sand;
        if (!modernApplies && support != null) {
            final BlockPos below = new BlockPos(
                    support.getX(),
                    MathHelper.floor_double(player.posY - 0.500001D),
                    support.getZ()
            );
            modernApplies = player.worldObj.getBlockState(below).getBlock()
                    == Blocks.soul_sand;
        }

        if (modernApplies) {
            player.motionX *= 0.4D;
            player.motionZ *= 0.4D;
        }
    }

    @Unique
    private static BlockPos viaforge$findSupportingBlock(
            EntityPlayerSP player,
            AxisAlignedBB search
    ) {
        final int minX = MathHelper.floor_double(search.minX);
        final int maxX = MathHelper.floor_double(search.maxX);
        final int minY = MathHelper.floor_double(search.minY);
        final int maxY = MathHelper.floor_double(search.maxY);
        final int minZ = MathHelper.floor_double(search.minZ);
        final int maxZ = MathHelper.floor_double(search.maxZ);
        final List<AxisAlignedBB> collisions = new ArrayList<>();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockY = minY; blockY <= maxY; blockY++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    final BlockPos candidate = new BlockPos(blockX, blockY, blockZ);
                    final IBlockState state = player.worldObj.getBlockState(candidate);
                    collisions.clear();
                    state.getBlock().addCollisionBoxesToList(
                            player.worldObj,
                            candidate,
                            state,
                            search,
                            collisions,
                            player
                    );
                    boolean reachesFeet = false;
                    for (AxisAlignedBB collision : collisions) {
                        if (ModernHorizontalCollision.isSupportingCollision(
                                collision.maxY, search.maxY
                        )) {
                            reachesFeet = true;
                            break;
                        }
                    }
                    if (!reachesFeet) {
                        continue;
                    }

                    final double dx = player.posX - ((double) blockX + 0.5D);
                    final double dy = player.posY - ((double) blockY + 0.5D);
                    final double dz = player.posZ - ((double) blockZ + 0.5D);
                    final double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance
                            || distance == bestDistance
                            && (best == null || viaforge$hasSupportingPriority(candidate, best))) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    @Unique
    private static boolean viaforge$hasSupportingPriority(BlockPos first, BlockPos second) {
        if (first.getY() != second.getY()) {
            return first.getY() > second.getY();
        }
        if (first.getZ() != second.getZ()) {
            return first.getZ() > second.getZ();
        }
        return first.getX() > second.getX();
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateXOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 0
            ),
            require = 0
    )
    private double viaforge$resolveBaseX(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedX
    ) {
        if (viaforge$useModernHorizontalCollision() && viaforge$baseCollisions != null) {
            if (viaforge$baseHorizontal == null) {
                viaforge$baseHorizontal = ModernHorizontalCollision.resolve(
                        moving,
                        viaforge$baseCollisions,
                        viaforge$baseRequestedX,
                        viaforge$baseRequestedZ,
                        viaforge$boxOperations
                );
            }
            return viaforge$baseHorizontal.getX();
        }
        return collision.calculateXOffset(moving, requestedX);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateZOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 0
            ),
            require = 0
    )
    private double viaforge$resolveBaseZ(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedZ
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$baseHorizontal != null
                ? viaforge$baseHorizontal.getZ()
                : collision.calculateZOffset(moving, requestedZ);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateXOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 1
            ),
            require = 0
    )
    private double viaforge$resolveFirstStepX(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedX
    ) {
        if (viaforge$useModernHorizontalCollision() && viaforge$stepCollisions != null) {
            if (viaforge$firstStepHorizontal == null) {
                viaforge$firstStepHorizontal = ModernHorizontalCollision.resolve(
                        moving,
                        viaforge$stepCollisions,
                        viaforge$stepRequestedX,
                        viaforge$stepRequestedZ,
                        viaforge$boxOperations
                );
            }
            return viaforge$firstStepHorizontal.getX();
        }
        return collision.calculateXOffset(moving, requestedX);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateZOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 1
            ),
            require = 0
    )
    private double viaforge$resolveFirstStepZ(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedZ
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$firstStepHorizontal != null
                ? viaforge$firstStepHorizontal.getZ()
                : collision.calculateZOffset(moving, requestedZ);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateXOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 2
            ),
            require = 0
    )
    private double viaforge$resolveSecondStepX(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedX
    ) {
        if (viaforge$useModernHorizontalCollision() && viaforge$stepCollisions != null) {
            if (viaforge$secondStepHorizontal == null) {
                viaforge$secondStepHorizontal = ModernHorizontalCollision.resolve(
                        moving,
                        viaforge$stepCollisions,
                        viaforge$stepRequestedX,
                        viaforge$stepRequestedZ,
                        viaforge$boxOperations
                );
            }
            return viaforge$secondStepHorizontal.getX();
        }
        return collision.calculateXOffset(moving, requestedX);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateZOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 2
            ),
            require = 0
    )
    private double viaforge$resolveSecondStepZ(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedZ
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$secondStepHorizontal != null
                ? viaforge$secondStepHorizontal.getZ()
                : collision.calculateZOffset(moving, requestedZ);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;offset(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 2
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$applyBaseHorizontal(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$baseHorizontal != null
                ? viaforge$baseHorizontal.getBox()
                : box.offset(x, y, z);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;offset(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 3
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$skipBaseSecondHorizontalOffset(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$baseHorizontal != null
                ? box
                : box.offset(x, y, z);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;offset(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 5
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$applyFirstStepHorizontal(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$firstStepHorizontal != null
                ? viaforge$firstStepHorizontal.getBox()
                : box.offset(x, y, z);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;offset(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 6
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$skipFirstStepSecondHorizontalOffset(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$firstStepHorizontal != null
                ? box
                : box.offset(x, y, z);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;offset(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 8
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$applySecondStepHorizontal(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$secondStepHorizontal != null
                ? viaforge$secondStepHorizontal.getBox()
                : box.offset(x, y, z);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;offset(DDD)Lnet/minecraft/util/AxisAlignedBB;",
                    ordinal = 9
            ),
            require = 0
    )
    private AxisAlignedBB viaforge$skipSecondStepSecondHorizontalOffset(
            AxisAlignedBB box,
            double x,
            double y,
            double z
    ) {
        return viaforge$useModernHorizontalCollision() && viaforge$secondStepHorizontal != null
                ? box
                : box.offset(x, y, z);
    }

    @Unique
    private boolean viaforge$useModernHorizontalCollision() {
        return (Object) this instanceof EntityPlayerSP && viaforge$isModernTarget();
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateYOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 0
            ),
            require = 0
    )
    private double viaforge$resolveBaseY(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedY
    ) {
        return viaforge$calculateModernYOffset(collision, moving, requestedY);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateYOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 1
            ),
            require = 0
    )
    private double viaforge$resolveFirstStepY(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedY
    ) {
        return viaforge$calculateModernYOffset(collision, moving, requestedY);
    }

    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateYOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 2
            ),
            require = 0
    )
    private double viaforge$resolveSecondStepY(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedY
    ) {
        return viaforge$calculateModernYOffset(collision, moving, requestedY);
    }

    /** 1.14+ preserves the requested Y movement while resolving a step down. */
    @Redirect(
            method = "moveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/AxisAlignedBB;calculateYOffset(Lnet/minecraft/util/AxisAlignedBB;D)D",
                    ordinal = 3
            ),
            require = 0
    )
    private double viaforge$resolveStepDownY(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double stepDown
    ) {
        if (!viaforge$modernStepDownAdjusted
                && (Object) this instanceof EntityPlayerSP
                && viaforge$isModernTarget()) {
            viaforge$modernStepDownAdjusted = true;
            stepDown += viaforge$modernStepDesiredY;
        }
        return viaforge$calculateModernYOffset(collision, moving, stepDown);
    }

    @Unique
    private double viaforge$calculateModernYOffset(
            AxisAlignedBB collision,
            AxisAlignedBB moving,
            double requestedY
    ) {
        if (!viaforge$useModernHorizontalCollision()) {
            return collision.calculateYOffset(moving, requestedY);
        }
        if (!ModernHorizontalCollision.overlaps(
                moving.minX, moving.maxX, collision.minX, collision.maxX
        ) || !ModernHorizontalCollision.overlaps(
                moving.minZ, moving.maxZ, collision.minZ, collision.maxZ
        )) {
            return requestedY;
        }
        return ModernHorizontalCollision.calculateOffset(
                collision.minY, collision.maxY,
                moving.minY, moving.maxY,
                moving.minX, moving.maxX,
                collision.minX, collision.maxX,
                requestedY
        );
    }

    /** 1.14+ normalizes movement input using doubles instead of floats. */
    @Inject(method = "moveFlying", at = @At("HEAD"), cancellable = true, require = 0)
    private void viaforge$modernMoveFlying(
            float strafe,
            float forward,
            float friction,
            CallbackInfo ci
    ) {
        if (!((Object) this instanceof EntityPlayerSP) || !viaforge$isModernTarget()) {
            return;
        }

        final EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        final double lengthSquared = (double) strafe * (double) strafe
                + (double) forward * (double) forward;
        if (lengthSquared >= 1.0E-7D) {
            final double scale = lengthSquared > 1.0D
                    ? (double) friction / Math.sqrt(lengthSquared)
                    : friction;
            final double scaledStrafe = (double) strafe * scale;
            final double scaledForward = (double) forward * scale;
            final float yaw = player.rotationYaw * ((float) Math.PI / 180.0F);
            final float sin = MathHelper.sin(yaw);
            final float cos = MathHelper.cos(yaw);

            player.motionX += scaledStrafe * (double) cos - scaledForward * (double) sin;
            player.motionZ += scaledForward * (double) cos + scaledStrafe * (double) sin;
        }
        ci.cancel();
    }

    @ModifyVariable(method = "setSize", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float viaforge$modernEntityWidth(float width) {
        if (!viaforge$isModernTarget()) {
            return width;
        }

        final Object entity = this;
        if (entity instanceof EntityRabbit) {
            return width * (0.4F / 0.6F);
        } else if (entity instanceof EntitySquid) {
            return width * (0.8F / 0.95F);
        } else if (entity instanceof EntityHorse) {
            return width * (1.3964844F / 1.4F);
        } else if (entity instanceof EntityBoat) {
            return width * (1.375F / 1.5F);
        } else if (entity instanceof EntitySkeleton && width > 0.7F) {
            return width * (0.7F / 0.72F);
        } else if (entity instanceof EntitySlime) {
            return width * (0.52F / 0.51000005F);
        }
        return width;
    }

    @ModifyVariable(method = "setSize", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float viaforge$modernEntityHeight(float height) {
        if (!viaforge$isModernTarget()) {
            return height;
        }

        final Object entity = this;
        if (entity instanceof EntityRabbit) {
            return height * (0.5F / 0.7F);
        } else if (entity instanceof EntitySquid) {
            return height * (0.8F / 0.95F);
        } else if (entity instanceof EntityBoat) {
            return height * (0.5625F / 0.6F);
        } else if (entity instanceof EntityCow) {
            return height * (1.4F / 1.3F);
        } else if (entity instanceof EntityIronGolem) {
            return height * (2.7F / 2.9F);
        } else if (entity instanceof EntitySkeleton) {
            return height > 2.2F
                    ? height * (2.4F / 2.535F)
                    : height * (1.99F / 1.95F);
        } else if (entity instanceof EntityWolf) {
            return height * (0.85F / 0.8F);
        } else if (entity instanceof EntityVillager) {
            return height * (1.95F / 1.8F);
        } else if (entity instanceof EntitySlime) {
            return height * (0.52F / 0.51000005F);
        }
        return height;
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }

}
