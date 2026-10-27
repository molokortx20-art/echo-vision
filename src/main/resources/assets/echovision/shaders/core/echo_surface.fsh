#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Эхо-зрение: поверхность, до которой дошёл звук. Из чего складывается картинка — настраивается
// (параметры приходят в TextureMat, см. EchoLookUniform):
//  - тело поверхности: смесь текстуры в оттенках серого и ровной заливки цветом эха;
//  - пятна — мягкие светлые и тёмные разводы, лежащие на мире;
//  - линии деталей — по границам деталей текстуры (кроме рамки у края блока, чтобы не было сетки блоков);
//  - затемнение вдали.
// vertexColor.rgb — цвет эха с учётом затенения грани, vertexColor.a — яркость эха / 2 (до 2 со вспышкой).

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 worldPos;
in float vertexDistance;

out vec4 fragColor;

float luma(vec4 c) {
    return dot(c.rgb, vec3(0.299, 0.587, 0.114)) * step(0.1, c.a);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Плавный шум 0–1 (значения в узлах решётки, квинтичная интерполяция между ними).
float smoothNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    vec3 w = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float z0 = mix(
        mix(hash31(cell), hash31(cell + vec3(1, 0, 0)), w.x),
        mix(hash31(cell + vec3(0, 1, 0)), hash31(cell + vec3(1, 1, 0)), w.x), w.y);
    float z1 = mix(
        mix(hash31(cell + vec3(0, 0, 1)), hash31(cell + vec3(1, 0, 1)), w.x),
        mix(hash31(cell + vec3(0, 1, 1)), hash31(cell + vec3(1, 1, 1)), w.x), w.y);
    return mix(z0, z1, w.z);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    vec3 lineColor = TextureMat[0].rgb;
    float textureAmount = TextureMat[0].a;
    float fillAmount = TextureMat[1].x;
    float mottle = TextureMat[1].y;
    float detailLines = TextureMat[1].z;
    float distanceFade = TextureMat[1].w;

    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 tc = texCoord0 * size;
    // Вдали тексели мельче пикселя — линии превратились бы в рябь, поэтому гасим их.
    // (Производные — до любых ветвлений.)
    float texelsPerPixel = max(fwidth(tc.x), fwidth(tc.y));
    float edgeFade = clamp(1.6 - texelsPerPixel * 1.4, 0.0, 1.0);

    // Линия деталей рисуется у той границы текселя, за которой яркость заметно меняется.
    vec2 f = fract(tc);
    float w = 0.2;
    float detail = 0.0;
    ivec2 p = ivec2(tc);
    // Тексель в спрайте 16×16. Крайний ряд — рамка у края блока (гладкий камень, плитка): по нему линии
    // не рисуем, иначе стены расчерчиваются сеткой блоков.
    ivec2 lo = (p / 16) * 16;
    ivec2 hi = lo + ivec2(15);
    ivec2 local = p - lo;
    bool frame = local.x == 0 || local.y == 0 || local.x == 15 || local.y == 15;
    // Соседние тексели читаем, только если линия тут вообще возможна: у края текселя и не вдали.
    bool nearEdge = f.x < w || f.x > 1.0 - w || f.y < w || f.y > 1.0 - w;
    if (detailLines > 0.0 && edgeFade > 0.0 && nearEdge && !frame) {
        float c = luma(texelFetch(Sampler0, p, 0));
        if (f.x < w && local.x > 1) detail = max(detail, abs(c - luma(texelFetch(Sampler0, p + ivec2(-1, 0), 0))));
        if (f.x > 1.0 - w && local.x < 14) detail = max(detail, abs(c - luma(texelFetch(Sampler0, p + ivec2(1, 0), 0))));
        if (f.y < w && local.y > 1) detail = max(detail, abs(c - luma(texelFetch(Sampler0, p + ivec2(0, -1), 0))));
        if (f.y > 1.0 - w && local.y < 14) detail = max(detail, abs(c - luma(texelFetch(Sampler0, p + ivec2(0, 1), 0))));
        detail = smoothstep(0.05, 0.22, detail) * edgeFade;
    }

    float level = vertexColor.a * 2.0;
    float lit = min(level, 1.0);
    float gray = luma(tex) * 0.55 + 0.05;
    float body = mix(fillAmount * 0.6, gray, textureAmount);
    if (mottle > 0.0) {
        // Крупные разводы и помельче поверх — как неровный отклик звука от поверхности.
        float n = smoothNoise(worldPos * 0.33) * 0.65 + smoothNoise(worldPos * 0.9 + vec3(17.0)) * 0.35;
        body *= mix(1.0, 0.2 + 1.7 * n * n, mottle);
    }
    float lines = detail * detailLines * 0.45;
    vec3 col = vertexColor.rgb * body * lit * 0.8 + lineColor * lines * lit;
    // Вспышка фронта волны: поверхность на мгновение белеет.
    float flash = max(level - 1.0, 0.0);
    col += vec3(flash * (0.25 + 0.5 * min(lines, 1.0)));
    // Дальнее эхо тусклее — глубина читается лучше.
    col *= 1.0 - distanceFade * smoothstep(6.0, 40.0, vertexDistance);

    fragColor = vec4(col, 1.0) * ColorModulator;
}
