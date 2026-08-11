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

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import com.viaversion.viaforge.common.ViaForgeCommon;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/** Replaces ViaBackwards' placeholder sequence in the final 1.20.6 packet. */
@ChannelHandler.Sharable
public final class ModernSequenceEncodeHandler extends MessageToMessageEncoder<ByteBuf> {

    public static final String NAME = "viaforge-modern-sequence";

    private final UserConnection connection;

    public ModernSequenceEncodeHandler(UserConnection connection) {
        this.connection = connection;
    }

    @Override
    protected void encode(ChannelHandlerContext context, ByteBuf buffer, List<Object> output) {
        patchSequence(buffer);
        output.add(buffer.retain());
    }

    private void patchSequence(ByteBuf buffer) {
        if (!isModernTarget()
                || connection.getProtocolInfo().getState(Direction.SERVERBOUND) != State.PLAY
                || !buffer.isReadable()) {
            return;
        }

        final ByteBuf input = buffer.duplicate();
        final int packetId;
        final int sequenceIndex;
        try {
            packetId = Types.VAR_INT.readPrimitive(input);
            if (packetId == ServerboundPackets1_20_5.USE_ITEM.getId()) {
                Types.VAR_INT.readPrimitive(input); // Hand
                sequenceIndex = input.readerIndex();
            } else if (packetId == ServerboundPackets1_20_5.USE_ITEM_ON.getId()) {
                Types.VAR_INT.readPrimitive(input); // Hand
                input.skipBytes(Long.BYTES); // Block position
                Types.VAR_INT.readPrimitive(input); // Direction
                input.skipBytes(Float.BYTES * 3); // Cursor position
                input.skipBytes(1); // Inside block
                sequenceIndex = input.readerIndex();
            } else if (packetId == ServerboundPackets1_20_5.PLAYER_ACTION.getId()) {
                final int action = Types.VAR_INT.readPrimitive(input);
                if (action != 0 && action != 2) {
                    return;
                }
                input.skipBytes(Long.BYTES); // Block position
                input.skipBytes(1); // Direction
                sequenceIndex = input.readerIndex();
            } else {
                return;
            }

            Types.VAR_INT.readPrimitive(input);
        } catch (IndexOutOfBoundsException ignored) {
            return;
        }

        final int sequenceEnd = input.readerIndex();
        final byte[] trailingData = new byte[buffer.writerIndex() - sequenceEnd];
        buffer.getBytes(sequenceEnd, trailingData);

        ModernSequenceStorage storage = connection.get(ModernSequenceStorage.class);
        if (storage == null) {
            storage = new ModernSequenceStorage();
            connection.put(storage);
        }

        buffer.writerIndex(sequenceIndex);
        Types.VAR_INT.writePrimitive(buffer, storage.next());
        buffer.writeBytes(trailingData);
    }

    private static boolean isModernTarget() {
        final ViaForgeCommon manager = ViaForgeCommon.getManager();
        return manager != null && manager.getTargetVersion() == ProtocolVersion.v1_20_5;
    }
}
