#!/usr/bin/env python3
"""Verify the release kit and optionally create its GitHub tag/draft. Never publish."""
from pathlib import Path
import subprocess,json,hashlib,argparse,sys
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--kit',type=Path,default=Path(__file__).resolve().parent,help='Extracted release-kit folder')
group=parser.add_mutually_exclusive_group();group.add_argument('--create-tag-only',action='store_true');group.add_argument('--create-draft',action='store_true')
args=parser.parse_args();kit=args.kit.resolve();assets=kit/'assets';meta=json.loads((assets/'RELEASE-METADATA.json').read_text())
for line in (assets/'SHA256SUMS').read_text().splitlines():
 expected,name=line.split('  ',1)
 if Path(name).name!=name:raise SystemExit('Unsafe checksum path')
 h=hashlib.sha256()
 with (assets/name).open('rb') as f:
  for block in iter(lambda:f.read(4*1024*1024),b''):h.update(block)
 if h.hexdigest()!=expected:raise SystemExit('Checksum mismatch: '+name)
print('Verified assets. Target:',meta['repository'],meta['tag'],meta['source_commit'])
if not args.create_tag_only and not args.create_draft:
 print('PLAN ONLY: --create-tag-only creates the annotated tag; --create-draft also uploads a draft prerelease. Nothing has been sent.');sys.exit(0)
def api(path,payload=None,optional=False):
 cmd=['gh','api',path]
 if payload is not None:cmd+=['--method','POST','--input','-']
 r=subprocess.run(cmd,input=json.dumps(payload) if payload is not None else None,text=True,capture_output=True)
 if r.returncode:
  if optional and '(HTTP 404)' in r.stderr:return None
  raise SystemExit(r.stderr or r.stdout)
 return json.loads(r.stdout)
repo=meta['repository'];remote=api('repos/'+repo)
if not remote.get('private'):raise SystemExit('Expected a private repository. Visibility is never changed by this script.')
tagref=api('repos/'+repo+'/git/ref/tags/'+meta['tag'],optional=True)
if tagref is None:
 data=json.loads((kit/'tag-api.json').read_text());obj=api('repos/'+repo+'/git/tags',data)
 if obj['object']['sha']!=meta['source_commit'] or obj['object']['type']!='commit':raise SystemExit('Unexpected annotated-tag target')
 # The release commits are immutable; do not force/repoint an existing tag.
 api('repos/'+repo+'/git/refs',dict(ref='refs/tags/'+meta['tag'],sha=obj['sha']))
 print('Created annotated tag:',meta['tag'])
else:
 obj=tagref['object'];seen=set()
 while obj['type']=='tag':
  if obj['sha'] in seen:raise SystemExit('Tag cycle')
  seen.add(obj['sha']);obj=api('repos/'+repo+'/git/tags/'+obj['sha'])['object']
 if obj['type']!='commit' or obj['sha']!=meta['source_commit']:raise SystemExit('Existing tag points elsewhere; refusing to move it')
 print('Existing tag verified against the frozen APK source.')
if args.create_tag_only:sys.exit(0)
existing=api('repos/'+repo+'/releases/tags/'+meta['tag'],optional=True)
if existing:raise SystemExit('Release already exists; inspect it instead of overwriting: '+existing['html_url'])
files=[str(p) for p in sorted(assets.iterdir()) if p.is_file()]+[str(kit/name) for name in ['INSTALL-AND-TEST.md','BUILD-FROM-SOURCE.md','restore-source.py']]
subprocess.run(['gh','release','create',meta['tag'],*files,'--repo',repo,'--verify-tag','--title',meta['title'],
 '--notes-file',str(kit/'RELEASE-NOTES.md'),'--draft','--prerelease','--latest=false'],check=True)
print('Draft created. Review it on GitHub; publishing remains a separate manual action.')
