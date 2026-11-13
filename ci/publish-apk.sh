#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
: "${ANDROID_HOME:?Android SDK is required}"
: "${SPESE_KEYSTORE:?Path to the PKCS12 release keystore is required}"
: "${SPESE_PASSWORD_FILE:?Path to the keystore password file is required}"

signer="$ANDROID_HOME/build-tools/37.0.0/apksigner"
badging="$ANDROID_HOME/build-tools/37.0.0/aapt"
apk="app/build/outputs/apk/release/app-release-unsigned.apk"
destination="${1:?Output directory is required}"
notes="${2:?Release notes file is required}"
mkdir -p "$destination"

"$signer" sign --ks "$SPESE_KEYSTORE" --ks-type PKCS12 --ks-key-alias spese \
  --ks-pass "file:$SPESE_PASSWORD_FILE" --out "$destination/spese.apk" "$apk"
"$signer" verify --verbose "$destination/spese.apk"
rm -f "$destination/spese.apk.idsig"

python3 - "$destination" "$notes" "$badging" <<'PY'
import hashlib
import json
import pathlib
import re
import subprocess
import sys

output = pathlib.Path(sys.argv[1])
notes = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8").strip()
apk = output / "spese.apk"
line = subprocess.check_output([sys.argv[3], "dump", "badging", str(apk)], text=True).splitlines()[0]
match = re.search(r"name='it\.avanzi\.spese' versionCode='(\d+)' versionName='([^']+)'", line)
if not match:
    raise SystemExit("Unexpected APK identity")
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
manifest = {
    "versionCode": int(match[1]),
    "versionName": match[2],
    "sha256": digest,
    "notes": notes,
}
(output / "version.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(output / "spese.apk.sha256").write_text(f"{digest}  spese.apk\n", encoding="ascii")
PY
