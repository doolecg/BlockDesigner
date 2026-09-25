#version 330 core
layout(location = 0) in vec2 aPos;
uniform mat4 uViewProj;
uniform vec3 uCenter;
uniform float uExtent;
uniform float uHeight;
out vec3 vWorld;
void main() {
    vec3 w = vec3(uCenter.x + aPos.x * uExtent, uHeight, uCenter.z + aPos.y * uExtent);
    vWorld = w;
    gl_Position = uViewProj * vec4(w, 1.0);
}
