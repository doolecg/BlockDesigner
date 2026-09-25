package io.blockdesigner.render.gl;

import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/** A linked GLSL program loaded from {@code /io/blockdesigner/render/shaders/<name>.vert|frag}. */
public final class Shader implements AutoCloseable {
    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public Shader(String name) {
        int vs = compile(GL_VERTEX_SHADER, source(name + ".vert"), name);
        int fs = compile(GL_FRAGMENT_SHADER, source(name + ".frag"), name);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Link failed for " + name + ": " + glGetProgramInfoLog(program));
        }
    }

    private static String source(String file) {
        try (InputStream in = Shader.class.getResourceAsStream("/io/blockdesigner/render/shaders/" + file)) {
            if (in == null) throw new IllegalStateException("Missing shader " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int compile(int type, String src, String name) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Compile failed for " + name + ": " + glGetShaderInfoLog(s));
        }
        return s;
    }

    public void use() {
        glUseProgram(program);
    }

    private int loc(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    public void set(String name, float v) {
        glUniform1f(loc(name), v);
    }

    public void set(String name, int v) {
        glUniform1i(loc(name), v);
    }

    public void set(String name, float x, float y, float z) {
        glUniform3f(loc(name), x, y, z);
    }

    public void set(String name, float x, float y, float z, float w) {
        glUniform4f(loc(name), x, y, z, w);
    }

    public void setRgb(String name, int rgb) {
        set(name, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    public void setMat4(String name, float[] m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(loc(name), false, stack.floats(m));
        }
    }

    @Override
    public void close() {
        glDeleteProgram(program);
    }
}
