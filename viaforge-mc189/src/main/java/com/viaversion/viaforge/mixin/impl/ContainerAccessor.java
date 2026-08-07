/*
 * This file is part of ViaForge.
 */
package com.viaversion.viaforge.mixin.impl;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Container.class)
public interface ContainerAccessor {

    @Invoker("addSlotToContainer")
    Slot viaforge$addSlotToContainer(Slot slot);
}
