package com.unidownco.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

object NetworkClient {

    const val USER_AGENT_MOBILE = "Mozilla/5.0 (Linux; Android 15; OnePlus 13R Build/UKQ1.230924.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.102 Mobile Safari/537.36"
    const val USER_AGENT_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun newRequest(url: String, headers: Map<String, String> = emptyMap(), rangeStart: Long = 0L): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT_MOBILE)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")

        if (rangeStart > 0L) {
            builder.header("Range", "bytes=$rangeStart-")
        }

        headers.forEach { (key, value) ->
            builder.header(key, value)
        }

        return builder.build()
    }

    suspend fun execute(request: Request): Response {
        return okHttpClient.newCall(request).execute()
    }
}
