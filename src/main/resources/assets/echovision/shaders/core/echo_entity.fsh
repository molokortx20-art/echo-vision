#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Эхо-зрение для всего, что рисуется моделью с текстурой: мобы, броня, предметы, сундуки, рамки, рука игрока.
// Та же картинка, что у блоков — оттенки серого и линии деталей текстуры.
// ECHO_LEVEL — яркость эха (до 2 со вспышкой фронта), задаётся пайплайном: их 16 ступеней.
// vertexColor — окраска текстуры (крашеная кожа, трава и т. п.), влияет только на оттенок серого.

#ifndef ECHO_LEVEL
#define ECHO_LEVEL 1.0
#endif

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
in float shade;

out vec4 fragColor;

float luma(vec4 c) {
    return dot(c.rgb, vec3(0.299, 0.587, 0.114)) * step(0.1, c.a);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    ivec2 isize = textureSize(Sampler0, 0);
    vec2 tc = texCoord0 * vec2(isize);
    // Производные — до ветвлений; вдали линии гаснут.
    float texelsPerPixel = max(fwidth(tc.x), fwidth(tc.y));
    float edgeFade = clamp(1.6 - texelsPerPixel * 1.4, 0.0, 1.0);

    vec2 f = fract(tc);
    float w = 0.2;
    float edge = 0.0;
    // Соседние тексели читаем только у края текселя и не вдали.
    bool nearEdge = f.x < w || f.x > 1.0 - w || f.y < w || f.y > 1.0 - w;
    if (edgeFade > 0.0 && nearEdge) {
        ivec2 p = ivec2(tc);
        ivec2 hi = isize - ivec2(1);
        float c = luma(texelFetch(Sampler0, p, 0));
        if (f.x < w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(-1, 0), ivec2(0), hi), 0))));
        if (f.x > 1.0 - w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(1, 0), ivec2(0), hi), 0))));
        if (f.y < w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(0, -1), ivec2(0), hi), 0))));
        if (f.y > 1.0 - w) edge = max(edge, abs(c - luma(texelFetch(Sampler0, clamp(p + ivec2(0, 1), ivec2(0), hi), 0))));
        edge = smoothstep(0.05, 0.22, edge) * edgeFade;
    }

    float level = ECHO_LEVEL;
    float lit = min(level, 1.0);
    float gray = luma(tex * vec4(vertexColor.rgb, 1.0)) * 0.75 + 0.1;
    vec3 col = vec3(gray * shade * lit * 0.9 + edge * lit * 0.45);
    float flash = max(level - 1.0, 0.0);
    col += vec3(flash * (0.25 + 0.5 * edge));

    fragColor = vec4(col, 1.0) * ColorModulator;
}
