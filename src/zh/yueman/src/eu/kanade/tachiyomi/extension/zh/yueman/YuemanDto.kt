package eu.kanade.tachiyomi.extension.zh.yueman

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterGroupDto(val data: List<ChapterDto> = emptyList())

@Serializable
class ChapterDto(
    @SerialName("chapter_name") val name: String,
    @SerialName("h") val url: String,
)
