/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaforge.compat.ModernOffhandKeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Arrays;

@Mixin(GameSettings.class)
public class MixinGameSettings implements ModernOffhandKeyBinding {

    @Unique
    private KeyBinding viaforge$swapOffhandKey;

    @Unique
    private boolean viaforge$offhandKeyRegistered;

    @Override
    public KeyBinding viaforge$getSwapOffhandKey() {
        return viaforge$swapOffhandKey;
    }

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At("RETURN"))
    private void viaforge$registerOffhandKey(Minecraft minecraft, File optionsFile, CallbackInfo ci) {
        if (viaforge$offhandKeyRegistered) {
            return;
        }
        viaforge$offhandKeyRegistered = true;
        viaforge$swapOffhandKey = new KeyBinding("key.swapOffhand", Keyboard.KEY_F, "key.categories.inventory");
        final GameSettings settings = (GameSettings) (Object) this;
        settings.keyBindings = Arrays.copyOf(settings.keyBindings, settings.keyBindings.length + 1);
        settings.keyBindings[settings.keyBindings.length - 1] = viaforge$swapOffhandKey;
        KeyBinding.resetKeyBindingArrayAndHash();
    }
}
