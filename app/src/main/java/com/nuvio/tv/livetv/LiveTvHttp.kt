package com.nuvio.tv.livetv

import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches addon URLs as raw text using NuvioTV's permissive addon HTTP client. */
@Singleton
class LiveTvHttp @Inject constructor(
    @Named("addonPermissive") private val client: OkHttpClient,
) {
    suspend fun fetch(url: String, forceRefresh: Boolean): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).get()
        if (forceRefresh) builder.cacheControl(CacheControl.FORCE_NETWORK)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from addon")
            response.body?.string() ?: throw IOException("Empty response from addon")
        }
    }
}
