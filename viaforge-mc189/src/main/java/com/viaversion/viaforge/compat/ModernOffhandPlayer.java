/*
 * This file is part of ViaForge.
 */
package com.viaversion.viaforge.compat;

/** Client-side animation state for the modern offhand. */
public interface ModernOffhandPlayer {

    void viaforge$swingOffhand();

    float viaforge$getOffhandSwingProgress(float partialTicks);
}
