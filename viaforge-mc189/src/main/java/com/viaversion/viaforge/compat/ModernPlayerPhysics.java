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

import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;

public interface ModernPlayerPhysics {

    boolean viaforge$isModernSwimming();

    float viaforge$getModernEyeHeight();

    double viaforge$getModernWaterHeight();

    void viaforge$setModernWaterHeight(double height);

    double viaforge$getModernLavaHeight();

    void viaforge$setModernLavaHeight(double height);

    boolean viaforge$isTouchingModernLava();

    void viaforge$setTouchingModernLava(boolean touching);

    BlockPos viaforge$getMainSupportingBlock();

    boolean viaforge$wasSupportingBlockOnGround();

    void viaforge$setMainSupportingBlock(BlockPos position, boolean onGround);

    boolean viaforge$isMinorHorizontalCollision();

    void viaforge$setMinorHorizontalCollision(boolean minor);

    void viaforge$markLocalItemUseFinished();

    void viaforge$confirmServerItemUseFinished();

    void viaforge$updateModernMovementInput(MovementInput input);

}
