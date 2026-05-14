package me.rerere.rikkahub.data.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import me.rerere.rikkahub.R

interface RemoteConfigService {
    fun initialize()

    fun getString(key: String): String
}

object NoOpRemoteConfigService : RemoteConfigService {
    override fun initialize() = Unit

    override fun getString(key: String): String = ""
}

class FirebaseRemoteConfigService(
    private val remoteConfig: FirebaseRemoteConfig
) : RemoteConfigService {
    override fun initialize() {
        remoteConfig.setConfigSettingsAsync(remoteConfigSettings {
            minimumFetchIntervalInSeconds = 1800
        })
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate()
    }

    override fun getString(key: String): String = remoteConfig.getString(key)
}
