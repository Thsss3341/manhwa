package eu.kanade.tachiyomi.extension.zh.yueman

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    Filter.Select<String>(
        "分类",
        CATEGORY_OPTIONS.map { it.first }.toTypedArray(),
    ) {
    val selectedId: Int? get() = CATEGORY_OPTIONS[state].second
}

// Pair<label, id under /mmm/{id}/>. The site has no combined list, so "排行" falls back to the ranking.
private val CATEGORY_OPTIONS: List<Pair<String, Int?>> = listOf(
    "排行" to null,
    "少年热血" to 1,
    "武侠格斗" to 2,
    "科幻魔幻" to 3,
    "竞技体育" to 4,
    "爆笑喜剧" to 5,
    "侦探推理" to 6,
    "恐怖灵异" to 7,
    "耽美人生" to 8,
    "少女爱情" to 9,
    "恋爱生活" to 10,
    "生活漫画" to 11,
    "战争漫画" to 12,
    "故事漫画" to 13,
    "其他漫画" to 14,
)
