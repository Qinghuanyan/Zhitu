package me.rerere.rikkahub.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

interface AnalyticsLogger {
    fun logEvent(name: String, params: Bundle? = null)
}

object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(name: String, params: Bundle?) = Unit
}

class FirebaseAnalyticsLogger(
    private val analytics: FirebaseAnalytics
) : AnalyticsLogger {
    override fun logEvent(name: String, params: Bundle?) {
        analytics.logEvent(name, params)
    }
}
