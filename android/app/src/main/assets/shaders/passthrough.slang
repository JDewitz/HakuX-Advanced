#version 450
// SPDX-License-Identifier: CC0-1.0 (hakuX Advanced examples)
layout(set=0,binding=0,std140) uniform UBO { mat4 MVP; vec4 SourceSize; vec4 OutputSize; float Amount; } global;
#pragma parameter Amount "Strength" 0.0 0.0 1.0 0.05
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
 vec4 color=texture(Source,uv);

 FragColor=vec4(clamp(color.rgb,0.0,1.0),1.0);
}
