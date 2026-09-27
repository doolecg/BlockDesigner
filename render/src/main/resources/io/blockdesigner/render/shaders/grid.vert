#version 330 core
layout(location = 0) in vec2 aPos;
uniform mat4 uViewProj;
uniform vec3 uCenter;
uniform float uExtent;
// Which axis the grid faces: 1 = the ground (Y up), 0 = a wall facing X, 2 = a wall facing Z.
uniform int uNormal;
// The plane's position along that axis.
uniform float uHeight;
out vec3 vWorld;
void main() {
    vec2 q = aPos * uExtent;
    vec3 w = uNormal == 0 ? vec3(uHeight, uCenter.y + q.y, uCenter.z + q.x)
           : uNormal == 2 ? vec3(uCenter.x + q.x, uCenter.y + q.y, uHeight)
           : vec3(uCenter.x + q.x, uHeight, uCenter.z + q.y);
    vWorld = w;
    gl_Position = uViewProj * vec4(w, 1.0);
}
