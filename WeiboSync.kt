package com.zhangke.algorithms

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.lang.RuntimeException

private val gson = Gson()

private const val SOURCE_UID = ""
private const val DEST_UID = ""

private const val COOKIE_SOURCE = ""

private const val COOKIE_DEST = ""

private const val DEST_TOKEN = ""

private const val FILE_PATH = "AllFollowing.json"

fun main() {
    collectAllFollowingToFile()
    startFollow()
}

private fun collectAllFollowingToFile() {
    val repo = WeiboRepo(COOKIE_SOURCE, null)
    val allFollowings = repo.getAllFollowing(SOURCE_UID)
    val allFollowingsJson = gson.toJson(allFollowings)
    val file = File(FILE_PATH).also {
        if (!it.exists()) it.createNewFile()
    }
    file.writeText(allFollowingsJson)
}

private fun startFollow() {
    val repo = WeiboRepo(COOKIE_DEST, DEST_TOKEN)
    val currentAllFollowing = repo.getAllFollowing(DEST_UID).map { it.getAsJsonPrimitive("id").asString }.toSet()
    println("You have ${currentAllFollowing.size} following at now.")
    val sourceAllFollowing =
        gson.fromJson<List<JsonObject>>(
            File(FILE_PATH).readText(),
            object : TypeToken<List<JsonObject>>() {}.type
        ).map { it.getAsJsonPrimitive("id").asString }
    val needFollowing = sourceAllFollowing.filter { !currentAllFollowing.contains(it) }
    println("The remaining ${needFollowing.size} users need follow.")
    var followCount = 0
    try {
        needFollowing.forEach {
            repo.follow(it)
            followCount++
        }
    } finally {
        println("Followed $followCount users.")
    }
}

class WeiboRepo(cookie: String, token: String?) {

    private val client = getOkHttpClient(cookie, token)

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

    private fun getWeiboFollowings(pageId: Int, uid: String): JsonObject {
        val url = "https://weibo.com/ajax/friendships/friends?page=$pageId&uid=$uid"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val responseString = client.newCall(request).execute().body!!.string()
        return gson.fromJson(responseString, JsonObject::class.java)
    }

    fun follow(uid: String) {
        val url = "https://weibo.com/ajax/friendships/create"
        val formBody = FormBody.Builder()
            .add("friend_uid", uid)
            .add("page", "profile")
            .add("lpage", "profile")
            .build()
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Follow failed! maybe case Weibo limited, see log for detail.")
        }
    }

    private fun getOkHttpClient(cookie: String, token: String?): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(WeiboHeaderInterceptor(cookie, token))
            .apply {
                addInterceptor(HttpLoggingInterceptor { message -> println(message) }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
            .build()
    }
}

private class WeiboHeaderInterceptor(cookie: String, token: String?) : Interceptor {

    private val weiboHeaders: Map<String, String>

    init {
        weiboHeaders = mutableMapOf(
            "server-version" to "v2022.10.25.1",
            "sec-ch-ua" to "\"Chromium\";v=\"106\", \"Google Chrome\";v=\"106\", \"Not;A=Brand\";v=\"99\"",
            "sec-ch-ua-mobile" to "?0",
            "client-version" to "v2.36.10",
            "cookie" to cookie,
            "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.0.0 Safari/537.36",
            "x-requested-with" to "XMLHttpRequest",
        ).also {
            if (!token.isNullOrEmpty()) {
                it["x-xsrf-token"] = token
            }
        }
    }

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
}
