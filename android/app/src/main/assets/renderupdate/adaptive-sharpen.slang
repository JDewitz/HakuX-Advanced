#version 450
// SPDX-License-Identifier: CC0-1.0
// HakuX-Advanced RenderUpdate: bounded, contrast-adaptive unsharp filter.
// Original implementation; this is not AMD CAS/RCAS.
#pragma parameter RU_Sharpness "RenderUpdate Sharpness" 0.25 0.0 1.0 0.05
layout(set=0,binding=0,std140) uniform UBO {
    mat4 MVP; vec4 SourceSize; vec4 OutputSize; float RU_Sharpness;
} global;
#pragma stage vertex
layout(location=0) in vec4 Position;
layout(location=1) in vec2 TexCoord;
layout(location=0) out vec2 uv;
void main() { gl_Position=global.MVP*Position; uv=TexCoord; }
#pragma stage fragment
layout(location=0) in vec2 uv;
layout(location=0) out vec4 FragColor;
layout(set=0,binding=1) uniform sampler2D Source;
void main() {
    vec4 center=texture(Source,uv);
    vec2 d=global.SourceSize.zw;
    vec3 n=texture(Source,uv+vec2(0,d.y)).rgb;
    vec3 s=texture(Source,uv-vec2(0,d.y)).rgb;
    vec3 e=texture(Source,uv+vec2(d.x,0)).rgb;
    vec3 w=texture(Source,uv-vec2(d.x,0)).rgb;
    vec3 lo=min(center.rgb,min(min(n,s),min(e,w)));
    vec3 hi=max(center.rgb,max(max(n,s),max(e,w)));
    float contrast=max(max(hi.r-lo.r,hi.g-lo.g),hi.b-lo.b);
    float strength=clamp(global.RU_Sharpness,0.0,1.0);
    float gain=2.0*strength/(1.0+4.0*contrast);
    vec3 detail=center.rgb-0.25*(n+s+e+w);
    // A small bounded overshoot permits sharpening without large bright/dark halos.
    vec3 margin=vec3(0.015*strength);
    vec3 color=clamp(center.rgb+gain*detail,max(lo-margin,vec3(0)),min(hi+margin,vec3(1)));
    FragColor=vec4(color,center.a);
}
