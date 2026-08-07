# Offhand Interaction And Rendering Design

## Goal

Make the emulated 1.20.6 offhand usable without preventing normal main-hand block interaction, and render it at a practical first-person size.

## Interaction

The original 1.8.9 interaction runs first. If it reports that a block consumed the action, the offhand does nothing. If it reports that the action was not consumed and the selected main-hand slot is empty, ViaForge sends the corresponding 1.9 offhand packet. Empty-main-hand air clicks are handled from `Minecraft.rightClickMouse`, because vanilla 1.8.9 does not call `sendUseItem` when no main-hand stack exists.

This preserves chest, door, button, and other block activation while retaining block, entity, food, and other offhand use paths.

## Rendering

The first-person offhand uses a mirrored left-hand transform at the modern base position. The arbitrary extra rotations are removed and the compatibility scale is reduced from `0.72` to `0.4` so shields and custom models do not cover most of the viewport.

## Verification

Regression tests verify that block fallback runs at method return, empty-main-hand air use is wired into `rightClickMouse`, and the compact mirrored render transform remains present. The module must also pass a complete Gradle build and final-JAR bytecode inspection.
