#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Эхо-зрение: поверхность, до которой дошёл звук, видна в оттенках серого
// со светлыми линиями по границам деталей текстуры.
// vertexColor.rgb — цвет эха с учётом затенения грани, vertexColor.a — яркость эха / 2 (до 2 со вспышкой).

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float luma(vec4 c) {
    return dot(c.rgb, vec3(0.299, 0.587, 0.114)) * step(0.1, c.a);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 tc = texCoord0 * size;
    // Вдали тексели мельче пикселя — линии превратились бы в рябь, поэтому гасим их.
    // (Производные — до любых ветвлений.)
    float texelsPerPixel = max(fwidth(tc.x), fwidth(tc.y));
    float edgeFade = clamp(1.6 - texelsPerPixel * 1.4, 0.0, 1.0);

    // Линия рисуется у той границы текселя, за которой яркость заметно меняется.
    vec2 f = fract(tc);
    float w = 0.2;
    float edge = 0.0;
    // Соседние тексели читаем, только если линия тут вообще возможна: у края текселя и не вдали.
    bool nearEdge = f.x < w || f.x > 1.0 - w || f.y < w || f.y > 1.0 - w;
    if (edgeFade > 0.0 && nearEdge) {
        ivec2 p = ivec2(tc);
        // Соседи берутся в пределах своего спрайта 16×16, чтобы края блока не давали ложной сетки.
        ivec2 lo = (p / 16) * 16;
        ivec2 hi = lo + ivec2(15);
        float c = luma(texelFetch(Sampler0, p, 0));
        if (f.x < w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(-1, 0), lo, hi), 0))));
        if (f.x > 1.0 - w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(1, 0), lo, hi), 0))));
        if (f.y < w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(0, -1), lo, hi), 0))));
        if (f.y > 1.0 - w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(0, 1), lo, hi), 0))));
        edge = smoothstep(0.05, 0.22, edge) * edgeFade;
    }

    float level = vertexColor.a * 2.0;
    float lit = min(level, 1.0);
    float gray = luma(tex) * 0.55 + 0.05;
    vec3 col = vertexColor.rgb * (gray * lit * 0.8 + edge * lit * 0.45);
    // Вспышка фронта волны: поверхность на мгновение белеет.
    float flash = max(level - 1.0, 0.0);
    col += vec3(flash * (0.25 + 0.5 * edge));

    fragColor = vec4(col, 1.0) * ColorModulator;
}
