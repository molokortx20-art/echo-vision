#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Эхо-зрение: поверхность, до которой дошёл звук. Из чего складывается картинка — настраивается
// (параметры приходят в TextureMat, см. EchoLookUniform):
//  - тело поверхности: смесь текстуры в оттенках серого и ровной заливки цветом эха;
//  - линии деталей — по границам деталей текстуры;
//  - контуры углов — светящаяся кромка у тех рёбер грани, где поверхность ломается;
//  - затемнение вдали.
// vertexColor.rgb — цвет эха с учётом затенения грани, vertexColor.a — яркость эха / 2 (до 2 со вспышкой).

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 faceUV;
flat in int edgeMask;
in float vertexDistance;

out vec4 fragColor;

float luma(vec4 c) {
    return dot(c.rgb, vec3(0.299, 0.587, 0.114)) * step(0.1, c.a);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    vec3 lineColor = TextureMat[0].rgb;
    float textureAmount = TextureMat[0].a;
    float fillAmount = TextureMat[1].x;
    float edgeGlow = TextureMat[1].y;
    float detailLines = TextureMat[1].z;
    float distanceFade = TextureMat[1].w;
    float edgeWidth = TextureMat[2].x;

    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 tc = texCoord0 * size;
    // Вдали тексели мельче пикселя — линии превратились бы в рябь, поэтому гасим их.
    // (Производные — до любых ветвлений.)
    float texelsPerPixel = max(fwidth(tc.x), fwidth(tc.y));
    float edgeFade = clamp(1.6 - texelsPerPixel * 1.4, 0.0, 1.0);
    vec2 uvPerPixel = fwidth(faceUV);

    // Линия деталей рисуется у той границы текселя, за которой яркость заметно меняется.
    vec2 f = fract(tc);
    float w = 0.2;
    float detail = 0.0;
    // Соседние тексели читаем, только если линия тут вообще возможна: у края текселя и не вдали.
    bool nearEdge = f.x < w || f.x > 1.0 - w || f.y < w || f.y > 1.0 - w;
    if (detailLines > 0.0 && edgeFade > 0.0 && nearEdge) {
        ivec2 p = ivec2(tc);
        // Соседи берутся в пределах своего спрайта 16×16, чтобы края блока не давали ложной сетки.
        ivec2 lo = (p / 16) * 16;
        ivec2 hi = lo + ivec2(15);
        float c = luma(texelFetch(Sampler0, p, 0));
        if (f.x < w) detail = max(detail, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(-1, 0), lo, hi), 0))));
        if (f.x > 1.0 - w) detail = max(detail, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(1, 0), lo, hi), 0))));
        if (f.y < w) detail = max(detail, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(0, -1), lo, hi), 0))));
        if (f.y > 1.0 - w) detail = max(detail, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(0, 1), lo, hi), 0))));
        detail = smoothstep(0.05, 0.22, detail) * edgeFade;
    }

    // Контур угла: расстояние до ближайшего «настоящего» ребра грани. Не тоньше полутора пикселей вдали.
    float corner = 0.0;
    if (edgeGlow > 0.0 && edgeMask != 0) {
        float d = 4.0;
        if ((edgeMask & 1) != 0) d = min(d, faceUV.x);
        if ((edgeMask & 2) != 0) d = min(d, 1.0 - faceUV.x);
        if ((edgeMask & 4) != 0) d = min(d, faceUV.y);
        if ((edgeMask & 8) != 0) d = min(d, 1.0 - faceUV.y);
        float px = max(uvPerPixel.x, uvPerPixel.y);
        float width = max(edgeWidth, px * 1.5);
        // Чёткая кромка и мягкое свечение рядом с ней.
        corner = (1.0 - smoothstep(width * 0.6, width, d)) + 0.35 * (1.0 - smoothstep(width, width * 5.0, d));
    }

    float level = vertexColor.a * 2.0;
    float lit = min(level, 1.0);
    float gray = luma(tex) * 0.55 + 0.05;
    float body = mix(fillAmount * 0.6, gray, textureAmount);
    float lines = detail * detailLines * 0.45 + corner * edgeGlow * 0.9;
    vec3 col = vertexColor.rgb * body * lit * 0.8 + lineColor * lines * lit;
    // Вспышка фронта волны: поверхность на мгновение белеет.
    float flash = max(level - 1.0, 0.0);
    col += vec3(flash * (0.25 + 0.5 * min(lines, 1.0)));
    // Дальнее эхо тусклее — глубина читается лучше.
    col *= 1.0 - distanceFade * smoothstep(6.0, 40.0, vertexDistance);

    fragColor = vec4(col, 1.0) * ColorModulator;
}
