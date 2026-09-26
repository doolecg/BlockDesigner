#version 330 core
in vec2 vUv;
uniform sampler2D uImage;
uniform float uOpacity;
out vec4 fragColor;
void main() {
    // Outside the image is transparent, so a UV offset crops the picture instead of smearing its edge.
    if (vUv.x < 0.0 || vUv.x > 1.0 || vUv.y < 0.0 || vUv.y > 1.0) discard;
    vec4 c = texture(uImage, vUv);
    float a = c.a * uOpacity;
    if (a < 0.004) discard;
    fragColor = vec4(c.rgb, a);
}
