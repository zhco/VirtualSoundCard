// 美白+LUT滤镜
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform sampler2D uLutTexture;
uniform float uWhitenStrength; // 0~1

vec3 applyLut(vec3 color) {
    float size = 32.0;
    float b = color.b * (size - 1.0);
    float b0 = floor(b);
    float b1 = min(b0 + 1.0, size - 1.0);
    float fb = b - b0;
    float r = color.r * (size - 1.0);
    vec2 uv0 = vec2((b0 + r) / size, 1.0 - color.g);
    vec2 uv1 = vec2((b1 + r) / size, 1.0 - color.g);
    vec3 c0 = texture2D(uLutTexture, uv0).rgb;
    vec3 c1 = texture2D(uLutTexture, uv1).rgb;
    return mix(c0, c1, fb);
}

vec3 whiten(vec3 color, float strength) {
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    float curve = lum + lum * (1.0 - lum) * 0.4 * strength;
    vec3 white = vec3(1.0, 0.98, 0.95);
    return mix(color, white * curve, strength * 0.6);
}

void main() {
    vec3 color = texture2D(uTexture, vTexCoord).rgb;
    color = whiten(color, uWhitenStrength);
    color = applyLut(color);
    gl_FragColor = vec4(color, 1.0);
}
