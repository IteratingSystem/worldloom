// Worldloom：桌面GLSL精灵轮廓体投影，每顶点一次仿射变换，无纹理读取。
attribute vec3 a_position;
attribute vec2 a_texCoord;
uniform vec2 u_drawPosition;
uniform vec2 u_origin;
uniform vec2 u_size;
uniform vec2 u_scale;
uniform vec2 u_spriteFlip;
uniform float u_rotation;
uniform float u_footY;
uniform float u_shadowDepth;
uniform vec2 u_uvBottomLeft;
uniform vec2 u_uvTopRight;
uniform vec3 u_lightAxisU;
uniform vec3 u_lightAxisV;
uniform vec3 u_lightAxisDepth;
uniform vec2 u_lightUvMin;
uniform vec2 u_lightUvSize;
uniform vec2 u_lightDepthRange;
varying vec2 v_texCoords;
varying float v_lightDepth;

void main() {
    // 轮廓与纹理一起翻转，保持侧面和精灵的可见像素对齐。
    vec2 local = mix(a_position.xy, vec2(1.0) - a_position.xy, u_spriteFlip) * u_size;
    vec2 relative = (local - u_origin) * u_scale;
    float angle = radians(u_rotation);
    float cosine = cos(angle);
    float sine = sin(angle);
    vec2 rotated = vec2(
        relative.x * cosine - relative.y * sine,
        relative.x * sine + relative.y * cosine);
    vec2 spriteWorld = u_drawPosition + u_origin + rotated;
    // 保持仿射变换，避免脚点穿过精灵时前后面与轮廓侧面产生裂缝。
    // 地下部分在地面接收点之后，由原有深度比较自然排除。
    float height = spriteWorld.y - u_footY;
    vec3 volumePoint = vec3(
        spriteWorld.x,
        u_footY + mix(-u_shadowDepth * 0.5,
            u_shadowDepth * 0.5, a_position.z),
        height);
    vec2 lightUv = vec2(
        dot(volumePoint, u_lightAxisU),
        dot(volumePoint, u_lightAxisV));
    float lightDepth = dot(volumePoint, u_lightAxisDepth);
    vec2 normalizedUv = (lightUv - u_lightUvMin) / u_lightUvSize;
    v_lightDepth = clamp(
        (lightDepth - u_lightDepthRange.x)
            / (u_lightDepthRange.y - u_lightDepthRange.x),
        0.0, 1.0);
    v_texCoords = mix(u_uvBottomLeft, u_uvTopRight, a_texCoord);
    gl_Position = vec4(
        normalizedUv * 2.0 - 1.0,
        v_lightDepth * 2.0 - 1.0, 1.0);
}
