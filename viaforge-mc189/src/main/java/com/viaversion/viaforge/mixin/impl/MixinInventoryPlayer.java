/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaforge.compat.ModernOffhandInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryPlayer.class)
public class MixinInventoryPlayer implements ModernOffhandInventory {

    @Unique
    private ItemStack viaforge$offhand;

    @Override
    public ItemStack viaforge$getOffhand() {
        return viaforge$offhand;
    }

    @Override
    public void viaforge$setOffhand(ItemStack stack) {
        viaforge$offhand = stack;
    }

    @Inject(method = "getStackInSlot", at = @At("HEAD"), cancellable = true)
    private void viaforge$getOffhandSlot(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 45) {
            cir.setReturnValue(viaforge$offhand);
        }
    }

    @Inject(method = "setInventorySlotContents", at = @At("HEAD"), cancellable = true)
    private void viaforge$setOffhandSlot(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot == 45) {
            viaforge$offhand = stack;
            ci.cancel();
        }
    }

    @Inject(method = "decrStackSize", at = @At("HEAD"), cancellable = true)
    private void viaforge$decreaseOffhand(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != 45) {
            return;
        }

        if (viaforge$offhand == null) {
            cir.setReturnValue(null);
            return;
        }

        final ItemStack result;
        if (viaforge$offhand.stackSize <= amount) {
            result = viaforge$offhand;
            viaforge$offhand = null;
        } else {
            result = viaforge$offhand.splitStack(amount);
            if (viaforge$offhand.stackSize <= 0) {
                viaforge$offhand = null;
            }
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "removeStackFromSlot", at = @At("HEAD"), cancellable = true)
    private void viaforge$removeOffhand(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 45) {
            final ItemStack result = viaforge$offhand;
            viaforge$offhand = null;
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "isItemValidForSlot", at = @At("HEAD"), cancellable = true)
    private void viaforge$validateOffhand(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == 45) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void viaforge$clearOffhand(CallbackInfo ci) {
        viaforge$offhand = null;
    }

    @Inject(method = "copyInventory", at = @At("RETURN"))
    private void viaforge$copyOffhand(InventoryPlayer source, CallbackInfo ci) {
        if (source instanceof ModernOffhandInventory) {
            viaforge$offhand = ((ModernOffhandInventory) source).viaforge$getOffhand();
        }
    }
}
