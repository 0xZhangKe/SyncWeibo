Hckage com.zhangke.algorithms

import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import kotlin.math.log

fun main() {
//    val allFollowings = WeiboRepo.getAllFollowing("5103204106")
//    println(allFollowings.joinToString("; "))
//    println(allFollowings.size)
    WeiboRepo.follow("1823630913")
}

object WeiboRepo {

    private val client = getOkHttpClient()

    fun getAllFollowing(uid: String): List<JsonObject> {
        val followingList = mutableListOf<JsonObject>()
        var pageId = 1
        var hasNext = true
        while (hasNext) {
            val currentPage = getWeiboFollowings(pageId++, uid)
            val users = currentPage.getAsJsonArray("users")
            followingList += users.map { it.asJsonObject.simplyUser() }
            if (users.size() == 0) {
                hasNext = false
            }
        }
        return followingList
    }

    private fun JsonObject.simplyUser(): JsonObject {
        val simplyUser = JsonObject()
        simplyUser.add("id", get("id"))
        simplyUser.add("name", get("name"))
        simplyUser.add("description", get("description"))
        return simplyUser
    }

    fun follow(uid: String) {
        val url = "https://weibo.com/ajax/friendships/create"
        //{"friend_uid":"1823630913","page":"profile","lpage":"profile"}
        val jsonBody = JsonObject().apply {
            addProperty("friend_uid", uid)
            addProperty("page", "profile")
            addProperty("lpage", "profile")
        }
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody())
            .build()
        client.newCall(request).execute()
    }

    private fun getWeiboFollowings(pageId: Int, uid: String): JsonObject {
        val url = "https://weibo.com/ajax/friendships/friends?page=$pageId&uid=$uid"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val responseString = client.newCall(request).execute().body!!.string()
        return gson.fromJson(responseString, JsonObject::class.java)
    }

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(WeiboHeaderInterceptor())
            .run {
                addInterceptor(HttpLoggingInterceptor { message -> println(message) }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                this
            }
            .build()
    }
}

private class WeiboHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .apply {
                weiboHeaders.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()
        return chain.proceed(request)
    }

    private val weiboHeaders: Map<String, String> = mapOf(
        "server-version" to "v2022.10.25.1",
        "sec-ch-ua" to "\"Chromium\";v=\"106\", \"Google Chrome\";v=\"106\", \"Not;A=Brand\";v=\"99\"",
        "sec-ch-ua-mobile" to "?0",
        "client-version" to "v2.36.10",
        "cookie" to COOKIE,
        "x-requested-with" to "XMLHttpRequest",
        "referer" to "https://weibo.com/1823630913?refer_flag=1001030103_",
        "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.0.0 Safari/537.36",
    )
}
