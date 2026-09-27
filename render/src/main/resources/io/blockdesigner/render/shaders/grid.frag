#version 330 core
in vec3 vWorld;
uniform vec3 uCenter;
uniform float uExtent;
uniform int uNormal;
uniform vec3 uMinorColor;
uniform vec3 uMajorColor;
// Line 1 runs along the grid's first direction where its second coordinate is uLine1At (the ground: the X axis, z = 0;
// a wall: the ground line); line 2 runs along the second direction where the first coordinate is uLine2At.
uniform vec3 uLine1Color;
uniform vec3 uLine2Color;
uniform float uLine1At;
uniform float uLine2At;
out vec4 fragColor;

float gridLine(vec2 p, float spacing) {
    vec2 g = abs(fract(p / spacing - 0.5) - 0.5) / fwidth(p / spacing);
    return 1.0 - min(min(g.x, g.y), 1.0);
}

void main() {
    // The grid's own 2D coordinates: across, then up (walls) or along Z (the ground).
    vec2 p = uNormal == 0 ? vWorld.zy : uNormal == 2 ? vWorld.xy : vWorld.xz;
    vec2 c = uNormal == 0 ? uCenter.zy : uNormal == 2 ? uCenter.xy : uCenter.xz;
    float minor = gridLine(p, 1.0);
    float major = gridLine(p, 16.0);
    float fade = 1.0 - smoothstep(uExtent * 0.35, uExtent, length(p - c));
    vec3 col = mix(uMinorColor, uMajorColor, major);
    float a = max(minor * 0.35, major * 0.7);
    vec2 w = fwidth(p);
    float l1 = 1.0 - min(abs(p.y - uLine1At) / (w.y * 1.5), 1.0);
    float l2 = 1.0 - min(abs(p.x - uLine2At) / (w.x * 1.5), 1.0);
    col = mix(col, uLine1Color, l1);
    col = mix(col, uLine2Color, l2);
    a = max(a, max(l1, l2) * 0.9);
    fragColor = vec4(col, a * fade);
    if (fragColor.a < 0.01) discard;
}
