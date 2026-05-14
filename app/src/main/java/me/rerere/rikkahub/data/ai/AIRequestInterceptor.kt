package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.firebase.RemoteConfigService
import okhttp3.Interceptor
import okhttp3.Response

class AIRequestInterceptor(private val remoteConfig: RemoteConfigService) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val host = request.url.host

//        if (host == "api.siliconflow.cn") {
//            request = processSiliconCloudRequest(request)
//        }

        return chain.proceed(request)
    }

    // Handle SiliconCloud free-tier API key injection when remote config is enabled.
//    private fun processSiliconCloudRequest(request: Request): Request {
//        val authHeader = request.header("Authorization")
//        val path = request.url.encodedPath
//
//        if ((authHeader?.trim() == "Bearer" || authHeader?.trim() == "Bearer sk-") && path in listOf(
//                "/v1/chat/completions",
//                "/v1/models"
//            )
//        ) {
//            val bodyJson = request.readBodyAsJson()
//            val model = bodyJson?.jsonObject["model"]?.jsonPrimitiveOrNull?.content
//            val freeModels = remoteConfig.getString("silicon_cloud_free_models").split(",")
//            if (model.isNullOrEmpty() || model in freeModels) {
//                val apiKey = String(Base64.decode(remoteConfig.getString("silicon_cloud_api_key")))
//                return request.newBuilder()
//                    .header("Authorization", "Bearer $apiKey")
//                    .build()
//            }
//        }
//
//        return request
//    }
}
