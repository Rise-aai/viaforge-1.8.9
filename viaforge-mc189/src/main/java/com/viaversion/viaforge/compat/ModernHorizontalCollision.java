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

public final class ModernHorizontalCollision {

    private static final double MINOR_COLLISION_ANGLE = 0.13962633907794952D;
    private static final double MINOR_COLLISION_LENGTH_SQUARED = 9.999999747378752E-6D;

    private ModernHorizontalCollision() {
    }

    /** Matches LocalPlayer's 1.20.6 minor-horizontal-collision test. */
    public static boolean isMinorCollision(
            double sin,
            double cos,
            float strafe,
            float forward,
            double resolvedX,
            double resolvedZ
    ) {
        final double inputX = (double) strafe * cos - (double) forward * sin;
        final double inputZ = (double) forward * cos + (double) strafe * sin;
        final double inputLengthSquared = inputX * inputX + inputZ * inputZ;
        final double movementLengthSquared = resolvedX * resolvedX + resolvedZ * resolvedZ;
        if (inputLengthSquared < MINOR_COLLISION_LENGTH_SQUARED
                || movementLengthSquared < MINOR_COLLISION_LENGTH_SQUARED) {
            return false;
        }

        final double dot = inputX * resolvedX + inputZ * resolvedZ;
        final double rawCosine = dot / Math.sqrt(inputLengthSquared * movementLengthSquared);
        final double cosine = Math.max(-1.0D, Math.min(1.0D, rawCosine));
        return Math.acos(cosine) < MINOR_COLLISION_ANGLE;
    }

    public static <B> Result<B> resolve(
            B moving,
            Iterable<B> collisions,
            double requestedX,
            double requestedZ,
            BoxOperations<B> operations
    ) {
        double x = requestedX;
        double z = requestedZ;
        B resolved = moving;

        if (Math.abs(x) < Math.abs(z)) {
            for (B collision : collisions) {
                z = operations.calculateZOffset(collision, resolved, z);
            }
            resolved = operations.offset(resolved, 0.0D, z);
            for (B collision : collisions) {
                x = operations.calculateXOffset(collision, resolved, x);
            }
            resolved = operations.offset(resolved, x, 0.0D);
        } else {
            for (B collision : collisions) {
                x = operations.calculateXOffset(collision, resolved, x);
            }
            resolved = operations.offset(resolved, x, 0.0D);
            for (B collision : collisions) {
                z = operations.calculateZOffset(collision, resolved, z);
            }
            resolved = operations.offset(resolved, 0.0D, z);
        }

        return new Result<>(resolved, x, z);
    }

    public interface BoxOperations<B> {

        double calculateXOffset(B collision, B moving, double requestedX);

        double calculateZOffset(B collision, B moving, double requestedZ);

        B offset(B box, double x, double z);
    }

    public static final class Result<B> {

        private final B box;
        private final double x;
        private final double z;

        private Result(B box, double x, double z) {
            this.box = box;
            this.x = x;
            this.z = z;
        }

        public B getBox() {
            return box;
        }

        public double getX() {
            return x;
        }

        public double getZ() {
            return z;
        }
    }
}
