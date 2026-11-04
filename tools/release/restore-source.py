#!/usr/bin/env python3
"""Restore the compact source bundle into a new directory, without network access."""
from pathlib import Path
import subprocess,json,sys,hashlib,argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('destination',type=Path);parser.add_argument('--kit',type=Path,default=Path(__file__).resolve().parent)
args=parser.parse_args();kit=args.kit.resolve();assets=kit/'assets' if (kit/'assets').is_dir() else kit;meta=json.loads((assets/'RELEASE-METADATA.json').read_text());dest=args.destination.resolve()
if dest.exists():raise SystemExit('Choose a new destination; existing directories are never overwritten')
bundle=assets/'HakuX-Advanced-5.19-preview1-source.bundle'
h=hashlib.sha256()
with bundle.open('rb') as f:
 for block in iter(lambda:f.read(4*1024*1024),b''):h.update(block)
assert h.hexdigest()==meta['payloads'][bundle.name]['sha256']
subprocess.run(['git','init','-q',str(dest)],check=True)
(dest/'.git/shallow').write_text('\n'.join(meta['bundle_shallow_boundaries'])+'\n')
subprocess.run(['git','-C',str(dest),'bundle','verify',str(bundle)],check=True)
subprocess.run(['git','-C',str(dest),'bundle','unbundle',str(bundle)],check=True)
subprocess.run(['git','-C',str(dest),'update-ref','refs/tags/'+meta['tag'],meta['tag_object_sha']],check=True)
subprocess.run(['git','-C',str(dest),'checkout','--detach',meta['tag']],check=True)
actual=subprocess.check_output(['git','-C',str(dest),'rev-parse','HEAD'],text=True).strip()
assert actual==meta['source_commit']
subprocess.run(['git','-C',str(dest),'fsck','--connectivity-only'],check=True)
print('Restored exact frozen source:',actual)
