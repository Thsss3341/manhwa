package eu.kanade.tachiyomi.extension.zh.hipmh

import eu.kanade.tachiyomi.source.model.Filter

open class QueryFilter(name: String, private val options: List<Pair<String, String?>>, default: Int = 0) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray(), default) {
    val selected: String? get() = options[state].second
}

class SortFilter(default: Int = 0) : QueryFilter("排序", SORT_OPTIONS, default)

class CategoryFilter : QueryFilter("地区", CATEGORY_OPTIONS)

class StatusFilter : QueryFilter("状态", STATUS_OPTIONS)

const val SORT_POPULAR = 0
const val SORT_UPDATED = 2

// Values of the sort, category and status parameters of /v1/mangas.
private val SORT_OPTIONS = listOf(
    "人气" to "popular",
    "最新上架" to "latest",
    "最近更新" to "updated",
)

private val CATEGORY_OPTIONS = listOf(
    "全部" to null,
    "国漫" to "2",
    "韩漫" to "1",
    "日漫" to "3",
)

private val STATUS_OPTIONS = listOf(
    "全部" to null,
    "连载中" to "ongoing",
    "已完结" to "completed",
)
