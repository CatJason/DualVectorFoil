precision mediump float;
uniform vec4 uColor;
uniform sampler2D uTexture;
varying vec2 vTexCoord;

// 高斯模糊函数
vec4 gaussianBlur(sampler2D tex, vec2 uv, float offset, float sigma, int kSize) {
    vec4 color = vec4(0.0);
    int halfKernelSize = kSize / 2;

    // 最大核大小是 5x5，所以权重数组大小是 25
    float weight[49];
    float totalWeight = 0.0;
    int index = 0;

    // 计算高斯核权重
    for (int x = -halfKernelSize; x <= halfKernelSize; x++) {
        for (int y = -halfKernelSize; y <= halfKernelSize; y++) {
            float distance = float(x * x + y * y);
            weight[index] = exp(-distance / (2.0 * sigma * sigma)) / (2.0 * 3.14159 * sigma * sigma);
            totalWeight += weight[index];
            index++;
        }
    }

    // 归一化权重
    for (int i = 0; i < kSize * kSize; i++) {
        weight[i] /= totalWeight;
    }

    // 使用计算出的高斯核进行模糊，应用每个权重
    index = 0;
    for (int x = -halfKernelSize; x <= halfKernelSize; x++) {
        for (int y = -halfKernelSize; y <= halfKernelSize; y++) {
            vec2 offsetUV = uv + vec2(float(x), float(y)) * offset;
            color += texture2D(tex, offsetUV) * weight[index];  // 这里应用权重
            index++;
        }
    }

    return color;
}

void main() {
    float sigma = 3.0;  // 设置 sigma 来增强模糊效果
    int kernelSize = 7;  // 设置核的大小，比如 5x5 核
    float offset = 0.015;  // 偏移量，控制模糊的扩展距离

    // 应用高斯模糊，使用在 main 函数中定义的 kernelSize 和 sigma
    vec4 blurredTextureColor = gaussianBlur(uTexture, vTexCoord, offset, sigma, kernelSize);

    // 混合模糊效果与背景颜色，增加透明度来增强模糊效果
    gl_FragColor = mix(blurredTextureColor, uColor, 0.15);  // 减少背景颜色混合比例
}
