package com.mediamix.shared.services

import co.touchlab.kermit.Logger
import com.mediamix.shared.models.CmsApiSite
import com.mediamix.shared.models.SourceRef
import com.mediamix.shared.models.SourceUnavailableException
import com.mediamix.shared.spider.SpiderService

/**
 * 剧集标识 → 真实播放地址（含请求头）的解析器。
 *
 * **为什么单独抽出来**：`CmsApiSite` 的查找依赖 UI 层的 [SourceRepository]（自定义源
 * 只存在于用户配置里），而播放页需要在**切集/连播时按需解析** TVBox 剧集 ——
 * TVBox 的 `vod_play_url` 里存的是待解析标识，不是地址。把「反查站点 + 调网关」
 * 这段收敛到一个可被详情页与播放页共用的对象，避免两处各写一遍。
 *
 * CMS 源走的是直通分支（标识本身就是地址），因此对 CMS 剧集调用本类无副作用。
 */
class PlaybackResolver(
    private val spiderService: SpiderService,
    private val gateway: SourceContentGateway,
) {
    private val logger = Logger.withTag("PlaybackResolver")

    /**
     * 解析一集。
     *
     * [siteResolver] 由调用方注入（UI 层传 `SourceRepository::findByKey`），
     * 这样 shared 层不必反向依赖 UI 层的仓库。
     */
    suspend fun resolve(
        sourceKey: String,
        flag: String,
        episodeId: String,
        siteResolver: (String) -> CmsApiSite?,
    ): ResolvedPlay {
        if (episodeId.isBlank()) throw SourceUnavailableException("这一集没有可播放的地址。")

        val configKey = SourceRef.configKey(sourceKey)
        val site =
            siteResolver(configKey) ?: CmsApiSite.findByKey(configKey)
                ?: throw SourceUnavailableException("找不到数据源「$configKey」，请在设置 → 数据源管理里检查。")

        if (!site.isTvBox) return ResolvedPlay(url = episodeId)

        val resolved =
            try {
                gateway.resolvePlay(site = site, flag = flag, episodeId = episodeId, sourceKey = sourceKey)
            } catch (e: SourceUnavailableException) {
                // 站点解析器不在缓存里（进程被回收 / 首页没进过）时，按配置补建一次再试。
                // 用异常做信号是刻意的：不引入新的返回类型，失败的正常路径也只走一次。
                logger.i { "解析失败，补建蜘蛛后重试: ${e.message}" }
                val config = spiderService.fetchTvBoxConfig(site.apiUrl)
                spiderService.initFromConfig(config, configKey = site.key)
                gateway.resolvePlay(site = site, flag = flag, episodeId = episodeId, sourceKey = sourceKey)
            }
        logger.i { "解析播放地址成功: $configKey/${SourceRef.siteKey(sourceKey)} flag=$flag" }
        return resolved
    }
}
