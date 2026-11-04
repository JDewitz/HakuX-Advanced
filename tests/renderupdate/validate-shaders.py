#!/usr/bin/env python3
"""Compile actual bundled Slang stages to Vulkan SPIR-V and validate every stage.
Requires glslangValidator, spirv-val and Pillow; does not claim Android GPU testing.
"""
from pathlib import Path
import argparse
import json
import re
import subprocess
import tempfile
import sys
from PIL import Image

repo=Path(__file__).resolve().parents[2]
assets=repo/'android/app/src/main/assets/renderupdate'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--compiler',default='glslangValidator')
parser.add_argument('--validator',default='spirv-val')
args=parser.parse_args()
compiled=[]
with tempfile.TemporaryDirectory(prefix='renderupdate-spirv-') as tmp:
    for shader in sorted(assets.rglob('*.slang')):
        text=shader.read_text()
        common,rest=text.split('#pragma stage vertex',1)
        vertex,fragment=rest.split('#pragma stage fragment',1)
        common=re.sub(r'^#pragma (?:parameter|name).*$', '', common, flags=re.M)
        common=common.replace('#version 450','#version 450\n#extension GL_GOOGLE_include_directive : require',1)
        for stage,body in [('vert',vertex),('frag',fragment)]:
            source=Path(tmp)/(shader.stem+'.'+stage)
            source.write_text(common+body)
            spv=source.with_suffix(source.suffix+'.spv')
            result=subprocess.run([args.compiler,'-V','--target-env','vulkan1.1','-S',stage,
                                   '-I'+str(shader.parent),'-o',str(spv),str(source)],
                                  stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
            if result.returncode:
                print(result.stdout,file=sys.stderr)
                result.check_returncode()
            subprocess.run([args.validator,'--target-env','vulkan1.1',str(spv)],check=True)
            compiled.append(str(shader.relative_to(assets))+':'+stage)
    for preset in assets.glob('*.slangp'):
        values={m[0]:m[1].strip('"') for m in re.findall(r'^\s*([\w]+)\s*=\s*(.*?)\s*$',preset.read_text(),re.M)}
        for i in range(int(values['shaders'])):
            assert (preset.parent/values['shader'+str(i)]).is_file(),preset
        for texture in values.get('textures','').split(';'):
            if texture:
                with Image.open(preset.parent/values[texture]) as image:
                    image.load();assert image.width>0 and image.height>0
        if 'fsr1' in preset.name:
            assert values['FSR_FILMGRAIN']=='0.0', 'Grain must stay off by default'
            assert values['scale_type'+str(int(values['shaders'])-1)]=='source'
        if preset.name=='fxaa-fsr1.slangp':
            assert values['scale_type0']=='source' and values['scale_type1']=='viewport'
        if preset.name=='bicubic.slangp':
            assert values['filter_linear0']=='true'
        if preset.name.startswith('smaa'):
            assert values['alias0']=='SMAA_Input'
            assert int(values['shaders'])==(5 if 'sharpen' in preset.name else 4)
print(json.dumps({'validated_stages':len(compiled),'stages':compiled,'presets':len(list(assets.glob('*.slangp'))),'device_gpu_tested':False}))
