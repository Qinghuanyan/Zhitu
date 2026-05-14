package me.rerere.rikkahub.di

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.AILoggingManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.amap.MapWarmupManager
import me.rerere.rikkahub.data.analytics.AnalyticsLogger
import me.rerere.rikkahub.data.analytics.FirebaseAnalyticsLogger
import me.rerere.rikkahub.data.analytics.NoOpAnalyticsLogger
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.firebase.FirebaseRemoteConfigService
import me.rerere.rikkahub.data.firebase.NoOpRemoteConfigService
import me.rerere.rikkahub.data.firebase.RemoteConfigService
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        Highlighter(get())
    }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get())
    }

    single {
        UpdateChecker(get())
    }

    single {
        AppScope()
    }

    single {
        MapWarmupManager(get())
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single<RemoteConfigService> {
        if (BuildConfig.HAS_GOOGLE_SERVICES) {
            FirebaseRemoteConfigService(Firebase.remoteConfig)
        } else {
            NoOpRemoteConfigService
        }
    }

    single<AnalyticsLogger> {
        if (BuildConfig.HAS_GOOGLE_SERVICES) {
            FirebaseAnalyticsLogger(Firebase.analytics)
        } else {
            NoOpAnalyticsLogger
        }
    }

    single {
        AILoggingManager()
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            travelPlanningDataRepository = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
