/*
 * This file is part of ViaForge.
 */
package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaforge.common.ViaForgeCommon;
import com.viaversion.viaforge.compat.ModernOffhandStorage;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viarewind.protocol.v1_9to1_8.rewriter.BlockItemPacketRewriter1_9;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_8;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockItemPacketRewriter1_9.class, remap = false)
public abstract class MixinViaRewindBlockItemPacketRewriter {

    @Shadow
    public abstract Item handleItemToClient(UserConnection connection, Item item);

    @Inject(method = "lambda$registerPackets$3", at = @At("RETURN"), remap = false)
    private void viaforge$keepOffhandSlot(PacketWrapper wrapper, CallbackInfo ci) {
        if (!viaforge$isModernTarget() || !wrapper.is(Types.BYTE, 0)
                || !wrapper.is(Types.SHORT, 0)) {
            return;
        }

        final byte window = wrapper.get(Types.BYTE, 0);
        final short slot = wrapper.get(Types.SHORT, 0);
        if (window == 0 && slot == 45 && wrapper.isCancelled()) {
            wrapper.set(Types.BYTE, 0, ModernOffhandStorage.CLIENT_WINDOW_ID);
            wrapper.setCancelled(false);
        }
    }

    @Inject(method = "lambda$registerPackets$2", at = @At("HEAD"), remap = false)
    private void viaforge$captureOffhandWindow(PacketWrapper wrapper, CallbackInfo ci) {
        if (!viaforge$isModernTarget()) {
            return;
        }

        final Object input = wrapper;
        if (!(input instanceof com.viaversion.viaversion.protocol.packet.PacketWrapperImpl)) {
            return;
        }

        final io.netty.buffer.ByteBuf buffer =
                ((com.viaversion.viaversion.protocol.packet.PacketWrapperImpl) input).getInputBuffer();
        if (buffer == null) {
            return;
        }

        final io.netty.buffer.ByteBuf duplicate = buffer.duplicate();
        try {
            final short window = Types.UNSIGNED_BYTE.read(duplicate);
            final Item[] items = Types.ITEM1_8_SHORT_ARRAY.read(duplicate);
            if (window == 0 && items.length == 46) {
                final Item offhand = items[45] == null ? null : handleItemToClient(wrapper.user(), items[45]);
                ModernOffhandStorage storage = wrapper.user().get(ModernOffhandStorage.class);
                if (storage == null) {
                    storage = new ModernOffhandStorage();
                    wrapper.user().put(storage);
                }
                storage.setItem(offhand);
            }
        } catch (RuntimeException ignored) {
            // Non-standard container payloads must not break the connection.
        }
    }

    @Inject(method = "lambda$registerPackets$2", at = @At("RETURN"), remap = false)
    private void viaforge$sendCapturedOffhand(PacketWrapper wrapper, CallbackInfo ci) {
        if (!viaforge$isModernTarget()) {
            return;
        }

        final ModernOffhandStorage storage = wrapper.user().get(ModernOffhandStorage.class);
        if (storage == null || !storage.hasPendingItem()) {
            return;
        }

        final Item item = storage.takeItem();

        final PacketWrapper slot = wrapper.create(ClientboundPackets1_8.CONTAINER_SET_SLOT);
        slot.write(Types.BYTE, ModernOffhandStorage.CLIENT_WINDOW_ID);
        slot.write(Types.SHORT, (short) 45);
        slot.write(Types.ITEM1_8, item);
        slot.scheduleSend(Protocol1_9To1_8.class);
    }

    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }
}
