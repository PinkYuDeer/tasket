#version 120

uniform sampler2D u_blurredTex;
uniform vec2 iResolution;
uniform vec2 u_screenUVOffset;
uniform vec2 u_screenUVScale;
uniform float u_alpha;

uniform float u_continuityIndex;
uniform vec2 u_rectSize;
uniform vec2 u_rectCenter;
uniform float u_rectEdgeSoftness;
uniform vec4 u_cornerRadiuses;

float roundedBoxSDF(vec2 p, vec2 a, float r, float n) {
    vec2 q = abs(p) - a + r;
    vec2 q_clamped = max(q, 0.0);
    float exterior_dist = pow(pow(q_clamped.x, n) + pow(q_clamped.y, n), 1.0 / n);
    float interior_dist = min(max(q.x, q.y), 0.0);
    return interior_dist + exterior_dist - r;
}

float getEffectRadius(vec2 p, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    return (p.y > 0.0) ? r.x : r.y;
}

void main() {
    vec2 uv = gl_TexCoord[0].xy;
    vec2 screenUV = vec2(
        u_screenUVOffset.x + uv.x * u_screenUVScale.x,
        u_screenUVOffset.y + (1.0 - uv.y) * u_screenUVScale.y);

    vec2 position = (uv - u_rectCenter) * iResolution.xy;
    position.x = -position.x;
    vec2 rectSize = u_rectSize * iResolution.xy;
    float minSize = min(rectSize.x, rectSize.y);
    vec4 radius4 = u_cornerRadiuses * minSize;

    vec2 halfSize = rectSize / 2.0;
    float radius = getEffectRadius(position, radius4);
    float distance = roundedBoxSDF(position, halfSize, radius, u_continuityIndex);
    float rectAlpha = 1.0 - smoothstep(0.0, u_rectEdgeSoftness, distance);

    vec4 blurred = texture2D(u_blurredTex, clamp(screenUV, 0.0, 1.0));
    gl_FragColor = vec4(blurred.rgb, blurred.a * rectAlpha * clamp(u_alpha, 0.0, 1.0));
}
