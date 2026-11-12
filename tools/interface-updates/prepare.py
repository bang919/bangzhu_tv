"""Build a dex-only source module and signed manifest from a tested release APK."""
import argparse
import hashlib
import json
import pathlib
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--apk', type=pathlib.Path, required=True)
parser.add_argument('--version', type=int, required=True)
parser.add_argument('--host-version', type=int, required=True)
parser.add_argument('--tag', required=True)
parser.add_argument('--java', default='java')
args = parser.parse_args()
if args.version < 1 or args.host_version < 1 or not args.tag.replace('-', '').replace('.', '').replace('_', '').isalnum():
    parser.error('invalid version or tag')
out = ROOT / 'build' / 'interface-updates'
out.mkdir(parents=True, exist_ok=True)
module = out / f'sources-v{args.version}.zip'
with zipfile.ZipFile(args.apk) as source, zipfile.ZipFile(module, 'w', zipfile.ZIP_DEFLATED) as target:
    names = sorted(name for name in source.namelist() if name.startswith('classes') and name.endswith('.dex') and '/' not in name)
    if not names:
        raise ValueError('APK does not contain dex files')
    for name in names:
        target.writestr(name, source.read(name))
digest = hashlib.file_digest(module.open('rb'), 'sha256').hexdigest()
url = f'https://raw.githubusercontent.com/bang919/bangzhu_tv/main/interface-updates/modules/{module.name}'
entries = [dict(id=source, version=args.version, minHostVersion=args.host_version,
                maxHostVersion=args.host_version, url=url, sha256=digest)
           for source in ('hongguo', 'xiaoheimi')]
manifest = out / 'manifest.json'
manifest.write_text(json.dumps(dict(schema=1, modules=entries), separators=(',', ':')), encoding='utf-8')
subprocess.run([args.java, str(ROOT / 'tools/interface-updates/SignManifest.java'),
                str(ROOT / '.tools/interface-update.p12'), str(ROOT / '.tools/interface-update-pass.txt'),
                str(manifest), str(out / 'manifest.sig')], check=True)
print(f'Module: {module}\nSHA256: {digest}\nManifest: {manifest}\nSignature: {out / "manifest.sig"}')
