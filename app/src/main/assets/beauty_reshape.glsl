// 瘦脸大眼 - 基于关键点的局部变形
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec2 uFaceCenter;      // 脸中心归一化坐标
uniform float uThinStrength;   // 瘦脸强度 0~1
uniform float uEyeStrength;    // 大眼强度 0~1
uniform vec2 uLeftEye;         // 左眼中心
uniform vec2 uRightEye;        // 右眼中心
uniform vec2 uTexelSize;

// 脸整体向中心收缩(瘦脸)
vec2 thinFace(vec2 uv) {
    float dist = distance(uv, uFaceCenter);
    float radius = 0.35;
    if (dist < radius) {
        float factor = 1.0 - (1.0 - dist / radius) * uThinStrength * 0.15;
        return uFaceCenter + (uv - uFaceCenter) * factor;
    }
    return uv;
}

// 眼部局部放大(大眼)
vec2 enlargeEye(vec2 uv, vec2 eyeCenter) {
    float dist = distance(uv, eyeCenter);
    float radius = 0.06;
    if (dist < radius) {
        float factor = 1.0 - (1.0 - dist / radius) * uEyeStrength * 0.3;
        return eyeCenter + (uv - eyeCenter) * factor;
    }
    return uv;
}

void main() {
    vec2 uv = vTexCoord;

    // 大眼(先做,因为眼部坐标也在脸内)
    uv = enlargeEye(uv, uLeftEye);
    uv = enlargeEye(uv, uRightEye);

    // 瘦脸
    uv = thinFace(uv);

    uv = clamp(uv, 0.0, 1.0);
    gl_FragColor = texture2D(uTexture, uv);
}
