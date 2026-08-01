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
import net.minecraft.util.BlockPos;
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

}
