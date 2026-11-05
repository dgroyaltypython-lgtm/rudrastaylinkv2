package com.rudradigital.rudrastaylink.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.rudradigital.rudrastaylink.security.TokenStore
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiProvider {
    fun create(context: Context, baseUrl: String): StayLinkApi {
        val cleanBase = normalizeBaseUrl(baseUrl)
        val tokenStore = TokenStore(context)
        val gson = GsonBuilder().serializeNulls().create()
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE // Do not log credentials or guest data.
        }

        val authenticator = Authenticator { _, response ->
            if (responseCount(response) >= 2) return@Authenticator null
            val refreshToken = tokenStore.refreshToken() ?: return@Authenticator null
            try {
                val body = gson.toJson(mapOf("refresh" to refreshToken))
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(cleanBase + "api/v1/auth/token/refresh/")
                    .post(body)
                    .build()
                OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build().newCall(request).execute().use { refreshResponse ->
                    if (!refreshResponse.isSuccessful) {
                        tokenStore.clear()
                        return@Authenticator null
                    }
                    val payload = refreshResponse.body?.string() ?: return@Authenticator null
                    val pair = gson.fromJson(payload, TokenResponse::class.java)
                    if (pair.access.isBlank()) return@Authenticator null
                    tokenStore.save(pair.access, pair.refresh.ifBlank { refreshToken })
                    response.request.newBuilder().header("Authorization", "Bearer ${pair.access}").build()
                }
            } catch (_: Exception) {
                null
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val isAuthEndpoint = path.contains("/auth/token/") || path.contains("/auth/token/refresh/")
                val access = if (isAuthEndpoint) null else tokenStore.accessToken()
                val authenticated = if (!access.isNullOrBlank()) {
                    request.newBuilder().header("Authorization", "Bearer $access").build()
                } else request
                chain.proceed(authenticated)
            }
            .authenticator(authenticator)
            .addInterceptor(logger)
            .build()

        return Retrofit.Builder()
            .baseUrl(cleanBase)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(StayLinkApi::class.java)
    }

    fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "Server address must start with http:// or https://"
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
