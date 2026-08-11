/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 * Copyright (C) 2021-2026 Florian Reuth <git@florianreuth.de> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viaforge.compat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class ModernFluidPhysics {

    private ModernFluidPhysics() {
    }

    /** Fluid surface height used by the 1.13+ fluid collision checks. */
    public static float getFluidHeight(World world, BlockPos position, Material material) {
        final IBlockState state = world.getBlockState(position);
        final Block block = state.getBlock();
        if (block.getMaterial() != material) {
            return 0.0F;
        }

        if (world.getBlockState(position.up()).getBlock().getMaterial() == material) {
            return 1.0F;
        }

        if (!(block instanceof BlockLiquid)) {
            return 8.0F / 9.0F;
        }

        final int level = state.getValue(BlockLiquid.LEVEL);
        return (level & 8) == 8 ? 8.0F / 9.0F : (8 - level) / 9.0F;
    }

    public static float getWaterHeight(World world, BlockPos position) {
        return getFluidHeight(world, position, Material.water);
    }

    public static float getLavaHeight(World world, BlockPos position) {
        return getFluidHeight(world, position, Material.lava);
    }

    /** Reproduce FlowingFluid#getFlow using modern fluid heights. */
    public static Vec3 getFlow(World world, BlockPos position, Material material) {
        final IBlockState currentState = world.getBlockState(position);
        final float currentHeight = getOwnHeight(currentState, material);
        double flowX = 0.0D;
        double flowZ = 0.0D;

        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            final BlockPos neighborPosition = position.offset(direction);
            final IBlockState neighborState = world.getBlockState(neighborPosition);
            final Material neighborMaterial = neighborState.getBlock().getMaterial();
            if (neighborMaterial.isLiquid() && neighborMaterial != material) {
                continue;
            }

            float neighborHeight = getOwnHeight(neighborState, material);
            float heightDifference = 0.0F;
            if (neighborHeight == 0.0F) {
                if (!neighborMaterial.blocksMovement()) {
                    final IBlockState belowState = world.getBlockState(neighborPosition.down());
                    neighborHeight = getOwnHeight(belowState, material);
                    if (neighborHeight > 0.0F) {
                        heightDifference = currentHeight - neighborHeight - 8.0F / 9.0F;
                    }
                }
            } else {
                heightDifference = currentHeight - neighborHeight;
            }

            if (heightDifference != 0.0F) {
                flowX += (double) ((float) direction.getFrontOffsetX() * heightDifference);
                flowZ += (double) ((float) direction.getFrontOffsetZ() * heightDifference);
            }
        }

        Vec3 flow = new Vec3(flowX, 0.0D, flowZ);
        if (isFalling(currentState)) {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                final BlockPos neighborPosition = position.offset(direction);
                if (isModernSolidFace(world, neighborPosition, direction, material)
                        || isModernSolidFace(world, neighborPosition.up(), direction, material)) {
                    flow = flow.normalize().addVector(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }
        return flow.normalize();
    }

    private static float getOwnHeight(IBlockState state, Material material) {
        final Block block = state.getBlock();
        if (block.getMaterial() != material) {
            return 0.0F;
        }
        if (!(block instanceof BlockLiquid)) {
            return 8.0F / 9.0F;
        }

        final int level = state.getValue(BlockLiquid.LEVEL);
        return (level & 8) == 8 ? 8.0F / 9.0F : (8 - level) / 9.0F;
    }

    private static boolean isFalling(IBlockState state) {
        return state.getBlock() instanceof BlockLiquid
                && (state.getValue(BlockLiquid.LEVEL) & 8) == 8;
    }

    private static boolean isModernSolidFace(
            World world,
            BlockPos position,
            EnumFacing direction,
            Material fluidMaterial
    ) {
        final IBlockState state = world.getBlockState(position);
        final Block block = state.getBlock();
        if (block.getMaterial() == fluidMaterial
                || block == Blocks.ice
                || block == Blocks.packed_ice) {
            return false;
        }
        return block.isSideSolid(world, position, direction);
    }

}
