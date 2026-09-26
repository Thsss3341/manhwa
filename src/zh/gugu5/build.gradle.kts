import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gugu5"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "古古漫画"
        lang = "zh"
        baseUrl = "http://www.gugu5.cc"
    }

    deeplink {
        path("/o/..*")
    }
}
