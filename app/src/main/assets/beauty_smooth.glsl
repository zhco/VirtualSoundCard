// 磨皮着色器 - 双边滤波(单pass简化版,配合高斯做高反差保留叠加)
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec2 uTexelSize;

void main() {
    vec3 center = texture2D(uTexture, vTexCoord).rgb;
    vec3 sum = vec3(0.0);
    float totalWeight = 0.0;
    float sigmaSpace = 4.0;
    float sigmaColor = 0.1;

    for (int x = -3; x <= 3; x++) {
        for (int y = -3; y <= 3; y++) {
            vec2 offset = vec2(float(x), float(y)) * uTexelSize;
            vec3 neighbor = texture2D(uTexture, vTexCoord + offset).rgb;
            float spaceDist = float(x*x + y*y) / (2.0 * sigmaSpace * sigmaSpace);
            float colorDist = dot(neighbor - center, neighbor - center) / (2.0 * sigmaColor * sigmaColor);
            float weight = exp(-spaceDist - colorDist);
            sum += neighbor * weight;
            totalWeight += weight;
        }
    }
    gl_FragColor = vec4(sum / totalWeight, 1.0);
}
