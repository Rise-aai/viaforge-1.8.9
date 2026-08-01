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

import com.viaversion.viaversion.api.connection.StorableObject;

public final class ModernSequenceStorage implements StorableObject {

    private int sequence;

    public synchronized int next() {
        if (sequence == Integer.MAX_VALUE) {
            sequence = 0;
        }
        return ++sequence;
    }

    public synchronized void reset() {
        sequence = 0;
    }

}
