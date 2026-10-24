#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

out vec2 texCoord0;
out vec4 vertexColor;
out float shade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    // Простое объёмное затенение: верх светлее, бока и низ темнее.
    vec3 n = normalize(Normal);
    shade = 0.55 + 0.45 * clamp(abs(n.y) * 0.7 + max(n.z, 0.0) * 0.3, 0.0, 1.0);
}
