"""
Builds the Mihon extension repo from freshly built extensions.

Run from the root of the checked-out `repo` branch:

    python publish-repo.py <artifacts dir> <signing key sha256>

The `repo` branch is fully regenerated on every run: every extension in this repository is rebuilt
by CI, so there is nothing to merge with the previously published index.
"""

import gzip
import html
import json
import os
import shutil
import sys
from pathlib import Path

import index_pb2
from google.protobuf import json_format

REPO_NAME = os.environ["GITHUB_REPOSITORY"]
REPO_BRANCH = "repo"
RAW_BASE_URL = f"https://raw.githubusercontent.com/{REPO_NAME}/{REPO_BRANCH}"

INDEX_NAME = "Manhwa"
INDEX_BADGE = "MHW"
WEBSITE = f"https://github.com/{REPO_NAME}"

SOURCE_DIR = Path(__file__).resolve().parents[2]
ICON_FILE = "res/mipmap-xhdpi/ic_launcher.png"
DEFAULT_ICON = SOURCE_DIR / "core/src/main" / ICON_FILE

artifacts_dir = Path(sys.argv[1])
signing_key = sys.argv[2].strip().lower()
repo_dir = Path.cwd()

if len(signing_key) != 64:
    raise ValueError(f"Expected a SHA-256 certificate fingerprint, got '{signing_key}'")

for directory in ("apk", "jar", "icon"):
    shutil.rmtree(repo_dir / directory, ignore_errors=True)
    (repo_dir / directory).mkdir()

extensions: list[index_pb2.Extension] = []

for info_file in sorted(artifacts_dir.glob("**/build/keiyoushi-source-info.json")):
    info = json.loads(info_file.read_text(encoding="utf-8"))
    package_name = info["packageName"]
    build_dir = info_file.parent

    apk = next((build_dir / "outputs/apk/release").glob("*.apk"), None)
    jar = next((build_dir / "outputs/jar/release").glob("*.jar"), None)
    if apk is None or jar is None:
        raise FileNotFoundError(f"{package_name}: release apk or jar missing under {build_dir}")

    shutil.copy2(apk, repo_dir / "apk" / apk.name)
    shutil.copy2(jar, repo_dir / "jar" / jar.name)

    module_icon = SOURCE_DIR / "src" / info["module"].replace(".", "/") / ICON_FILE
    shutil.copy2(
        module_icon if module_icon.exists() else DEFAULT_ICON,
        repo_dir / "icon" / f"{package_name}.png",
    )

    extensions.append(
        index_pb2.Extension(
            name=info["name"],
            packageName=package_name,
            resources=index_pb2.Resources(
                apkUrl=f"{RAW_BASE_URL}/apk/{apk.name}",
                jarUrl=f"{RAW_BASE_URL}/jar/{jar.name}",
                iconUrl=f"{RAW_BASE_URL}/icon/{package_name}.png",
            ),
            extensionLib=info["extensionLib"],
            versionCode=info["versionCode"],
            versionName=info["versionName"],
            contentWarning=info["contentWarning"],
            sources=[
                index_pb2.Source(
                    id=int(source["id"]),
                    name=source["name"],
                    language=source["lang"],
                    homeUrl=source["baseUrl"],
                    mirrorUrls=source.get("mirrorUrls", []),
                )
                for source in info["sources"]
            ],
        )
    )
    print(f"Published {package_name} v{info['versionName']}")

if not extensions:
    raise RuntimeError(f"No built extensions found under {artifacts_dir}")

extensions.sort(key=lambda ext: ext.packageName)

index = index_pb2.Index(
    name=INDEX_NAME,
    badgeLabel=INDEX_BADGE,
    signingKey=signing_key,
    contact=index_pb2.Contact(website=WEBSITE),
    extensionList=index_pb2.ExtensionList(extensions=extensions),
)

(repo_dir / "index.json").write_text(
    json_format.MessageToJson(index, preserving_proto_field_name=True, ensure_ascii=False) + "\n",
    encoding="utf-8",
)
(repo_dir / "index.pb").write_bytes(
    gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
)

with (repo_dir / "index.html").open("w", encoding="utf-8") as f:
    f.write('<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n')
    for ext in extensions:
        f.write(f'<a href="{html.escape(ext.resources.apkUrl)}">{html.escape(ext.name)}</a>\n')
    f.write("</pre>\n</body>\n</html>\n")
