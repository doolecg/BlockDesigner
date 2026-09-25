#version 330 core
in vec3 vWorld;
uniform vec3 uCenter;
uniform float uExtent;
uniform vec3 uMinorColor;
uniform vec3 uMajorColor;
uniform vec3 uAxisXColor;
uniform vec3 uAxisZColor;
out vec4 fragColor;

float gridLine(vec2 p, float spacing) {
    vec2 g = abs(fract(p / spacing - 0.5) - 0.5) / fwidth(p / spacing);
    return 1.0 - min(min(g.x, g.y), 1.0);
}

void main() {
    vec2 p = vWorld.xz;
    float minor = gridLine(p, 1.0);
    float major = gridLine(p, 16.0);
    float fade = 1.0 - smoothstep(uExtent * 0.35, uExtent, length(p - uCenter.xz));
    vec3 col = mix(uMinorColor, uMajorColor, major);
    float a = max(minor * 0.35, major * 0.7);
    // World axes through the origin.
    vec2 w = fwidth(p);
    float ax = 1.0 - min(abs(p.y) / (w.y * 1.5), 1.0);
    float az = 1.0 - min(abs(p.x) / (w.x * 1.5), 1.0);
    col = mix(col, uAxisXColor, ax);
    col = mix(col, uAxisZColor, az);
    a = max(a, max(ax, az) * 0.9);
    fragColor = vec4(col, a * fade);
    if (fragColor.a < 0.01) discard;
}
