#version 330 core
in vec2 vNdc;
uniform mat4 uInvViewProj;
uniform vec3 uZenith;
uniform vec3 uHorizon;
uniform vec3 uGround;
out vec4 fragColor;
void main() {
    vec4 far = uInvViewProj * vec4(vNdc, 1.0, 1.0);
    vec4 near = uInvViewProj * vec4(vNdc, -1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w - near.xyz / near.w);
    float h = dir.y;
    vec3 c = h > 0.0 ? mix(uHorizon, uZenith, pow(clamp(h, 0.0, 1.0), 0.6)) : mix(uHorizon, uGround, pow(clamp(-h, 0.0, 1.0), 0.4));
    // Subtle vignette.
    float v = 1.0 - 0.18 * dot(vNdc * 0.7, vNdc * 0.7);
    fragColor = vec4(c * v, 1.0);
}
