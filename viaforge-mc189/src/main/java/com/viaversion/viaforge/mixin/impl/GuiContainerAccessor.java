/*
 * This file is part of ViaForge.
 */
package com.viaversion.viaforge.mixin.impl;

import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiContainer.class)
public interface GuiContainerAccessor {

    @Accessor("guiLeft")
    int viaforge$getGuiLeft();

    @Accessor("guiTop")
    int viaforge$getGuiTop();
}
