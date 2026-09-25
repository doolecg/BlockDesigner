#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec4 aColor;
layout(location = 3) in vec3 aNormal;

uniform mat4 uViewProj;
uniform mat4 uModel;

out vec2 vUv;
out vec4 vColor;
out vec3 vNormal;
out vec3 vWorld;

void main() {
    vec4 world = uModel * vec4(aPos, 1.0);
    vWorld = world.xyz;
    vUv = aUv;
    vColor = aColor;
    vNormal = normalize(mat3(uModel) * aNormal);
    gl_Position = uViewProj * world;
}
