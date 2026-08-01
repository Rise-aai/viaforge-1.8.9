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

package com.viaversion.viaforge.mixin.impl.connect;

import com.viaversion.viaforge.common.ViaForgeCommon;
import com.viaversion.viaforge.common.extended.ExtendedNetworkManager;
import com.viaversion.viaforge.compat.ModernSequenceEncodeHandler;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.NetworkManager$5")
public class MixinNetworkManager_5 {

    @Final
    @Mutable
    NetworkManager val$networkmanager;

    @Inject(method = "initChannel", at = @At(value = "TAIL"), remap = false)
    private void hookViaPipeline(Channel channel, CallbackInfo ci) {
        ViaForgeCommon.getManager().inject(channel, (ExtendedNetworkManager) val$networkmanager);

        final UserConnection connection = channel.attr(ViaForgeCommon.VF_VIA_USER).get();
        if (connection != null
                && channel.pipeline().get(ViaEncodeHandler.NAME) != null
                && channel.pipeline().get(ModernSequenceEncodeHandler.NAME) == null) {
            // Outbound handlers run from tail to head. Placing this before
            // via-encoder lets it see the fully translated 1.20.6 ByteBuf.
            channel.pipeline().addBefore(
                    ViaEncodeHandler.NAME,
                    ModernSequenceEncodeHandler.NAME,
                    new ModernSequenceEncodeHandler(connection)
            );
        }
    }
    
}
