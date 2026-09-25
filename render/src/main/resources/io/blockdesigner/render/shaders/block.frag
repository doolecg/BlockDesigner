#version 330 core
in vec2 vUv;
in vec4 vColor;
in vec3 vNormal;
in vec3 vWorld;

uniform sampler2D uAtlas;
uniform float uAlphaCutoff;   // > 0 for cutout pass
uniform float uOpacity;       // < 1 for ghost layers
uniform vec4 uTint;           // rgb tint, a = strength (ghost / highlight)
uniform vec3 uSunDir;
uniform vec3 uEye;
uniform vec3 uFogColor;
uniform float uFogDistance;

out vec4 fragColor;

void main() {
    vec4 tex = texture(uAtlas, vUv);
    if (tex.a < uAlphaCutoff) discard;
    vec3 n = normalize(vNormal);
    // Minecraft's per-face shading, blended with a soft sun term for a modern look.
    float mcShade = abs(n.x) * 0.6 + abs(n.z) * 0.8 + (n.y > 0.0 ? 1.0 : 0.5) * abs(n.y);
    float sun = 0.62 + 0.38 * max(dot(n, normalize(uSunDir)), 0.0);
    float light = mix(mcShade, sun, 0.45);
    vec3 rgb = tex.rgb * vColor.rgb * light;
    rgb = mix(rgb, uTint.rgb, uTint.a);
    float dist = length(vWorld - uEye);
    float fog = clamp((dist - uFogDistance) / (uFogDistance * 1.5), 0.0, 0.85);
    rgb = mix(rgb, uFogColor, fog);
    fragColor = vec4(rgb, tex.a * uOpacity);
}
