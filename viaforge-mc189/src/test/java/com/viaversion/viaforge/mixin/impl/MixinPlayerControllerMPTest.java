/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 */
package com.viaversion.viaforge.mixin.impl;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MixinPlayerControllerMPTest {

    @Test
    public void rightClickOffhandCallbackMatchesMinecraftMethodArguments() throws Exception {
        final Path sourcePath = Paths.get(
                "src/main/java/com/viaversion/viaforge/mixin/impl/MixinPlayerControllerMP.java"
        );
        final String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final JavaFileObject sourceFile = new SimpleJavaFileObject(
                URI.create("string:///MixinPlayerControllerMP.java"),
                JavaFileObject.Kind.SOURCE
        ) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        final JavacTask task = (JavacTask) compiler.getTask(
                null, null, null, Collections.singletonList("-proc:none"), null,
                Collections.singletonList(sourceFile)
        );
        final List<String> parameters = new ArrayList<>();
        for (CompilationUnitTree unit : task.parse()) {
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitMethod(MethodTree method, Void unused) {
                    if (method.getName().contentEquals("viaforge$rightClickOffhandBlock")) {
                        method.getParameters().forEach(parameter ->
                                parameters.add(parameter.getType().toString()));
                    }
                    return super.visitMethod(method, unused);
                }
            }.scan(unit, null);
        }

        assertEquals(Arrays.asList(
                "EntityPlayerSP",
                "WorldClient",
                "ItemStack",
                "BlockPos",
                "EnumFacing",
                "Vec3",
                "CallbackInfoReturnable<Boolean>"
        ), parameters);
    }
}
