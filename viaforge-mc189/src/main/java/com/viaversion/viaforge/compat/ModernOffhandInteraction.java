/*
 * This file is part of ViaForge.
 */
package com.viaversion.viaforge.compat;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_9;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

/** Sends 1.9 hand-aware packets from the 1.8 client. */
public final class ModernOffhandInteraction {

    private static boolean clientOffhandAction;

    private ModernOffhandInteraction() {
    }

    public static boolean hasOffhand(EntityPlayer player) {
        return player != null && getOffhand(player) != null;
    }

    public static ItemStack getOffhand(EntityPlayer player) {
        if (!(player.inventory instanceof ModernOffhandInventory)) {
            return null;
        }
        return ((ModernOffhandInventory) player.inventory).viaforge$getOffhand();
    }

    public static boolean shouldUseItemAfterBlock(EntityPlayer player) {
        final ItemStack stack = getOffhand(player);
        return stack != null && stack.getItemUseAction() != EnumAction.NONE;
    }

    public static void beginRightClick() {
        clientOffhandAction = false;
    }

    public static boolean wasClientOffhandAction() {
        return clientOffhandAction;
    }

    public static boolean sendSwapItemWithOffhand(EntityPlayerSP player) {
        final UserConnection connection = getConnection(player);
        if (connection == null) {
            return false;
        }

        final PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.PLAYER_ACTION, connection);
        wrapper.write(Types.VAR_INT, 6);
        wrapper.write(Types.BLOCK_POSITION1_8,
                new com.viaversion.viaversion.api.minecraft.BlockPosition(0, 0, 0));
        // PLAYER_ACTION face is a real block face for digging actions; the
        // swap-with-offhand action is directionless and must use DOWN (0).
        wrapper.write(Types.UNSIGNED_BYTE, (short) 0);
        wrapper.scheduleSendToServer(Protocol1_9To1_8.class);
        return true;
    }

    public static boolean sendUseItem(EntityPlayerSP player) {
        final ItemStack stack = getOffhand(player);
        final UserConnection connection = getConnection(player);
        if (stack == null || connection == null) {
            return false;
        }

        final PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.USE_ITEM, connection);
        wrapper.write(Types.VAR_INT, 1);
        wrapper.scheduleSendToServer(Protocol1_9To1_8.class);
        clientOffhandAction = true;

        final int previousSize = stack.stackSize;
        final ItemStack result = stack.useItemRightClick(player.worldObj, player);
        if (result != stack || result == null || result.stackSize != previousSize) {
            ((ModernOffhandInventory) player.inventory).viaforge$setOffhand(
                    result != null && result.stackSize > 0 ? result : null
            );
        }
        return true;
    }

    public static boolean sendUseItemOnBlock(
            EntityPlayerSP player,
            BlockPos pos,
            EnumFacing face,
            Vec3 hitVec
    ) {
        final UserConnection connection = getConnection(player);
        if (connection == null || getOffhand(player) == null) {
            return false;
        }

        final PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.USE_ITEM_ON, connection);
        wrapper.write(Types.BLOCK_POSITION1_8,
                new com.viaversion.viaversion.api.minecraft.BlockPosition(pos.getX(), pos.getY(), pos.getZ()));
        wrapper.write(Types.VAR_INT, face.getIndex());
        wrapper.write(Types.VAR_INT, 1);
        writeCursorPosition(wrapper, hitVec.xCoord - pos.getX());
        writeCursorPosition(wrapper, hitVec.yCoord - pos.getY());
        writeCursorPosition(wrapper, hitVec.zCoord - pos.getZ());
        wrapper.scheduleSendToServer(Protocol1_9To1_8.class);
        clientOffhandAction = true;
        if (!shouldUseItemAfterBlock(player)) {
            sendSwing(connection, player);
        }
        return true;
    }

    private static void writeCursorPosition(PacketWrapper wrapper, double coordinate) {
        final int scaled = Math.max(0, Math.min(16, (int) (coordinate * 16.0D)));
        wrapper.write(Types.UNSIGNED_BYTE, (short) scaled);
    }

    public static void sendInteract(EntityPlayer player, Entity target) {
        final UserConnection connection = getConnection(player);
        if (connection == null) {
            return;
        }

        final PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.INTERACT, connection);
        wrapper.write(Types.VAR_INT, target.getEntityId());
        wrapper.write(Types.VAR_INT, 0);
        wrapper.write(Types.VAR_INT, 1);
        wrapper.scheduleSendToServer(Protocol1_9To1_8.class);
        clientOffhandAction = true;
    }

    public static void sendInteractAt(EntityPlayer player, Entity target, Vec3 hit) {
        final UserConnection connection = getConnection(player);
        if (connection == null) {
            return;
        }

        final PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.INTERACT, connection);
        wrapper.write(Types.VAR_INT, target.getEntityId());
        wrapper.write(Types.VAR_INT, 2);
        wrapper.write(Types.FLOAT, (float) hit.xCoord);
        wrapper.write(Types.FLOAT, (float) hit.yCoord);
        wrapper.write(Types.FLOAT, (float) hit.zCoord);
        wrapper.write(Types.VAR_INT, 1);
        wrapper.scheduleSendToServer(Protocol1_9To1_8.class);
        clientOffhandAction = true;
    }

    private static void sendSwing(UserConnection connection, EntityPlayer player) {
        final PacketWrapper swing = PacketWrapper.create(ServerboundPackets1_9.SWING, connection);
        swing.write(Types.VAR_INT, 1);
        swing.scheduleSendToServer(Protocol1_9To1_8.class);
        if (player instanceof ModernOffhandPlayer) {
            ((ModernOffhandPlayer) player).viaforge$swingOffhand();
        }
    }

    private static UserConnection getConnection(EntityPlayer player) {
        if (!(player instanceof EntityPlayerSP)) {
            return null;
        }
        final NetHandlerPlayClient netHandler = ((EntityPlayerSP) player).sendQueue;
        return netHandler.getNetworkManager().channel()
                .attr(com.viaversion.viaforge.common.ViaForgeCommon.VF_VIA_USER).get();
    }
}
