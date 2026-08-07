# Offhand Interaction And Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore main-hand block activation, usable empty-main-hand offhand actions, and normal first-person offhand sizing.

**Architecture:** Keep vanilla 1.8.9 as the authoritative main-hand interaction path. Add offhand behavior only as a fallback after an unconsumed main-hand action, with a separate air-use hook for the vanilla empty-stack gap. Render through the existing 1.8 item renderer using a compact mirrored left-hand matrix.

**Tech Stack:** Java 8-compatible source, Sponge Mixin 0.7, Forge 1.8.9, JUnit 4, Gradle/Unimined.

---

### Task 1: Add Regression Coverage

**Files:**
- Modify: `viaforge-mc189/src/test/java/com/viaversion/viaforge/mixin/impl/MixinPlayerControllerMPTest.java`
- Create: `viaforge-mc189/src/test/java/com/viaversion/viaforge/mixin/impl/ModernOffhandBehaviorTest.java`

- [ ] Assert that the block callback injects at `RETURN`, the air fallback exists in `MixinMinecraft`, and the renderer contains the compact mirrored transform.
- [ ] Run `./gradlew :viaforge-mc189:test --tests '*ModernOffhandBehaviorTest' --no-daemon` and confirm the assertions fail against the current implementation.

### Task 2: Correct Interaction Ordering

**Files:**
- Modify: `viaforge-mc189/src/main/java/com/viaversion/viaforge/mixin/impl/MixinPlayerControllerMP.java`
- Modify: `viaforge-mc189/src/main/java/com/viaversion/viaforge/mixin/impl/MixinMinecraft.java`

- [ ] Move block offhand handling from `HEAD` to `RETURN` and skip it when `cir.getReturnValue()` is true.
- [ ] Add a `rightClickMouse` return hook that sends offhand air use only for an empty main hand and an air or missing hit result.
- [ ] Run the targeted tests and confirm they pass.

### Task 3: Correct First-Person Rendering

**Files:**
- Modify: `viaforge-mc189/src/main/java/com/viaversion/viaforge/mixin/impl/MixinItemRenderer.java`

- [ ] Replace the fixed rotated `0.72` transform with translation `(-0.56, -0.52, -0.72)` and mirrored scale `(-0.4, 0.4, 0.4)`.
- [ ] Run the targeted tests and confirm they pass.

### Task 4: Verify And Publish

**Files:**
- Verify: `viaforge-mc189/build/libs/viaforge-mc189-4.4.0+unknown.jar`

- [ ] Run `./gradlew :viaforge-mc189:build --no-daemon` and require exit code 0.
- [ ] Inspect the final JAR callback descriptors with `javap` and run `git diff --check`.
- [ ] Commit the scoped changes and push `main` to `origin`.
