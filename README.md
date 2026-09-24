# Manhwa extensions

A personal [Mihon](https://mihon.app) extension repository.

| Extension | Language | Site |
|-----------|----------|------|
| 漫画160 (`zh.mh160`) | zh | https://www.mh160mh.com |

## Adding the repo in Mihon

In Mihon go to **Browse → Extensions → Extension repos → Add** and paste:

```
https://raw.githubusercontent.com/Thsss3341/manhwa/repo/index.pb
```

Mihon can only fetch the index if this GitHub repository is **public**.

## How publishing works

- `src/<lang>/<name>/` contains one extension module each.
- On every push to `main`, the [CI workflow](.github/workflows/build_push.yml) builds and signs all
  extensions, then regenerates the `repo` branch with `index.pb`, `index.json`, the APKs, the
  extension JARs and icons ([publish-repo.py](.github/scripts/publish-repo.py)).
- Pull requests and other branches are built (debug-signed) and linted by the
  [build check workflow](.github/workflows/build_pull_request.yml).

### One-time setup: signing key

Mihon only accepts updates signed with the same key, so create one keystore and keep it forever:

```bash
keytool -genkeypair -v -keystore signingkey.jks -alias manhwa \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w 0 signingkey.jks > signingkey.jks.b64
```

Then add these secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|--------|-------|
| `SIGNING_KEY` | contents of `signingkey.jks.b64` |
| `ALIAS` | the key alias (`manhwa` above) |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password (same as the keystore password unless you set a separate one) |

Back up `signingkey.jks` somewhere safe and never commit it (`*.jks` is git-ignored). If it is lost,
every user has to uninstall and reinstall the extensions.

Also make sure **Settings → Actions → General → Workflow permissions** allows the workflow to push
(the publish job requests `contents: write`).

## Development

Building needs JDK 21+ and the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

```bash
./gradlew :src:zh:mh160:assembleDebug     # build one extension
./gradlew :src:zh:mh160:lintRelease       # lint it
```

To load only the modules you are working on, edit the bottom of
[settings.gradle.kts](settings.gradle.kts).

To add a new extension, create `src/<lang>/<name>/` with a `build.gradle.kts` (see
[the mh160 one](src/zh/mh160/build.gradle.kts)), launcher icons under `res/mipmap-*/ic_launcher.png`
and a class annotated with `@Source` that extends `KeiSource`.

## Credits

The build tooling (`gradle/`, `core/`, `compiler/`, `common/`) and the 漫画160 source are adapted
from [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source), licensed under
the Apache License 2.0 (see [LICENSE-APACHE](LICENSE-APACHE)). Everything else is under the
[MIT License](LICENSE).

This project is not affiliated with Mihon or with the content providers available.
