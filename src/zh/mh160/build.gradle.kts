import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhua160"
    // Keiyoushi publishes this source as eu.kanade.tachiyomi.extension.zh.mh160 under a different
    // signing key, so use our own package name to let both be installed without a signature clash.
    pkgName = "zh.mh160manhwa"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "漫画160"
        lang = "zh"
        baseUrl = "https://www.mh160mh.com"
    }

    deeplink {
        path("/kanmanhua/..*")
    }
}
