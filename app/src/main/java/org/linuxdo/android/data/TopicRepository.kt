package org.linuxdo.android.data

import android.os.SystemClock
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import java.net.URI
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.linuxdo.android.Config
import org.linuxdo.android.net.HttpStatusException
import org.linuxdo.android.net.LoginRejectedException
import org.linuxdo.android.net.NeedsInteractiveVerification
import org.linuxdo.android.net.NetworkTimeoutException
import org.linuxdo.android.net.RiskControlException
import org.linuxdo.android.net.SecondFactorRequiredException
import org.linuxdo.android.net.Transport
import org.linuxdo.android.net.TransportResult
import org.linuxdo.android.ui.html.CookedParser

data class DiscourseTagInfo(
    val name: String,
    val text: String = name,
    val count: Int = 0,
)

data class DiscourseTagSearchResult(
    val results: List<DiscourseTagInfo> = emptyList(),
    val requiredTagName: String? = null,
    val requiredTagMinCount: Int = 1,
)

@Serializable
private data class TopicListPayload(
    val topics: List<Topic> = emptyList(),
    @SerialName("more_topics_url") val moreTopicsUrl: String? = null,
)

@Serializable
private data class TopicListResponse(
    @SerialName("topic_list") val topicList: TopicListPayload,
    /** 作者信息不在 topic 里,要按 posters[].user_id 和这个数组 join。 */
    val users: List<DiscourseUser> = emptyList(),
)

@Serializable
private data class CategoryListPayload(val categories: List<Category> = emptyList())

@Serializable
private data class CategoryListResponse(
    @SerialName("category_list") val categoryList: CategoryListPayload,
)

@Serializable
private data class PostStreamResponse(
    @SerialName("post_stream") val postStream: PostStream,
)

@Serializable
private data class SearchResults(
    @SerialName("more_full_page_results") val more: Boolean = false,
)

@Serializable
private data class SearchResponse(
    val topics: List<Topic> = emptyList(),
    val posts: List<Post> = emptyList(),
    @SerialName("grouped_search_result") val grouped: SearchResults = SearchResults(),
)

class TopicRepository(
    private val transportProvider: () -> Transport,
    private val diagnostics: Diagnostics,
    private val reading: ReadingStore? = null,
    private val sessionStore: SessionStore? = null,
    private val fallbackTransportProvider: (() -> Transport)? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val gate = Mutex()
    private var lastRequestAt = 0L

    /**
     * 阅读进度上报复用的 CSRF Token。
     * 写请求本来每次都要先 GET 一次 /session/csrf.json,统计这种可丢弃的请求不值得为此翻倍;
     * 失效(403/挑战)时清空重取即可。
     */
    private var cachedCsrfToken: String? = null

    /** 分类表全站共用且很少变,整个进程只拉一次。 */
    private var categoryCache: Map<Int, Category>? = null
    private val categoryGate = Mutex()
    private var categoryListCache: List<Category>? = null
    private var emojiCache: Map<String, String>? = null

    suspend fun emojiUrls(): Map<String, String> {
        emojiCache?.let { return it }
        val result = mutableMapOf<String, String>()
        fun visit(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach(::visit)
                is JsonObject -> {
                    val name = element.text("name").ifBlank { element.text("id") }
                    val url = element.text("url")
                    if (name.isNotBlank() && url.isNotBlank()) {
                        runCatching { URI(Config.BASE_URL + "/").resolve(url).toASCIIString() }.getOrNull()?.let { result[name] = it }
                    }
                    element.values.forEach(::visit)
                }
                else -> Unit
            }
        }
        try { visit(json.parseToJsonElement(request("/emojis.json"))) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { diagnostics.log("表情索引加载失败: ${error.message}") }
        return result.toMap().also { emojiCache = it }
    }

    suspend fun fetchFeed(
        feed: HomeFeedType,
        page: Int,
        awaitCategories: Boolean = true,
        present: Boolean = true,
        order: String? = null,
        ascending: Boolean? = null,
    ): TopicPage {
        val excludeRead = feed == HomeFeedType.Unread
        val query = buildList {
            add("page=$page")
            if (!order.isNullOrBlank()) add("order=$order")
            if (ascending != null) add("ascending=$ascending")
        }.joinToString("&")
        val path = "${feed.apiPath}?$query"
        val result = toPage(
            response = json.decodeFromString(request(path, burst = page == 0, present = present)),
            awaitCategories = awaitCategories,
            excludeReadUnread = excludeRead,
        )
        return if (feed == HomeFeedType.Latest) result.copy(
            items = result.items.filterNot { it.notificationLevel == TopicNotificationLevel.Muted },
        ) else result
    }

    suspend fun fetchLatest(page: Int, awaitCategories: Boolean = true, present: Boolean = true): TopicPage =
        fetchFeed(HomeFeedType.Latest, page, awaitCategories, present = present)

    suspend fun fetchCategoryTopics(
        slug: String,
        categoryId: Int,
        page: Int,
        present: Boolean = true,
        order: String? = null,
        ascending: Boolean? = null,
    ): TopicPage {
        val query = buildList {
            add("page=$page")
            if (!order.isNullOrBlank()) add("order=$order")
            if (ascending != null) add("ascending=$ascending")
        }.joinToString("&")
        return toPage(
            json.decodeFromString(
                request(
                    "/c/${encode(slug.ifBlank { "category" })}/$categoryId.json?$query",
                    burst = page == 0,
                    present = present,
                )
            ),
            awaitCategories = false,
        )
    }

    suspend fun fetchUserCreatedTopics(username: String, page: Int): TopicPage =
        toPage(json.decodeFromString(request("/topics/created-by/${encode(username)}.json?page=$page", burst = page == 0)), awaitCategories = false)

    suspend fun fetchUserBookmarks(username: String, page: Int): TopicPage {
        val userPath = encode(username)
        val pageQuery = if (page > 0) "&page=$page" else ""
        val root = objectRequest("/u/$userPath/bookmarks.json?q=&acting_username=$pageQuery", burst = page == 0)
        val listObj = root["user_bookmark_list"] as? JsonObject
        val bookmarks = listObj?.objects("bookmarks") ?: root.objects("bookmarks")
        val moreUrl = listObj?.text("more_bookmarks_url").orEmpty().ifBlank { root.text("more_bookmarks_url") }
        val known = categoryCache ?: categories()
        val urlTopicRegex = Regex("/t/(?:[^/]+/)?(\\d+)")

        val items = bookmarks.mapNotNull { bm ->
            val topicId = bm.number("topic_id")
                ?: bm.number("bookmarkable_id")?.takeIf { bm.text("bookmarkable_type").equals("Topic", ignoreCase = true) }
                ?: urlTopicRegex.find(bm.text("bookmarkable_url"))?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: return@mapNotNull null

            val rawTitle = bm.text("title").ifBlank { bm.text("fancy_title") }.ifBlank { bm.text("name") }
            val title = Jsoup.parseBodyFragment(rawTitle).text().ifBlank { "已收藏话题" }
            val slug = bm.text("slug").ifBlank { "topic" }
            val highest = bm.text("highest_post_number").toIntOrNull()
            val linked = bm.text("linked_post_number").toIntOrNull() ?: bm.text("post_number").toIntOrNull()
            val postsCount = bm.text("posts_count").toIntOrNull() ?: highest ?: linked ?: 1
            val replyCount = bm.text("reply_count").toIntOrNull() ?: (postsCount - 1).coerceAtLeast(0)
            val categoryId = bm.text("category_id").toIntOrNull()

            val userObj = (bm["user"] as? JsonObject) ?: (bm["post_user"] as? JsonObject)
            val author = userObj?.text("username")?.takeIf { it.isNotBlank() }
                ?: bm.text("username").ifBlank { bm.text("post_username") }.ifBlank { null }
            val avatar = userObj?.text("avatar_template")?.takeIf { it.isNotBlank() }
                ?: bm.text("avatar_template").ifBlank { bm.text("post_user_avatar_template") }

            val createdAt = bm.text("created_at")
            val bumpedAt = bm.text("bumped_at").ifBlank { bm.text("updated_at").ifBlank { createdAt } }
            val excerpt = Jsoup.parseBodyFragment(bm.text("excerpt").ifBlank { bm.text("cooked") }).text()
            val tags = (bm["tags"] as? JsonArray).orEmpty().mapNotNull { elem ->
                (elem as? JsonPrimitive)?.contentOrNull
                    ?: (elem as? JsonObject)?.text("name")?.takeIf { it.isNotBlank() }
            }

            val cachedLevel = sessionStore?.getTopicNotificationLevel(topicId)
            val notificationLevel = cachedLevel?.let { TopicNotificationLevel.fromCode(it) }

            TopicListItem(
                topic = Topic(
                    id = topicId,
                    title = title,
                    slug = slug,
                    postsCount = postsCount,
                    replyCount = replyCount,
                    createdAt = createdAt,
                    lastPostedAt = bumpedAt,
                    bumpedAt = bumpedAt,
                    categoryId = categoryId,
                    excerpt = excerpt,
                    tags = tags,
                ),
                authorUsername = author,
                avatarUrl = avatarUrlOf(avatar),
                category = categoryId?.let { known[it] },
                trustLevel = null,
                notificationLevel = notificationLevel,
            )
        }.distinctBy { it.topic.id }

        return TopicPage(
            items = items,
            hasMore = moreUrl.isNotBlank() && items.isNotEmpty(),
        )
    }

    suspend fun fetchTopic(
        id: Long,
        postNumber: Int? = null,
        burst: Boolean = false,
        trackVisit: Boolean = true,
    ): TopicDetail {
        val position = postNumber?.takeIf { it > 1 }?.let { "/$it" }.orEmpty()
        val body = request("/t/$id$position.json?track_visit=$trackVisit", burst = burst, present = true)
        return json.decodeFromString<TopicDetail>(body).also {
            CookedParser.prewarm(it.postStream.posts)
        }
    }

    /**
     * 上报阅读进度(`POST /topics/timings`),让网页端的未读蓝点消失、总结页的
     * 已读帖子与阅读时长正常增长。只有用户在设置里开启同步开关后才会被调用。
     *
     * 走全局 gate 串行节流。此为可丢弃的统计请求，遇 Cloudflare 挑战或服务异常直接返回 false，
     * 绝不抛异常打断用户正常阅读。
     */
    suspend fun recordTopicTimings(topicId: Long, topicTimeMs: Long, postNumbers: List<Int>): Boolean {
        if (topicId <= 0L) return false
        val numbers = postNumbers.filter { it > 0 }.distinct().sorted()
        if (numbers.isEmpty()) {
            diagnostics.log("阅读进度未上报: 话题 $topicId 没有可见楼层")
            return false
        }
        val topicTime = topicTimeMs.coerceIn(MIN_READ_TIME_MS, MAX_TOPIC_TIME_MS)
        val perPost = (topicTime / numbers.size).coerceIn(MIN_READ_TIME_MS, MAX_POST_TIME_MS)
        // Rails 的嵌套参数写法:timings[楼层号]=停留毫秒数。JSON body 在这个端点上不被认。
        val form = buildString {
            append("topic_id=").append(topicId)
            append("&topic_time=").append(topicTime)
            numbers.forEach { number -> append("&timings%5B").append(number).append("%5D=").append(perPost) }
        }
        val primaryTransport = transportProvider()
        val fallbackTransport = fallbackTransportProvider?.invoke()
        var currentTransport = primaryTransport
        var isFallback = false

        repeat(2) { attempt ->
            val result = try {
                val csrf = cachedCsrf() ?: run {
                    diagnostics.log("阅读进度未上报: CSRF Token 获取失败")
                    return false
                }
                gate.withLock {
                    throttle()
                    try {
                        currentTransport.write("/topics/timings", "POST", form, csrf, Transport.FORM_CONTENT_TYPE, present = true)
                    } finally { lastRequestAt = SystemClock.elapsedRealtime() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.log("阅读进度上报异常(已忽略): ${error.javaClass.simpleName} ${error.message.orEmpty().take(80)}")
                return false
            }
            diagnostics.recordStatus(currentTransport.name, result.status, "/topics/timings")
            when {
                result.status in 200..299 -> {
                    val tag = if (isFallback) "(${currentTransport.name} 兜底成功)" else "(路径 ${currentTransport.name})"
                    diagnostics.log("阅读进度已同步$tag: 话题 $topicId, ${numbers.size} 层(至 #${numbers.last()}), ${topicTime / 1000}s")
                    return true
                }
                result.challenged -> {
                    cachedCsrfToken = null
                    if (!isFallback && fallbackTransport != null && fallbackTransport.name != currentTransport.name) {
                        diagnostics.log("阅读进度上报 ${currentTransport.name} 遭遇 Cloudflare 挑战，尝试 ${fallbackTransport.name} 兜底")
                        currentTransport = fallbackTransport
                        isFallback = true
                    } else {
                        diagnostics.log("阅读进度上报被 Cloudflare 挑战拦截(已静默放弃)")
                        return false
                    }
                }
                result.status == 403 || result.status == 422 -> {
                    // 常规 HTTP 403/422(多为 CSRF 过期失效):清空缓存 token 后重试一次
                    cachedCsrfToken = null
                    val isBadCsrf = result.body.contains("BAD CSRF", ignoreCase = true)
                    val detail = if (isBadCsrf) "CSRF失效" else "HTTP ${result.status}"
                    if (attempt == 0) {
                        diagnostics.log("阅读进度上报 $detail，重取 CSRF 重试一次")
                    } else {
                        diagnostics.log("阅读进度上报失败($detail): ${result.body.take(80)}")
                        return false
                    }
                }
                else -> {
                    diagnostics.log("阅读进度上报失败: HTTP ${result.status} ${result.body.take(80)}")
                    return false
                }
            }
        }
        return false
    }

    private suspend fun obtainCsrf(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            cachedCsrfToken?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val token = objectRequest("/session/csrf.json").text("csrf").also { require(it.isNotBlank()) }
        cachedCsrfToken = token
        return token
    }

    private suspend fun cachedCsrf(): String? {
        return runCatching { obtainCsrf() }.getOrNull()
    }

    suspend fun fetchPosts(topicId: Long, ids: List<Long>): List<Post> {
        require(ids.size in 1..20)
        val query = ids.joinToString("&") { "post_ids%5B%5D=$it" }
        return json.decodeFromString<PostStreamResponse>(
            request("/t/$topicId/posts.json?$query&track_visit=false"),
        ).postStream.posts.also { CookedParser.prewarm(it) }
    }

    suspend fun categoryById(id: Int?): Category? = id?.let { categories()[it] }

    suspend fun fetchReplies(postId: Long, after: Int): List<Post> =
        run {
            val root = json.parseToJsonElement(request("/posts/$postId/replies.json?after=$after"))
            val array = when (root) {
                is JsonArray -> root
                is JsonObject -> (root["post_stream"] as? JsonObject)?.get("posts") as? JsonArray
                    ?: root["replies"] as? JsonArray
                    ?: root["posts"] as? JsonArray
                else -> null
            } ?: return@run emptyList()
            array.map { json.decodeFromJsonElement(Post.serializer(), it) }.also { CookedParser.prewarm(it) }
        }

    suspend fun toggleReaction(postId: Long, reaction: String): Post {
        require(reaction.matches(Regex("[a-zA-Z0-9_+:-]+")))
        return json.decodeFromString(mutate("/discourse-reactions/posts/$postId/custom-reactions/${encode(reaction)}/toggle.json", "PUT"))
    }

    suspend fun createBoost(postId: Long, raw: String): Boost {
        // 和界面同一套规则再校一次,避免绕过输入框的调用路径把超长内容发出去。
        require(measureBoost(raw).submittable) { "简评最多 ${BoostLength.MAX_VISIBLE} 个可见字符、${BoostLength.MAX_EMOJI} 个表情" }
        val body = JsonObject(mapOf("raw" to JsonPrimitive(raw.trim()))).toString()
        return json.decodeFromString(mutate("/discourse-boosts/posts/$postId/boosts.json", "POST", body))
    }

    suspend fun deleteBoost(boostId: Long) { mutate("/discourse-boosts/boosts/$boostId.json", "DELETE") }

    /**
     * 发表回复。replyToPostNumber 为 null 时回复整个话题,否则回复指定楼层。
     * POST /posts.json 返回的是裸 post 对象或 enqueued 状态。
     */
    suspend fun createPost(
        topicId: Long,
        raw: String,
        replyToPostNumber: Int? = null,
        draftKey: String? = null,
    ): PostResult {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "回复内容不能为空" }
        val fields = mutableMapOf<String, JsonElement>(
            "raw" to JsonPrimitive(trimmed),
            "topic_id" to JsonPrimitive(topicId),
        )
        replyToPostNumber?.takeIf { it > 0 }?.let { fields["reply_to_post_number"] = JsonPrimitive(it) }
        draftKey?.takeIf { it.isNotBlank() }?.let { fields["draft_key"] = JsonPrimitive(it) }
        val responseText = mutate("/posts.json", "POST", JsonObject(fields).toString())
        return PostResult.decode(json.parseToJsonElement(responseText), json)
    }

    /**
     * 创建新话题。
     * POST /posts.json
     */
    suspend fun createTopic(
        title: String,
        raw: String,
        categoryId: Int? = null,
        tags: List<String> = emptyList(),
        draftKey: String? = "new_topic",
    ): PostResult {
        val trimmedTitle = title.trim()
        val trimmedRaw = raw.trim()
        require(trimmedTitle.isNotBlank()) { "请输入话题标题" }
        require(trimmedRaw.isNotBlank()) { "请输入话题内容" }
        val fields = mutableMapOf<String, JsonElement>(
            "title" to JsonPrimitive(trimmedTitle),
            "raw" to JsonPrimitive(trimmedRaw),
            "archetype" to JsonPrimitive("regular"),
        )
        categoryId?.takeIf { it > 0 }?.let { fields["category"] = JsonPrimitive(it) }
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanTags.isNotEmpty()) {
            fields["tags"] = JsonArray(cleanTags.map { JsonPrimitive(it) })
        }
        draftKey?.takeIf { it.isNotBlank() }?.let { fields["draft_key"] = JsonPrimitive(it) }
        val responseText = mutate("/posts.json", "POST", JsonObject(fields).toString())
        return PostResult.decode(json.parseToJsonElement(responseText), json)
    }

    /**
     * 照搬 FluxDO (DiscourseService.searchTags) 搜索与过滤分类标签。
     * GET /tags/filter/search?q={query}&limit=8&categoryId={categoryId}&filterForInput=true
     */
    suspend fun searchTags(
        categoryId: Int?,
        query: String = "",
        selectedTags: List<String> = emptyList(),
        limit: Int = 8,
        filterForInput: Boolean = true,
    ): DiscourseTagSearchResult {
        val params = mutableListOf<String>()
        params.add("q=${encode(query)}")
        if (filterForInput) {
            params.add("filterForInput=true")
        }
        params.add("limit=$limit")
        if (categoryId != null && categoryId > 0) {
            params.add("categoryId=$categoryId")
        }
        for (tag in selectedTags) {
            if (tag.isNotBlank()) {
                params.add("selected_tags[]=${encode(tag)}")
            }
        }
        val path = "/tags/filter/search?${params.joinToString("&")}"
        return runCatching {
            val responseText = request(path)
            val root = json.parseToJsonElement(responseText) as? JsonObject
            val resultsArray = root?.get("results") as? JsonArray
            val results = resultsArray.orEmpty().mapNotNull { elem ->
                (elem as? JsonObject)?.let { obj ->
                    val name = obj.text("name")
                    val text = obj.text("text").ifBlank { name }
                    val count = obj.number("count")?.toInt() ?: 0
                    if (name.isNotBlank()) DiscourseTagInfo(name = name, text = text, count = count) else null
                }
            }
            val reqObj = root?.get("required_tag_group") as? JsonObject
            val reqName = reqObj?.text("name")
            val reqMin = reqObj?.number("min_count")?.toInt() ?: 1

            if (results.isNotEmpty()) {
                DiscourseTagSearchResult(results = results, requiredTagName = reqName, requiredTagMinCount = reqMin)
            } else {
                val cat = if (categoryId != null) categoryCache?.get(categoryId) else null
                val fallbackList = fixedTagsForCategory(cat)
                    .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
                    .map { DiscourseTagInfo(name = it, text = it, count = 0) }
                DiscourseTagSearchResult(results = fallbackList)
            }
        }.getOrElse {
            val cat = if (categoryId != null) categoryCache?.get(categoryId) else null
            val fallbackList = fixedTagsForCategory(cat)
                .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
                .map { DiscourseTagInfo(name = it, text = it, count = 0) }
            DiscourseTagSearchResult(results = fallbackList)
        }
    }

    suspend fun fetchCategoryTags(categoryId: Int?, query: String = "", limit: Int = 8): List<String> {
        return searchTags(categoryId, query, limit = limit).results.map { it.name }
    }

    /**
     * 拉取当前用户的草稿列表（与 PC 端 `/my/activity/drafts` 打通）。
     * GET /drafts.json
     */
    suspend fun fetchDrafts(burst: Boolean = false): List<DraftItem> {
        val root = objectRequest("/drafts.json?offset=0&limit=50", burst = burst)
        val draftObjs = root.objects("drafts")
        val known = categoryCache ?: categories()
        return draftObjs.mapNotNull { obj ->
            val draftKey = obj.text("draft_key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sequence = obj.text("sequence").toLongOrNull() ?: 0L
            val dataRaw = obj.text("data")
            val dataObj = runCatching {
                if (dataRaw.isNotBlank()) json.parseToJsonElement(dataRaw).jsonObject else null
            }.getOrNull()

            val raw = dataObj?.text("reply").orEmpty()
            val topicId = obj.number("topic_id") ?: dataObj?.number("topic_id")
            val action = dataObj?.text("action")?.takeIf { it.isNotBlank() }
                ?: if (topicId != null && topicId > 0L) "reply" else "createTopic"
            val title = dataObj?.text("title")?.takeIf { it.isNotBlank() }
                ?: Jsoup.parseBodyFragment(obj.text("title")).text()
            val categoryId = dataObj?.text("categoryId")?.toIntOrNull()
                ?: dataObj?.text("category_id")?.toIntOrNull()
                ?: obj.text("category_id").toIntOrNull()
            val tags = (dataObj?.get("tags") as? JsonArray).orEmpty().mapNotNull { elem ->
                (elem as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: (elem as? JsonObject)?.text("name")?.takeIf { it.isNotBlank() }
                    ?: (elem as? JsonObject)?.text("id")?.takeIf { it.isNotBlank() }
            }
            val replyToPostNumber = dataObj?.text("postId")?.toIntOrNull()?.takeIf { it > 0 }
                ?: obj.text("post_number").toIntOrNull()?.takeIf { it > 0 }
            val postId = dataObj?.number("postId")
            val excerptSource = obj.text("excerpt").ifBlank { raw }
            val excerpt = Jsoup.parseBodyFragment(excerptSource).text().trim()
            val createdAt = obj.text("created_at").ifBlank { null }
            val username = obj.text("username").ifBlank { obj.text("name") }.ifBlank { null }
            val avatarUrl = avatarUrlOf(obj.text("avatar_template"))
            val closed = (obj["closed"] as? JsonPrimitive)?.booleanOrNull == true
            val archetype = obj.text("archetype").ifBlank { dataObj?.text("archetypeId").orEmpty() }.ifBlank { "regular" }

            DraftItem(
                draftKey = draftKey,
                sequence = sequence,
                title = title,
                raw = raw,
                excerpt = excerpt,
                categoryId = categoryId,
                category = categoryId?.let { known[it] },
                tags = tags,
                action = action,
                topicId = topicId,
                replyToPostNumber = replyToPostNumber,
                postId = postId,
                createdAt = createdAt,
                username = username,
                avatarUrl = avatarUrl,
                closed = closed,
                archetype = archetype,
            )
        }
    }

    /**
     * 保存草稿至 Discourse 服务端（POST /drafts.json）。
     * 若遇到 409 Conflict（跨端编辑导致 sequence 过期），自动拉取最新 sequence 重试一次。
     */
    suspend fun saveDraft(
        draftKey: String = "new_topic",
        sequence: Long = 0L,
        title: String = "",
        raw: String,
        categoryId: Int? = null,
        tags: List<String> = emptyList(),
        action: String = "createTopic",
        topicId: Long? = null,
        replyToPostNumber: Int? = null,
    ): Long {
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val dataMap = mutableMapOf<String, JsonElement>(
            "reply" to JsonPrimitive(raw),
            "action" to JsonPrimitive(action),
            "title" to JsonPrimitive(title.trim()),
            "categoryId" to (categoryId?.takeIf { it > 0 }?.let { JsonPrimitive(it) } ?: JsonNull),
            "tags" to JsonArray(cleanTags.map { JsonPrimitive(it) }),
            "archetypeId" to JsonPrimitive("regular"),
            "metaData" to JsonNull,
            "composerTime" to JsonPrimitive(1500),
            "typingTime" to JsonPrimitive(1200),
        )
        if (topicId != null && topicId > 0L) {
            dataMap["topic_id"] = JsonPrimitive(topicId)
        }
        if (replyToPostNumber != null && replyToPostNumber > 0) {
            dataMap["postId"] = JsonPrimitive(replyToPostNumber)
        }
        val dataString = JsonObject(dataMap).toString()

        suspend fun doSave(seq: Long): Long {
            val payload = JsonObject(
                mapOf(
                    "draft_key" to JsonPrimitive(draftKey),
                    "sequence" to JsonPrimitive(seq),
                    "data" to JsonPrimitive(dataString),
                ),
            ).toString()
            val responseText = mutate("/drafts.json", "POST", payload)
            val root = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull()
            return root?.text("draft_sequence")?.toLongOrNull() ?: (seq + 1L)
        }

        return try {
            doSave(sequence)
        } catch (e: HttpStatusException) {
            if (e.status == 409) {
                val latestSeq = runCatching {
                    fetchDrafts(burst = true).firstOrNull { it.draftKey == draftKey }?.sequence
                }.getOrNull() ?: 0L
                doSave(latestSeq)
            } else {
                throw e
            }
        }
    }

    /**
     * 删除服务端草稿（DELETE /drafts/{draft_key}.json）。
     */
    suspend fun deleteDraft(draftKey: String, sequence: Long = 0L) {
        require(draftKey.isNotBlank()) { "草稿标识不能为空" }
        suspend fun doDelete(seq: Long) {
            val payload = JsonObject(
                mapOf(
                    "draft_key" to JsonPrimitive(draftKey),
                    "sequence" to JsonPrimitive(seq),
                ),
            ).toString()
            mutate(
                "/drafts/${encode(draftKey)}.json?draft_key=${encode(draftKey)}&sequence=$seq",
                "DELETE",
                payload,
            )
        }
        try {
            doDelete(sequence)
        } catch (e: HttpStatusException) {
            if (e.status == 409) {
                val latestSeq = runCatching {
                    fetchDrafts(burst = true).firstOrNull { it.draftKey == draftKey }?.sequence
                }.getOrNull() ?: return
                doDelete(latestSeq)
            } else {
                throw e
            }
        }
    }

    /** 编辑楼层。PUT /posts/{id}.json 返回的 post 被包在 `{"post":{...}}` 里,要解一层。 */
    suspend fun updatePost(postId: Long, raw: String): Post {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "回复内容不能为空" }
        val body = JsonObject(mapOf(
            "post" to JsonObject(mapOf("raw" to JsonPrimitive(trimmed))),
        )).toString()
        val response = json.parseToJsonElement(mutate("/posts/$postId.json", "PUT", body)).jsonObject
        val post = response["post"] ?: error("编辑响应缺少 post 字段")
        return json.decodeFromJsonElement(Post.serializer(), post)
    }

    suspend fun deletePost(postId: Long) { mutate("/posts/$postId.json", "DELETE") }

    /**
     * 解析指定话题的 1 楼 Post ID（供列表页长按直接添加书签或举报使用）。
     */
    suspend fun fetchTopicFirstPostId(topicId: Long): Long {
        require(topicId > 0L) { "话题不存在" }
        val detail = fetchTopic(topicId, burst = true, trackVisit = false)
        return detail.postStream.posts.firstOrNull { it.postNumber == 1 }?.id
            ?: detail.postStream.posts.firstOrNull()?.id
            ?: detail.postStream.stream.firstOrNull()
            ?: error("无法获取该话题的首楼信息")
    }

    /**
     * 为楼层添加书签。
     * POST /bookmarks.json
     * 返回新建的书签 ID。
     */
    suspend fun bookmarkPost(postId: Long): Long {
        require(postId > 0L) { "楼层不存在" }
        val body = JsonObject(mapOf(
            "bookmarkable_id" to JsonPrimitive(postId),
            "bookmarkable_type" to JsonPrimitive("Post"),
        )).toString()
        val response = json.parseToJsonElement(mutate("/bookmarks.json", "POST", body)).jsonObject
        return (response["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0L }
            ?: error("书签结果无法确认，请刷新楼层后检查收藏状态")
    }

    /**
     * 从话题列表直接为话题添加书签。
     */
    suspend fun bookmarkTopic(topicId: Long, knownFirstPostId: Long? = null): Long {
        val firstPostId = knownFirstPostId?.takeIf { it > 0L } ?: fetchTopicFirstPostId(topicId)
        return bookmarkPost(firstPostId)
    }

    /**
     * 删除书签。
     * DELETE /bookmarks/{id}.json
     */
    suspend fun unbookmarkPost(bookmarkId: Long) {
        require(bookmarkId > 0L) { "缺少书签信息，请刷新楼层后再取消收藏" }
        mutate("/bookmarks/$bookmarkId.json", "DELETE")
    }

    /**
     * 举报楼层。
     * POST /post_actions.json
     */
    suspend fun flagPost(postId: Long, reasonId: Int, message: String? = null) {
        require(postId > 0L) { "楼层不存在" }
        val reason = PostFlagReason.entries.firstOrNull { it.id == reasonId }
        require(reason != null) { "请选择有效的举报原因" }
        require(!reason.requiresMessage || !message.isNullOrBlank()) { "请补充需要版主处理的问题" }
        val fields = mutableMapOf<String, JsonElement>(
            "id" to JsonPrimitive(postId),
            "post_action_type_id" to JsonPrimitive(reasonId),
            "flag_topic" to JsonPrimitive(false),
        )
        if (reason.requiresMessage && !message.isNullOrBlank()) {
            fields["message"] = JsonPrimitive(message.trim())
        }
        mutate("/post_actions.json", "POST", JsonObject(fields).toString())
    }

    /**
     * 设置话题通知级别 (0: 免打扰, 1: 常规, 2: 跟踪, 3: 关注)。
     * POST /t/{topic_id}/notifications.json
     */
    suspend fun setTopicNotificationLevel(topicId: Long, level: Int) {
        require(topicId > 0L && level in 0..3) { "无效的话题通知级别" }
        val body = JsonObject(mapOf(
            "notification_level" to JsonPrimitive(level),
        )).toString()
        mutate("/t/$topicId/notifications.json", "POST", body)
        sessionStore?.saveTopicNotificationLevel(topicId, level)
    }

    /**
     * 上传一个文件,返回可直接写进正文 raw 的结果。
     *
     * Discourse 的发帖接口只收纯文本,附件要分两步:先 POST /uploads.json 拿 short_url
     * (形如 `upload://xxx.png`),再把 Markdown 短码拼进 raw。
     * `synchronous=true` 让服务端同步处理完再返回,省掉轮询。
     */
    suspend fun uploadFile(fileName: String, mimeType: String, bytes: ByteArray): UploadedFile {
        UploadLimits.rejectReason(fileName, bytes.size.toLong())?.let { throw LoginRejectedException(it) }
        var revalidated = false
        var csrfRefreshed = false
        while (true) {
            val csrf = obtainCsrf(forceRefresh = csrfRefreshed)
            val result = gate.withLock {
                throttle(burst = true)
                val transport = transportProvider()
                try {
                    transport.upload(
                        "/uploads.json", csrf, fileName, mimeType, bytes,
                        mapOf("type" to "composer", "synchronous" to "true"),
                    )
                } finally { lastRequestAt = SystemClock.elapsedRealtime() }
            }
            diagnostics.recordStatus(transportProvider().name, result.status, "/uploads")
            if (result.challenged) {
                if (!revalidated && transportProvider().revalidate("/uploads.json")) {
                    revalidated = true
                    csrfRefreshed = true
                    cachedCsrfToken = null
                    continue
                }
                throw NeedsInteractiveVerification("上传需要重新验证")
            }
            if (result.status == 0) {
                // 路径 B 的"不支持上传"也走这里,它把原因放在 body 里。
                throw result.body.takeIf { it.isNotBlank() && !it.startsWith("java.") }
                    ?.let { LoginRejectedException(it) } ?: NetworkTimeoutException()
            }
            if ((result.status == 403 || result.status == 422) && !csrfRefreshed &&
                (result.body.contains("BAD CSRF", ignoreCase = true) || result.body.contains("csrf", ignoreCase = true))
            ) {
                cachedCsrfToken = null
                csrfRefreshed = true
                continue
            }
            val payload = runCatching { json.parseToJsonElement(result.body).jsonObject }.getOrNull()
            if (result.status !in 200..299) {
                // 站方的拒绝原因(类型不允许/太大)在 errors 数组里,原样转给用户最有用。
                val serverMessage = (payload?.get("errors") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString("\n")?.take(400)
                throw LoginRejectedException(
                    serverMessage?.takeIf { it.isNotBlank() } ?: "上传失败，请稍后重试",
                )
            }
            val root = payload ?: throw LoginRejectedException("上传响应格式不正确")
            val shortUrl = root.text("short_url").ifBlank { root.text("url") }
            if (shortUrl.isBlank()) throw LoginRejectedException("上传响应缺少文件地址")
            return UploadedFile(
                shortUrl = shortUrl,
                fileName = root.text("original_filename").ifBlank { fileName },
                width = root.text("width").toIntOrNull() ?: 0,
                height = root.text("height").toIntOrNull() ?: 0,
            )
        }
    }

    /** 取楼层原文。post_stream 下发的楼层只有 cooked,编辑前必须单独取一次 raw。 */
    suspend fun fetchPostRaw(postId: Long): String =
        objectRequest("/posts/$postId.json").text("raw")

    suspend fun fetchUnreadSummary(scope: NotificationScope): UnreadSummary {
        // Discourse 的 /notifications.json 不支持 filter=unread 参数；不带 recent=true 时会返回账号
        // 自注册以来的全量历史分页，导致 while 循环把多年前未逐条点击的陈年记录累加为 99+ 且耗时十余秒。
        // 统一使用 recent=true&limit=50&silent=true，与网页端顶栏铃铛及后台 NotificationWorker 严格同源。
        val page = json.decodeFromString<UnreadNotificationPage>(
            request("/notifications.json?recent=true&limit=50&silent=true", present = false),
        )
        val fresh = page.notifications.distinctBy { it.id }
        val count = fresh.count { it.countsAsUnread(scope) }
        val unreadByType = mutableMapOf<Int, Int>()
        fresh.filter { !it.read }.forEach { unreadByType[it.type] = (unreadByType[it.type] ?: 0) + 1 }
        // 按分类汇总。NotificationFilter.All.includes() 对所有类型都成立,所以 All 项
        // 拿到的是"全部类型的未读总数" —— 比铃铛口径的 count 更适合判断全部分类有没有变化。
        val byFilter = NotificationFilter.entries.associateWith { filter ->
            unreadByType.entries.filter { filter.includes(it.key) }.sumOf { it.value }
        }
        return UnreadSummary(
            count = count,
            unreadFilters = byFilter.filterValues { it > 0 }.keys,
            unreadByFilter = byFilter,
        )
    }

    suspend fun unreadCount(scope: NotificationScope): Int = fetchUnreadSummary(scope).count


    suspend fun markNotificationRead(id: Long) {
        mutate("/notifications/mark-read.json", "PUT", JsonObject(mapOf("id" to JsonPrimitive(id))).toString())
    }

    suspend fun setSolution(postId: Long, accepted: Boolean) {
        mutate("/solution/${if (accepted) "accept" else "unaccept"}.json", "POST",
            JsonObject(mapOf("id" to JsonPrimitive(postId))).toString())
    }

    /**
     * 发写请求。
     *
     * 被 CF 挑战时先静默重验证再重发一次,而不是直接把用户丢进全屏验证页 ——
     * 看了半小时帖子点个赞刚好撞上 cf_clearance 过期,是很常见的。
     *
     * 重放是安全的:CF 的挑战是在边缘拦下的(cf-mitigated: challenge),源站压根没收到这个请求,
     * 所以不存在"已经执行了又执行一次"。但**必须连 CSRF 一起重取** —— 重验证会在 WebView 里
     * 重新加载首页,Discourse 可能借机换掉 _forum_session,旧 token 就失效了。
     * 只重试一次,再挑战就交给用户出面。
     */
    private suspend fun mutate(path: String, method: String, body: String? = null): String {
        var revalidated = false
        var csrfRefreshed = false
        while (true) {
            val csrf = obtainCsrf(forceRefresh = csrfRefreshed)
            val result = gate.withLock {
                throttle(burst = true)
                val transport = transportProvider()
                try { transport.write(path, method, body, csrf) } finally { lastRequestAt = SystemClock.elapsedRealtime() }
            }
            diagnostics.recordStatus(transportProvider().name, result.status, path)
            if (result.challenged) {
                // clearChallenge 最长要等 30 秒,绝不能占着 gate 等 —— 那会把所有请求堵死。
                if (!revalidated && transportProvider().revalidate(path)) {
                    revalidated = true
                    csrfRefreshed = true
                    cachedCsrfToken = null
                    continue
                }
                throw NeedsInteractiveVerification("提交需要重新验证")
            }
            if (result.status == 0) throw NetworkTimeoutException()
            if ((result.status == 403 || result.status == 422) && !csrfRefreshed &&
                (result.body.contains("BAD CSRF", ignoreCase = true) || result.body.contains("csrf", ignoreCase = true))
            ) {
                cachedCsrfToken = null
                csrfRefreshed = true
                continue
            }
            if (result.status !in 200..299) {
                val serverMessage = runCatching {
                    (json.parseToJsonElement(result.body).jsonObject["errors"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString("\n")?.take(400)
                }.getOrNull()
                val message = if (result.status >= 500) "结果未确认，请刷新楼层后检查提交状态"
                    else serverMessage?.takeIf { it.isNotBlank() } ?: "操作未提交，请稍后重试"
                throw HttpStatusException(result.status, message)
            }
            return result.body
        }
    }

    suspend fun search(query: String, page: Int, order: SearchOrder = SearchOrder.Relevance): SearchPage {
        val orderedQuery = if (order.key.isEmpty()) query else
            query.replace(Regex("(?:^|\\s)order:\\S+"), " ").trim() + " order:${order.key}"
        val response = json.decodeFromString<SearchResponse>(
            request("/search.json?q=${encode(orderedQuery)}&page=$page"),
        )
        val known = categories()
        val posts = response.posts.groupBy { it.topicId }
        val seen = reading?.seenTopicIds().orEmpty()
        return SearchPage(
            items = response.topics.map { topic ->
                val author = posts[topic.id]?.firstOrNull()
                val cachedLevel = sessionStore?.getTopicNotificationLevel(topic.id)
                val effectiveLevelCode = cachedLevel ?: topic.notificationLevel?.takeIf { it in 0..3 && it != 1 }
                val notificationLevel = effectiveLevelCode?.let { TopicNotificationLevel.fromCode(it) }
                TopicListItem(
                    topic = if (topic.unseen && topic.id in seen) topic.copy(unseen = false) else topic,
                    authorUsername = author?.username,
                    avatarUrl = avatarUrlOf(author?.avatarTemplate),
                    category = known[topic.categoryId],
                    notificationLevel = notificationLevel,
                )
            },
            excerpts = posts.mapValues { (_, matches) -> matches.first().blurb },
            hasMore = response.grouped.more && response.topics.isNotEmpty(),
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    suspend fun fetchProfile(
        username: String,
        section: ProfileSection,
        cursor: Int,
        notificationFilter: NotificationFilter = NotificationFilter.All,
        excludeIds: Set<String> = emptySet(),
    ): ProfilePage {
        val userPath = encode(username)
        return when (section) {
            ProfileSection.Summary -> {
                val root = objectRequest("/u/$userPath/summary.json")
                val summary = root["user_summary"] as? JsonObject ?: JsonObject(emptyMap())
                val metrics = listOf("days_visited" to "访问天数", "time_read" to "阅读时长",
                    "topics_entered" to "浏览话题", "posts_read_count" to "已读帖子",
                    "likes_given" to "送出赞", "likes_received" to "收到赞",
                    "topic_count" to "创建话题", "post_count" to "回复数量",
                    "bookmark_count" to "我的收藏")
                ProfilePage(fields = metrics.map { (key, label) ->
                    val value = summary.text(key)
                    label to if (key == "time_read" && value.toLongOrNull() != null) "${value.toLong() / 60} 分钟" else value.ifBlank { "—" }
                })
            }
            ProfileSection.Account -> {
                val user = objectRequest("/u/$userPath.json")["user"] as? JsonObject ?: error("账号信息格式不正确")
                // 固定只有这两个请求,第二个走突发间隔。并发没有意义 —— gate 是全局串行锁,
                // async 出去也只会卡在同一把锁上,真正的开销是锁后的节流等待。
                val emails = objectRequest("/u/$userPath/emails.json", burst = true)
                ProfilePage(fields = listOf(
                    "用户名" to user.text("username").ifBlank { username },
                    "显示名称" to user.text("name").ifBlank { "未设置" },
                    "信任等级" to user.text("trust_level").let { if (it.isBlank()) "—" else "Lv$it" },
                    "注册日期" to user.text("created_at").take(10).ifBlank { "—" },
                    "个人简介" to Jsoup.parseBodyFragment(user.text("bio_raw").ifBlank { user.text("bio_cooked") }).text().ifBlank { "未设置" },
                    "主邮箱" to emails.text("email").ifBlank { user.text("email") }.ifBlank { "服务端未返回" },
                    "备用邮箱" to (emails["secondary_emails"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n").ifBlank { "未设置" },
                ))
            }
            ProfileSection.Activity -> {
                val actions = objectRequest("/user_actions.json?username=$userPath&offset=${cursor * 30}&limit=30").objects("user_actions")
                ProfilePage(entries = actions.mapIndexed { index, action ->
                    ProfileEntry("${cursor * 30 + index}:${action.text("post_id")}",
                        action.text("title").ifBlank { "社区活动" },
                        listOf(activityLabel(action.text("action_type")), Jsoup.parseBodyFragment(action.text("excerpt")).text(),
                            action.text("created_at").take(10)).filter { it.isNotBlank() }.joinToString(" · "),
                        action.number("topic_id"),
                        postNumber = action.text("post_number").toIntOrNull()?.takeIf { it > 0 })
                }, hasMore = actions.size >= 30, nextCursor = cursor + 1)
            }
            ProfileSection.Notifications -> fetchNotifications(cursor, notificationFilter, excludeIds)
            ProfileSection.Messages -> {
                val result = toPage(json.decodeFromString(request("/topics/private-messages/$userPath.json?page=$cursor")))
                ProfilePage(entries = result.items.map { item -> ProfileEntry(item.topic.id.toString(), item.topic.title,
                    "${item.topic.replyCount} 回复 · ${item.topic.lastPostedAt?.take(10).orEmpty()}", item.topic.id) },
                    hasMore = result.hasMore, nextCursor = cursor + 1)
            }
        }
    }

    /**
     * 按类型取通知。
     *
     * 首屏(offset == 0)对齐 PC 端右上角通知菜单，优先请求 `recent=true&silent=true`：
     * Discourse 后端仅在 `recent=true` 时走 `Notification.prioritized_list`，将未读的高优通知与所有非点赞类未读通知
     * （包括关注的人发帖 801、关注的人回复 802、新增关注 800、徽章 12、回复/提及等）置顶排列，且仅在此时生效 `filter_by_types`；
     * 若不带 `recent=true`，服务端按纯 `created_at DESC` 排序且忽略 `filter_by_types`，早期未读的关注提醒会被近期大量点赞淹没。
     * 继续向下翻页(offset > 0)时切回 `offset` 全量历史流，并用 `seenIds` 自动去重已展示条目。
     */
    private suspend fun fetchNotifications(
        offset: Int,
        filter: NotificationFilter,
        excludeIds: Set<String> = emptySet(),
    ): ProfilePage {
        val typeParam = filter.serverTypes.takeIf { it.isNotBlank() }
            ?.let { "&filter_by_types=${encode(it)}" }.orEmpty()
        val seenIds = excludeIds.toMutableSet()
        val collected = mutableListOf<ProfileEntry>()
        if (offset == 0) {
            val recentPage = try {
                objectRequest(
                    "/notifications.json?recent=true&limit=$NOTIFICATION_SCAN_LIMIT&silent=true$typeParam",
                    burst = true,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: NeedsInteractiveVerification) {
                throw error
            } catch (error: Exception) {
                diagnostics.log("优先通知列表加载回退: ${error.message}")
                null
            }
            if (recentPage != null) {
                val recentNotices = recentPage.objects("notifications")
                collected += recentNotices.map(::toNotificationEntry)
                    .filter { filter.includes(it.notificationType) && seenIds.add(it.id) }
                if (collected.size >= PAGE_TARGET || recentNotices.size < NOTIFICATION_SCAN_LIMIT) {
                    return ProfilePage(
                        entries = collected,
                        hasMore = recentNotices.size >= PAGE_TARGET,
                        nextCursor = 1,
                    )
                }
            }
        }
        var scanned = if (offset <= 1) 0 else offset
        var hasMore = true
        var rounds = 0
        while (hasMore && collected.size < PAGE_TARGET && rounds < MAX_SCAN_ROUNDS) {
            rounds++
            val page = objectRequest(
                "/notifications.json?offset=$scanned&limit=$NOTIFICATION_SCAN_LIMIT$typeParam",
                // 第 2 轮起走突发间隔。低频分类(私信)可能要连扫几轮才凑够一页,
                // 每轮都等满 1 秒的话用户要干等 5 秒;轮数有上限,峰值是封顶的。
                burst = rounds > 1 || offset == 0,
            )
            val notices = page.objects("notifications")
            // 游标按请求的 limit 走,不按返回条数 —— 服务端是先分页、再剔除不可访问的话题,
            // 按返回条数累加会让游标落后、下一页重复拉同一批。
            scanned += NOTIFICATION_SCAN_LIMIT
            val total = page.number("total_rows_notifications")
            // 同理,"这一页不满"不能当作已到末尾,有总数就按总数判断。
            hasMore = total?.let { scanned < it } ?: (notices.size >= NOTIFICATION_SCAN_LIMIT)
            collected += notices.map(::toNotificationEntry)
                .filter { filter.includes(it.notificationType) && seenIds.add(it.id) }
        }
        return ProfilePage(entries = collected, hasMore = hasMore, nextCursor = scanned)
    }

    private fun toNotificationEntry(notice: JsonObject): ProfileEntry {
        val data = notice["data"] as? JsonObject ?: JsonObject(emptyMap())
        val rawType = notice.text("notification_type").toIntOrNull() ?: 0
        val message = data.text("message")
        val resolvedType = when {
            rawType == 14 && message.contains("assigned", ignoreCase = true) -> 34
            else -> rawType
        }
        val resolvedPostNumber = notice.text("post_number").toIntOrNull()?.takeIf { it > 0 }
            ?: data.text("original_post_number").toIntOrNull()?.takeIf { it > 0 }
            ?: data.text("post_number").toIntOrNull()?.takeIf { it > 0 }
        val badgeName = data.text("badge_name")
        val topicTitle = notice.text("fancy_title").ifBlank { data.text("fancy_title") }.ifBlank { data.text("topic_title") }
        val rawTitle = when {
            resolvedType == 12 && badgeName.isNotBlank() -> "获得了 '$badgeName'"
            resolvedType == 800 && topicTitle.isBlank() -> "已开始关注您。"
            else -> topicTitle
        }
        val baseActor = data.text("display_username")
            .ifBlank { data.text("original_username") }
            .ifBlank { data.text("username") }
            .ifBlank { notice.text("acting_user_name") }
            .ifBlank { null }
        val count = data.text("count").toIntOrNull() ?: 0
        val resolvedActor = if (baseActor != null && count > 1) {
            "$baseActor 和其他 ${count - 1} 人"
        } else {
            baseActor
        }
        val label = notificationLabel(resolvedType.toString())
        return ProfileEntry(
            id = notice.text("id"),
            title = Jsoup.parseBodyFragment(rawTitle).text().ifBlank { label },
            subtitle = listOf(label, resolvedActor.orEmpty(), notice.text("created_at").take(10))
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            topicId = notice.number("topic_id") ?: data.number("topic_id"),
            unread = notice.text("read") == "false",
            notificationType = resolvedType,
            avatarUrl = avatarUrlOf(
                notice.text("acting_user_avatar_template").ifBlank { data.text("avatar_template") },
            ),
            actor = resolvedActor,
            postNumber = resolvedPostNumber,
        )
    }

    private suspend fun objectRequest(path: String, burst: Boolean = false): JsonObject =
        json.parseToJsonElement(request(path, burst)).jsonObject
    private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
    private fun JsonObject.objects(key: String): List<JsonObject> = (get(key) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun activityLabel(type: String): String = when (type) {
        "1" -> "点赞"; "2" -> "收到赞"; "4" -> "发表话题"; "5" -> "回复"; "6" -> "收到回复"
        "7" -> "被提及"; "9" -> "引用"; "11", "12" -> "消息"; else -> "活动"
    }

    private fun notificationLabel(type: String): String = when (type) {
        "1", "15" -> "提及"; "2" -> "回复"; "3" -> "引用"; "4" -> "编辑"; "5", "19" -> "收到赞"
        "6", "7", "16" -> "私信"; "8" -> "接受邀请"; "9", "17" -> "发布话题"; "10" -> "移动话题"
        "11", "38" -> "链接"; "12" -> "获赠徽章"; "13" -> "话题邀请"; "14" -> "解决方案"
        "25" -> "表情回应"; "27" -> "活动提醒"; "28" -> "活动邀请"; "29", "32" -> "聊天提及"
        "30" -> "聊天消息"; "31" -> "聊天邀请"; "33" -> "聊天引用"; "34" -> "指派"; "43" -> "简评"
        "800" -> "关注"; "801" -> "关注的人发布话题"; "802" -> "关注的人回复"; else -> "社区通知"
    }

    /** 冷启动从本地持久化 JSON 预热分类缓存（0ms 内存就绪，避免首屏阻塞等待网络）。 */
    fun seedCategoriesFromJson(rawJson: String) {
        if (rawJson.isBlank()) return
        runCatching {
            val list = json.decodeFromString<CategoryListResponse>(rawJson).categoryList.categories
            if (list.isNotEmpty()) {
                categoryListCache = list
                categoryCache = flattenCategories(list).associateBy { it.id }
            }
        }
    }

    fun cachedCategoryList(): List<Category>? = categoryListCache

    fun cachedCategoryMap(): Map<Int, Category> = categoryCache.orEmpty()

    /**
     * 向服务端探测最新分类树（Stale-While-Revalidate）。
     * 若与本地缓存不一致（新增/改名/改色/子分类调整），更新内存缓存并返回最新数据与原始 JSON 供持久化落盘；
     * 若完全一致则返回 null，避免无意义的界面重组。
     */
    suspend fun refreshCategoriesFromServer(): Pair<List<Category>, String>? = categoryGate.withLock {
        val raw = request("/categories.json?include_subcategories=true")
        val freshList = json.decodeFromString<CategoryListResponse>(raw).categoryList.categories
        val freshMap = flattenCategories(freshList).associateBy { it.id }
        val changed = categoryListCache != freshList || categoryCache != freshMap
        categoryListCache = freshList
        categoryCache = freshMap
        if (changed) freshList to raw else null
    }

    suspend fun fetchCategories(): List<Category> = categoryGate.withLock {
        categoryListCache ?: json.decodeFromString<CategoryListResponse>(
            request("/categories.json?include_subcategories=true"),
        ).categoryList.categories.also { categoryListCache = it }
    }

    /** 返回当前登录用户名;未登录(Discourse 返回 404)时为 null。这才是"登录态是否有效"的真实判断。 */
    suspend fun fetchCurrentUser(): String? {
        val body = try {
            request("/session/current.json")
        } catch (error: HttpStatusException) {
            if (error.status == 404) return null
            throw error
        }
        return json.parseToJsonElement(body).jsonObject["current_user"]
            ?.jsonObject?.get("username")?.jsonPrimitive?.content
    }

    /**
     * 从服务端拉取自定义侧边栏分区（与 PC 端打通）。
     * 若用户在 PC 端配置了自定义侧边栏分区，拉取并将其中的 FontAwesome 图标还原为移动端高颜值 Emoji。
     */
    suspend fun fetchCustomSidebarSection(): CustomSection? {
        val raw = try {
            request("/sidebar_sections.json")
        } catch (error: Exception) {
            diagnostics.log("拉取自定义侧边栏分区失败: ${error.message}")
            return null
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val sections = root.objects("sidebar_sections")
        // 查找属于用户的自定义版块：section_type != "community"，且 public != true
        val customSectionObj = sections.firstOrNull { sec ->
            val type = sec.text("section_type")
            val isPublic = (sec.get("public") as? JsonPrimitive)?.booleanOrNull == true
            type != "community" && !isPublic
        } ?: return null

        val serverId = customSectionObj.number("id")
        val title = customSectionObj.text("title").ifBlank { "自定义版块" }
        val linkObjs = customSectionObj.objects("links")
        val links = linkObjs.map { linkObj ->
            val linkServerId = linkObj.number("id")
            CustomSectionLink(
                id = linkServerId?.toString() ?: java.util.UUID.randomUUID().toString(),
                icon = CustomSectionIconMapper.toDisplayIcon(linkObj.text("icon")),
                name = linkObj.text("name"),
                url = linkObj.text("value"),
                serverId = linkServerId,
            )
        }
        return CustomSection(
            id = "custom_section_server_${serverId ?: "default"}",
            title = title,
            links = links,
            serverId = serverId,
        )
    }

    /**
     * 将自定义版块保存/同步至 Discourse 服务端（与 PC 端打通）。
     * [oldServerLinkIds] 用于在更新时告知后端已删除的 link 需要带上 _destroy: true。
     */
    suspend fun saveCustomSidebarSection(
        section: CustomSection,
        oldServerLinkIds: List<Long> = emptyList(),
    ): CustomSection {
        val serverId = section.serverId
        val linksArray = mutableListOf<JsonObject>()

        // 现存条目
        section.links.forEach { link ->
            val linkMap = mutableMapOf<String, JsonElement>(
                "name" to JsonPrimitive(link.name),
                "value" to JsonPrimitive(link.url),
                "icon" to JsonPrimitive(CustomSectionIconMapper.toFontAwesome(link.icon)),
            )
            if (link.serverId != null && link.serverId > 0L) {
                linkMap["id"] = JsonPrimitive(link.serverId)
            }
            linksArray.add(JsonObject(linkMap))
        }

        // 被删除的条目（通知 Rails 删除对应记录）
        val currentServerIds = section.links.mapNotNull { it.serverId }.toSet()
        oldServerLinkIds.filter { it !in currentServerIds }.forEach { removedId ->
            linksArray.add(
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(removedId),
                        "_destroy" to JsonPrimitive(true),
                        "name" to JsonPrimitive(""),
                        "value" to JsonPrimitive(""),
                        "icon" to JsonPrimitive("link"),
                    ),
                ),
            )
        }

        val payload = JsonObject(
            mapOf(
                "title" to JsonPrimitive(section.title),
                "links" to JsonArray(linksArray),
            ),
        ).toString()

        val responseBody = if (serverId != null && serverId > 0L) {
            mutate("/sidebar_sections/$serverId.json", "PUT", payload)
        } else {
            mutate("/sidebar_sections.json", "POST", payload)
        }

        // 解析返回结果中的新 ID
        val returnedObj = runCatching {
            val element = json.parseToJsonElement(responseBody).jsonObject
            (element["sidebar_section"] as? JsonObject) ?: element
        }.getOrNull()

        val newServerId = returnedObj?.number("id") ?: serverId
        val returnedLinks = returnedObj?.objects("links").orEmpty()
        val mappedLinks = section.links.mapIndexed { index, link ->
            val matching = returnedLinks.getOrNull(index)
                ?: returnedLinks.firstOrNull { it.text("name") == link.name && it.text("value") == link.url }
            val linkServerId = matching?.number("id") ?: link.serverId
            link.copy(
                serverId = linkServerId,
                id = linkServerId?.toString() ?: link.id,
            )
        }

        return section.copy(
            id = "custom_section_server_${newServerId ?: "default"}",
            serverId = newServerId,
            links = mappedLinks,
        )
    }

    /**
     * 从服务端删除自定义侧边栏分区。
     */
    suspend fun deleteCustomSidebarSection(serverId: Long) {
        if (serverId <= 0L) return
        try {
            mutate("/sidebar_sections/$serverId.json", "DELETE")
        } catch (e: Exception) {
            diagnostics.log("删除服务端自定义侧边栏分区失败: ${e.message}")
        }
    }

    /**
     * 使用账号密码登录（支持两步验证 2FA：TOTP 动态口令与备用恢复码）。
     * 流程：GET /session/csrf → POST /session。
     * 成功返回 true，凭据错误返回 false，需要两步验证抛 SecondFactorRequiredException，
     * CF 挑战抛 NeedsInteractiveVerification，连不上服务器抛 NetworkTimeoutException。
     */
    suspend fun sessionLogin(
        login: String,
        password: String,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
    ): Boolean = gate.withLock {
        throttle()
        val transport = transportProvider()
        // 1. 获取 CSRF token (独立 20s 超时)
        val csrfDeadlineNs = System.nanoTime() + Config.LOGIN_CALL_TIMEOUT_MS * 1_000_000L
        val csrf = loginCsrf(transport, csrfDeadlineNs)

        // 2. 发送登录请求（独立 20s 超时，登录固定两步请求，第二步走突发间隔）
        throttle(burst = true)
        val loginDeadlineNs = System.nanoTime() + Config.LOGIN_CALL_TIMEOUT_MS * 1_000_000L
        val fields = mutableMapOf<String, JsonElement>(
            "login" to JsonPrimitive(login),
            "password" to JsonPrimitive(password),
        )
        val trimmedToken = secondFactorToken?.trim().orEmpty()
        if (trimmedToken.isNotEmpty()) {
            fields["second_factor_token"] = JsonPrimitive(trimmedToken)
            fields["second_factor_method"] = JsonPrimitive(secondFactorMethod)
        }
        val body = JsonObject(fields).toString()
        val loginResult = loginCall("/session.json", loginDeadlineNs) { transport.write("/session.json", "POST", body, csrf) }
        diagnostics.recordStatus(transport.name, loginResult.status, "/session")
        if (loginResult.challenged) throw NeedsInteractiveVerification("登录请求被 Cloudflare 拦截 (403 挑战)")
        if (loginResult.status == 403) throw RiskControlException(403, "登录请求触发风控 (403)")
        if (loginResult.status == 0) throw NetworkTimeoutException()
        if (loginResult.status == 429) throw HttpStatusException(429, "登录尝试过于频繁，请稍后再试")
        val responseObj = try {
            json.parseToJsonElement(loginResult.body).jsonObject
        } catch (e: Exception) {
            if (loginResult.status in 200..299) return@withLock true
            throw HttpStatusException(loginResult.status, "HTTP ${loginResult.status}: ${loginResult.body.take(150)}")
        }
        val error = responseObj["error"]?.jsonPrimitive?.contentOrNull
            ?: responseObj["message"]?.jsonPrimitive?.contentOrNull
        val reason = responseObj["reason"]?.jsonPrimitive?.contentOrNull
        val totpEnabled = responseObj["totp_enabled"]?.jsonPrimitive?.booleanOrNull == true
        val backupEnabled = responseObj["backup_enabled"]?.jsonPrimitive?.booleanOrNull == true
        val securityKeyEnabled = responseObj["security_key_enabled"]?.jsonPrimitive?.booleanOrNull == true
        val hasMulti2fa = responseObj["multiple_second_factor_methods"] != null
        val isSecondFactor = reason?.contains("second_factor") == true ||
            totpEnabled || backupEnabled || securityKeyEnabled || hasMulti2fa
        if (isSecondFactor) {
            if (trimmedToken.isEmpty()) {
                diagnostics.log("账号已开启 2FA，等待输入两步验证码")
                throw SecondFactorRequiredException(
                    totpEnabled = totpEnabled || !backupEnabled,
                    backupEnabled = backupEnabled,
                    message = error?.takeIf { it.isNotBlank() } ?: "该账号已开启两步验证，请输入验证码",
                )
            } else {
                diagnostics.log("2FA 验证码错误: $error")
                throw LoginRejectedException(error?.takeIf { it.isNotBlank() } ?: "两步验证码错误或已过期，请重试")
            }
        }
        if (!error.isNullOrBlank()) {
            diagnostics.log("登录失败: $error")
            throw LoginRejectedException(error)
        }
        if (loginResult.status !in 200..299) {
            throw HttpStatusException(loginResult.status, "HTTP ${loginResult.status}: ${loginResult.body.take(150)}")
        }
        if (responseObj["user"] == null) {
            throw LoginRejectedException("登录未返回有效用户凭据，响应: ${loginResult.body.take(150)}")
        }
        true
    }

    /**
     * 向指定邮箱发送一次性登录链接邮件（Discourse magic link）。
     * 用户从邮件链接中复制 token，再交由 ViewModel.loginWithEmailCode 处理。
     */
    suspend fun sendEmailLogin(email: String) = gate.withLock {
        throttle()
        val transport = transportProvider()
        val csrfDeadlineNs = System.nanoTime() + Config.LOGIN_CALL_TIMEOUT_MS * 1_000_000L
        val csrf = loginCsrf(transport, csrfDeadlineNs)
        throttle(burst = true)
        val sendDeadlineNs = System.nanoTime() + Config.LOGIN_CALL_TIMEOUT_MS * 1_000_000L
        val safeEmail = email.replace("\"", "\\\"")
        val body = "{\"login\":\"$safeEmail\"}"
        val result = loginCall("/u/email-login.json", sendDeadlineNs) { transport.write("/u/email-login.json", "POST", body, csrf) }
        diagnostics.recordStatus(transport.name, result.status, "/u/email-login")
        if (result.challenged) throw NeedsInteractiveVerification("发邮件请求被 Cloudflare 拦截")
        if (result.status == 403) throw RiskControlException(403, "发送登录邮件触发风控 (403)")
        if (result.status == 0) throw NetworkTimeoutException()
        if (result.status !in 200..299) throw HttpStatusException(result.status, result.body.take(120))
    }

    private suspend fun loginCsrf(transport: Transport, deadlineNs: Long = 0L): String {
        val result = loginCall("/session/csrf.json", deadlineNs) { transport.get("/session/csrf.json") }
        if (result.challenged) throw NeedsInteractiveVerification("CSRF 请求被 Cloudflare 拦截")
        if (result.status == 403) throw RiskControlException(403, "获取登录凭证触发风控 (403)")
        if (result.status == 0) throw NetworkTimeoutException()
        if (result.status !in 200..299) throw HttpStatusException(result.status, "无法获取 CSRF token")
        return json.parseToJsonElement(result.body).jsonObject["csrf"]
            ?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("CSRF 响应缺少 token 字段")
    }

    /**
     * 用邮件里的 token 完成登录（支持携带两步验证码）。
     */
    suspend fun emailLoginWithToken(
        token: String,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
    ) = gate.withLock {
        throttle()
        val transport = transportProvider()
        val csrfDeadlineNs = System.nanoTime() + Config.LOGIN_CALL_TIMEOUT_MS * 1_000_000L
        val csrf = loginCsrf(transport, csrfDeadlineNs)
        throttle(burst = true)
        val loginDeadlineNs = System.nanoTime() + Config.LOGIN_CALL_TIMEOUT_MS * 1_000_000L
        val path = "/session/email-login/${encode(token)}"
        val trimmed2fa = secondFactorToken?.trim().orEmpty()
        val reqBody = if (trimmed2fa.isNotEmpty()) {
            JsonObject(
                mapOf(
                    "second_factor_token" to JsonPrimitive(trimmed2fa),
                    "second_factor_method" to JsonPrimitive(secondFactorMethod),
                ),
            ).toString()
        } else {
            "{}"
        }
        val result = loginCall(path, loginDeadlineNs) { transport.write(path, "POST", reqBody, csrf) }
        diagnostics.recordStatus(transport.name, result.status, "/session/email-login")
        if (result.challenged) throw NeedsInteractiveVerification("邮件登录请求被 Cloudflare 拦截")
        if (result.status == 403) throw RiskControlException(403, "验证码登录触发风控 (403)")
        if (result.status == 0) throw NetworkTimeoutException()

        val body = runCatching { json.parseToJsonElement(result.body).jsonObject }.getOrNull()
        val serverError = body?.get("error")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val reason = body?.get("reason")?.jsonPrimitive?.contentOrNull
        val secondFactorRequired = body?.get("second_factor_required")?.jsonPrimitive?.booleanOrNull == true ||
            body?.get("totp_enabled")?.jsonPrimitive?.booleanOrNull == true ||
            reason == "invalid_second_factor" ||
            (trimmed2fa.isEmpty() && serverError != null && (
                serverError.contains("两步") ||
                    serverError.contains("二步") ||
                    serverError.contains("验证") ||
                    serverError.contains("second factor", ignoreCase = true) ||
                    serverError.contains("authentication code", ignoreCase = true)
                ))
        if (secondFactorRequired && trimmed2fa.isEmpty()) {
            diagnostics.log("邮件登录要求 2FA: ${serverError ?: "second_factor_required"}")
            throw SecondFactorRequiredException(
                totpEnabled = true,
                backupEnabled = true,
                message = "该账号已开启两步验证，请输入两步验证码",
            )
        }
        if (serverError != null) {
            diagnostics.log("邮件登录被拒: $serverError")
            throw LoginRejectedException(serverError)
        }
        if (result.status !in 200..299) {
            throw LoginRejectedException("登录链接无效或已过期，请重新获取")
        }
    }

    /**
     * 登录链路的硬超时。
     * OkHttp 的 connectTimeout 只覆盖建连,DNS 解析卡住时不生效;同时把实际网络调用放在独立
     * SupervisorJob 异步协程中等待,即使底层发生不可中断的同步阻塞 I/O,withTimeout 也能在
     * 到达预算时立即脱离并取消底层请求,保证 6 秒总超时严格生效。
     */
    private suspend fun loginCall(
        path: String,
        deadlineNs: Long = 0L,
        call: suspend () -> TransportResult,
    ): TransportResult {
        val timeoutMs = if (deadlineNs > 0L) {
            ((deadlineNs - System.nanoTime()) / 1_000_000L).coerceAtMost(Config.LOGIN_CALL_TIMEOUT_MS)
        } else {
            Config.LOGIN_CALL_TIMEOUT_MS
        }
        if (timeoutMs <= 0L) {
            diagnostics.log("登录请求超时: $path")
            throw NetworkTimeoutException()
        }
        val deferred = CoroutineScope(Dispatchers.IO + SupervisorJob()).async { call() }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (timeout: TimeoutCancellationException) {
            deferred.cancel()
            diagnostics.log("登录请求超时: $path")
            throw NetworkTimeoutException()
        } finally {
            deferred.cancel()
            lastRequestAt = SystemClock.elapsedRealtime()
        }
    }

    /**
     * 列表行要显示真实分类名与颜色,所以第一次取列表时顺带把分类表拉下来。
     * 分类加载失败不应让列表失败 —— 退化为不显示分类标签。
     */
    private suspend fun categories(): Map<Int, Category> {
        categoryCache?.let { return it }
        return try {
            flattenCategories(fetchCategories()).associateBy { it.id }.also { categoryCache = it }
        } catch (error: CancellationException) {
            throw error
        } catch (error: NeedsInteractiveVerification) {
            throw error
        } catch (error: Exception) {
            diagnostics.log("分类表加载失败,列表将不显示分类: ${error.message}")
            emptyMap()
        }
    }

    private suspend fun toPage(
        response: TopicListResponse,
        awaitCategories: Boolean = true,
        excludeReadUnread: Boolean = false,
    ): TopicPage {
        val known = if (awaitCategories) categories() else categoryCache.orEmpty()
        val usersById = response.users.associateBy { it.id }
        // 本地已读集合优先于服务端 unseen:纯净模式下压根没上报过,服务端当然还认为没读。
        val filteredTopics = response.topicList.topics.distinctBy { it.id }.filterNot { topic ->
            excludeReadUnread && reading?.isUnreadExcluded(topic.id, topic.postsCount) == true
        }
        val items = filteredTopics.map { topic ->
            // Discourse 的 posters 顺序固定,第一个是原始发帖人。
            val author = topic.posters.firstOrNull()?.userId?.let { usersById[it] }
            val locallyRead = reading?.isUnreadExcluded(topic.id, topic.postsCount) == true
            val cachedLevel = sessionStore?.getTopicNotificationLevel(topic.id)
            val effectiveLevelCode = cachedLevel ?: topic.notificationLevel?.takeIf { it in 0..3 && it != 1 }
            val notificationLevel = effectiveLevelCode?.let { TopicNotificationLevel.fromCode(it) }
            TopicListItem(
                topic = if (topic.unseen && locallyRead) topic.copy(unseen = false) else topic,
                authorUsername = author?.username ?: topic.lastPosterUsername,
                avatarUrl = avatarUrlOf(author?.avatarTemplate),
                category = topic.categoryId?.let { known[it] },
                trustLevel = author?.trustLevel,
                notificationLevel = notificationLevel,
            )
        }
        return TopicPage(items = items, hasMore = response.topicList.moreTopicsUrl != null)
    }

    private suspend fun request(path: String, burst: Boolean = false, present: Boolean = true): String = gate.withLock {
        var attempt = 0
        while (true) {
            throttle(burst)
            val transport = transportProvider()
            val result = try {
                transport.get(path, present = present)
            } finally {
                lastRequestAt = SystemClock.elapsedRealtime()
            }
            diagnostics.recordStatus(transport.name, result.status, path)
            when {
                result.challenged -> throw NeedsInteractiveVerification("请求 $path 仍被 Cloudflare 挑战拦截")
                result.status in 200..299 -> return result.body
                // 连不上服务器时重试只是让用户多等十几秒,直接报错;5xx 才是值得重试的服务端抖动。
                result.status == 0 -> throw NetworkTimeoutException()
                result.status >= 500 && attempt < MAX_RETRIES -> {
                    delay(BACKOFF_BASE_MS shl attempt)
                    attempt++
                }
                else -> throw HttpStatusException(result.status, result.body.take(120))
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun throttle(burst: Boolean = false) {
        val interval = if (burst) Config.BURST_REQUEST_INTERVAL_MS else Config.MIN_REQUEST_INTERVAL_MS
        val wait = interval - (SystemClock.elapsedRealtime() - lastRequestAt)
        if (wait > 0) delay(wait)
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val BACKOFF_BASE_MS = 1_000L

        /** 通知分区一次加载的目标条数。 */
        const val PAGE_TARGET = 20

        /** 单次扫描服务端通知流的条数。取大一些,低频类型才不至于每页只命中一两条。 */
        const val NOTIFICATION_SCAN_LIMIT = 60

        /** 单次加载最多往前扫几轮,避免某一类通知很久没有时无限翻页。 */
        const val MAX_SCAN_ROUNDS = 5

        /** 阅读进度上报的时长边界(毫秒)。服务端会按批次封顶,客户端先规整一遍免得被整条丢弃。 */
        const val MIN_READ_TIME_MS = 1_000L
        const val MAX_POST_TIME_MS = 60_000L
        const val MAX_TOPIC_TIME_MS = 30 * 60 * 1_000L
    }
}
