package com.mediamix.shared.spider

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.*

/**
 * CMS 采集站蜘蛛适配器
 *
 * 基于站点配置 [site] 的 `api` 字段，通过 [VideoApiService] 调用标准 CMS 接口：
 * - 列表：`{api}?ac=detail&pg={page}[&t={typeId}]`
 * - 详情：`{api}?ac=detail&ids={vodId}`
 * - 搜索：`{api}?wd={keyword}`
 *
 * [VideoApiService] 内部自建 HttpClient，并带 DNS 预解析与接口预取缓存，
 * 因此不需要外部注入 httpClient。用默认参数实现，对 [SpiderRegistry] 零侵入
 * （`SpiderRegistry.buildSpider` 里 `CmsSpider(site = site)` 的调用无需改动）。
 */
class CmsSpider(
    private val site: TvBoxSite,
    private val apiService: VideoApiService = VideoApiService.shared,
) : SpiderAdapter {
    private val logger = Logger.withTag("CmsSpider")

    override val key: String get() = site.key
    override val name: String get() = site.name
    override val type: SpiderType get() = SpiderType.CMS
    override val isSearchSupported: Boolean get() = site.searchable

    override suspend fun init(config: Map<String, Any>) {
        // CMS 蜘蛛无需额外初始化：接口地址在构造时已由 site.api 提供
    }

    override suspend fun homeContent(page: Int): SpiderHomeResult =
        try {
            val categories = apiService.fetchCategories(site.api)
            val spiderCategories =
                categories.map { c ->
                    SpiderCategory(
                        typeId = c.typeId.toString(),
                        typeName = c.typeName,
                    )
                }
            val videoList = apiService.fetchVideoList(site.api, page = page)
            SpiderHomeResult(
                categories = spiderCategories,
                recommend = videoList.list,
                classList = spiderCategories.associate { it.typeId to emptyList<VideoItem>() },
            )
        } catch (e: Exception) {
            logger.e { "homeContent failed for ${site.key}: ${e.message}" }
            SpiderHomeResult()
        }

    override suspend fun categoryContent(
        tid: String,
        page: Int,
        filter: Map<String, String>?,
    ): SpiderListResult =
        try {
            val response =
                apiService.fetchVideoList(
                    apiUrl = site.api,
                    page = page,
                    typeId = tid.toIntOrNull(),
                )
            SpiderListResult(
                list = response.list,
                page = response.page,
                pageCount = response.pageCount,
                total = response.total,
            )
        } catch (e: Exception) {
            logger.e { "categoryContent($tid) failed for ${site.key}: ${e.message}" }
            SpiderListResult()
        }

    override suspend fun detailContent(id: String): SpiderDetailResult =
        try {
            SpiderDetailResult(
                detail = apiService.fetchVideoDetail(site.api, id, sourceKey = key),
            )
        } catch (e: Exception) {
            logger.e { "detailContent($id) failed for ${site.key}: ${e.message}" }
            SpiderDetailResult()
        }

    override suspend fun searchContent(
        keyword: String,
        page: Int,
    ): SpiderListResult =
        try {
            val response = apiService.searchVideos(site.api, keyword)
            SpiderListResult(
                list = response.list,
                page = response.page,
                pageCount = response.pageCount,
                total = response.total,
            )
        } catch (e: Exception) {
            logger.e { "searchContent($keyword) failed for ${site.key}: ${e.message}" }
            SpiderListResult()
        }

    /**
     * CMS 站点在详情接口里就返回了最终播放地址，`id` 即该地址；
     * `parse = "0"` 表示直连、无需二次解析。
     */
    override suspend fun playerContent(
        flag: String,
        id: String,
    ): SpiderPlayResult = SpiderPlayResult(url = id, parse = "0")

    override fun dispose() {
        // 注意：VideoApiService.clearAllCache() 是挂起函数，而 SpiderAdapter.dispose()
        // 不是，这里无法直接调用。缓存本身带 TTL（DNS 5 分钟 / 预取 10 分钟），
        // 会自然过期，故不强行清理。
    }
}
