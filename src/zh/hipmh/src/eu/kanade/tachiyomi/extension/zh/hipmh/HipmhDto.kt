package eu.kanade.tachiyomi.extension.zh.hipmh

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ResponseDto<T>(val code: Int, val message: String = "", val data: T? = null)

@Serializable
class MangaListDto(
    val items: List<MangaListItemDto> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
class MangaListItemDto(
    val mid: String,
    val title: String,
    @SerialName("vertical_image_url") val cover: String? = null,
)

@Serializable
class SearchResultDto(
    val data: List<SearchItemDto> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
class SearchItemDto(
    val id: String,
    val title: String,
    @SerialName("vertical_image_url") val cover: String? = null,
)

@Serializable
class MangaDetailDto(
    val title: String,
    val description: String? = null,
    val status: String? = null,
    @SerialName("vertical_image_url") val cover: String? = null,
    val authors: List<NamedDto> = emptyList(),
    val categories: List<NamedDto> = emptyList(),
    val genres: List<NamedDto> = emptyList(),
    val tags: List<NamedDto> = emptyList(),
    @SerialName("alt_titles") val altTitles: List<String> = emptyList(),
)

@Serializable
class NamedDto(val name: String)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
class ChapterDto(
    val hid: String,
    val title: String? = null,
    @SerialName("chapter_number") val number: Float? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class ChapterDataDto(
    @SerialName("chapter_title") val title: String? = null,
    @SerialName("chapter_number") val number: Float? = null,
    @SerialName("next_hid") val nextHid: String? = null,
    val images: String,
    @SerialName("order_id") val orderId: Long? = null,
    val sid: Long? = null,
    val line: Int? = null,
)
