/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 * Copyright (C) 2021-2026 Florian Reuth <git@florianreuth.de> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viaforge.mixin.impl;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.connection.ConnectionDetails;
import com.viaversion.viaforge.common.ViaForgeCommon;
import com.viaversion.viaforge.compat.ModernPlayerPhysics;
import com.viaversion.viaforge.compat.ModernOffhandInventory;
import com.viaversion.viaforge.compat.ModernOffhandStorage;
import com.viaversion.viaforge.compat.ModernSequenceStorage;
import com.viaversion.viarewind.protocol.v1_9to1_8.storage.PlayerPositionTracker;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    @Shadow
    @Final
    private NetworkManager netManager;

    @Unique
    private final AtomicInteger viaforge$earlyTeleportResponses = new AtomicInteger();

    @Inject(method = "handleSetSlot", at = @At("RETURN"), require = 0)
    private void viaforge$syncOffhandSlot(S2FPacketSetSlot packet, CallbackInfo ci) {
        if (packet.func_149175_c() == ModernOffhandStorage.CLIENT_WINDOW_ID
                && packet.func_149173_d() == 45
                && Minecraft.getMinecraft().thePlayer != null) {
            ((ModernOffhandInventory) Minecraft.getMinecraft().thePlayer.inventory)
                    .viaforge$setOffhand(packet.func_149174_e());
        }
    }

    @Inject(method = "handleWindowItems", at = @At("RETURN"), require = 0)
    private void viaforge$syncOffhandWindow(S30PacketWindowItems packet, CallbackInfo ci) {
        if (packet.func_148911_c() != 0
                || Minecraft.getMinecraft().thePlayer == null
                || packet.getItemStacks().length <= 45) {
            return;
        }

        ((ModernOffhandInventory) Minecraft.getMinecraft().thePlayer.inventory)
                .viaforge$setOffhand(packet.getItemStacks()[45]);
    }

    /** Match Grim's authoritative completion surface for consumed items. */
    @Inject(method = "handleSetSlot", at = @At("RETURN"), require = 0)
    private void viaforge$confirmHeldItemUseFinished(S2FPacketSetSlot packet, CallbackInfo ci) {
        if (!viaforge$isModernTarget()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.thePlayer.isUsingItem()) {
            return;
        }

        final int window = packet.func_149175_c();
        final int slot = packet.func_149173_d();
        final boolean mainHandUpdate = window == 0
                && slot == minecraft.thePlayer.inventory.currentItem + 36;
        final boolean offhandUpdate = window == ModernOffhandStorage.CLIENT_WINDOW_ID
                && slot == 45;
        if (!mainHandUpdate && !offhandUpdate) {
            return;
        }

        ((ModernPlayerPhysics) minecraft.thePlayer).viaforge$confirmServerItemUseFinished();
    }

    @Inject(method = "handleJoinGame", at = @At("RETURN"))
    public void sendConnectionDetails(CallbackInfo ci) {
        viaforge$earlyTeleportResponses.set(0);
        final Channel channel = Minecraft.getMinecraft().thePlayer.sendQueue.getNetworkManager().channel();
        final UserConnection connection = channel.attr(ViaForgeCommon.VF_VIA_USER).get();
        if (connection == null) {
            return;
        }

        connection.put(new ModernSequenceStorage());
        ConnectionDetails.sendConnectionDetails(connection, ConnectionDetails.MOD_CHANNEL);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void viaforge$resetModernSequence(S07PacketRespawn packet, CallbackInfo ci) {
        viaforge$earlyTeleportResponses.set(0);
        final UserConnection connection = netManager.channel().attr(ViaForgeCommon.VF_VIA_USER).get();
        if (connection == null) {
            return;
        }

        ModernSequenceStorage storage = connection.get(ModernSequenceStorage.class);
        if (storage == null) {
            storage = new ModernSequenceStorage();
            connection.put(storage);
        } else {
            storage.reset();
        }
    }

    /**
     * Grim brackets teleports with transactions. A 1.8 client normally waits
     * for the main thread before replying to S08, which can let the following
     * transaction overtake the required POS+LOOK. Reply on the channel event
     * loop. The later vanilla response is suppressed because ViaRewind keeps
     * the first movement packet after generating ACCEPT_TELEPORTATION; sending
     * another identical C06 creates an extra tick packet for Grim's Timer.
     */
    @Inject(method = "handlePlayerPosLook", at = @At("HEAD"))
    private void viaforge$sendEarlyTeleportResponse(S08PacketPlayerPosLook packet, CallbackInfo ci) {
        if (!viaforge$isModernTarget() || !netManager.channel().eventLoop().inEventLoop()) {
            return;
        }

        final UserConnection connection = netManager.channel().attr(ViaForgeCommon.VF_VIA_USER).get();
        final PlayerPositionTracker tracker = connection != null
                ? connection.get(PlayerPositionTracker.class)
                : null;
        if (tracker != null && tracker.getConfirmId() != -1) {
            viaforge$earlyTeleportResponses.incrementAndGet();
            viaforge$sendTrackerPosition(netManager, tracker);
        }
    }

    @Redirect(
            method = "handlePlayerPosLook",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V"
            )
    )
    private void viaforge$sendExactTeleportResponse(NetworkManager networkManager, Packet<?> packet) {
        if (!viaforge$isModernTarget() || !(packet instanceof C03PacketPlayer.C06PacketPlayerPosLook)) {
            networkManager.sendPacket(packet);
            return;
        }

        final UserConnection connection = networkManager.channel().attr(ViaForgeCommon.VF_VIA_USER).get();
        final PlayerPositionTracker tracker = connection != null
                ? connection.get(PlayerPositionTracker.class)
                : null;
        if (viaforge$consumeEarlyTeleportResponse()) {
            return;
        }

        if (tracker == null || tracker.getConfirmId() == -1) {
            networkManager.sendPacket(packet);
            return;
        }

        viaforge$sendTrackerPosition(networkManager, tracker);
    }

    @Unique
    private boolean viaforge$consumeEarlyTeleportResponse() {
        while (true) {
            final int pending = viaforge$earlyTeleportResponses.get();
            if (pending == 0) {
                return false;
            }
            if (viaforge$earlyTeleportResponses.compareAndSet(pending, pending - 1)) {
                return true;
            }
        }
    }

    @Unique
    private static void viaforge$sendTrackerPosition(
            NetworkManager networkManager,
            PlayerPositionTracker tracker
    ) {
        networkManager.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(
                tracker.getPosX(),
                tracker.getPosY(),
                tracker.getPosZ(),
                tracker.getYaw(),
                tracker.getPitch(),
                false
        ));
    }

    @Unique
    private static boolean viaforge$isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }

}
