# Ths Manhua

A personal [Mihon](https://mihon.app) extension repository.

| Extension | Language | Site |
|-----------|----------|------|
| 古古漫画 (`zh.gugu5`) | zh | http://www.gugu5.cc |
| 漫画大全 (`zh.yueman`) | zh | http://m.yueman1.cc |
| 嬉皮漫画 (`zh.hipmh`) | zh | https://m.hipmh.com |
| 漫画柜 (`zh.manhuaguiths`) | zh | https://www.manhuagui.com |

古古漫画 is a directory site. Depending on the manga, its chapter list links either to its own
reader or to the partner site 漫画大全 (`m.yueman1.cc`); the extension reads each chapter from
wherever it is hosted. Manga whose chapters only link to official third-party sites (e.g. 腾讯动漫
`ac.qq.com`) show no chapters and say so in their description.

漫画大全 publishes its current domains at http://reman.cc; if the site moves, change the base URL in
the extension's settings. Its own search is disabled, so the extension searches an index of the
whole catalogue instead: the [Search index workflow](.github/workflows/search_index.yml) crawls the
site's category listings daily and publishes `yueman.tsv` on the `search` branch. Pasting a 漫画大全
or 古古漫画 manga link into the search box also works. Both sites share the same catalogue, so use
漫画大全 to search for titles that 古古漫画's own search misses.

古古漫画, 漫画大全 and mh160 all serve the same re-hosted image sets, which are sometimes missing
panels or cut pages short. 嬉皮漫画 carries images taken directly from the official platforms
(快看漫画, 腾讯动漫, webtoon, ...), so use it when a chapter from the other sources is incomplete.
Its chapter lists can lag behind by a few hours because the site's API caches them; the extension
follows each chapter's "next chapter" link to find chapters the cached list doesn't show yet.

漫画柜 is Keiyoushi's ManHuaGui extension with one change: each chapter is labelled with the
section it belongs to on the site (单话, 单行本, 番外篇, ...) in the scanlator field. The label shows
under every chapter, and the chapter list's filter (scanlators) can hide whole sections. When a manga
has a 话 section, volumes and extras get no chapter number, so 第16卷 isn't taken for chapter 16 by
trackers. It keeps the same source id as Keiyoushi's build, so uninstall that one first;
library entries carry over.

## Adding the repo in Mihon

In Mihon go to **Browse → Extensions → Extension repos → Add** and paste:

```
https://raw.githubusercontent.com/Thsss3341/ths-manhua/repo/index.pb
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
./gradlew :src:zh:gugu5:assembleDebug     # build one extension
./gradlew :src:zh:gugu5:lintRelease       # lint it
```

To load only the modules you are working on, edit the bottom of
[settings.gradle.kts](settings.gradle.kts).

To add a new extension, create `src/<lang>/<name>/` with a `build.gradle.kts`, launcher icons under
`res/mipmap-*/ic_launcher.png` and a class annotated with `@Source` that extends `KeiSource`. The
[Keiyoushi contributing guide](https://github.com/keiyoushi/extensions-source/blob/main/CONTRIBUTING.md)
documents the `keiyoushi { }` build DSL and the `KeiSource` API used here.

## Credits

The build tooling (`gradle/`, `core/`, `compiler/`, `common/`), `lib/` and the 漫画柜 extension are
adapted from
[keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source), licensed under the
Apache License 2.0 (see [LICENSE-APACHE](LICENSE-APACHE)). Everything else is under the
[MIT License](LICENSE).

This project is not affiliated with Mihon or with the content providers available.
