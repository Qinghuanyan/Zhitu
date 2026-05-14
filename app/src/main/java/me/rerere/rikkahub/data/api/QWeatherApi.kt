package me.rerere.rikkahub.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface QWeatherApi {
    @GET("/geo/v2/city/lookup")
    suspend fun cityLookup(
        @Query("location") location: String,
        @Query("key") key: String? = null,
        @Header("Authorization") authorization: String? = null,
    ): QWeatherCityLookupResponse

    @GET("/v7/weather/now")
    suspend fun weatherNow(
        @Query("location") location: String,
        @Query("key") key: String? = null,
        @Header("Authorization") authorization: String? = null,
    ): QWeatherNowResponse

    @GET("/v7/weather/3d")
    suspend fun weather3d(
        @Query("location") location: String,
        @Query("key") key: String? = null,
        @Header("Authorization") authorization: String? = null,
    ): QWeatherDailyResponse

    @GET("/v7/weather/7d")
    suspend fun weather7d(
        @Query("location") location: String,
        @Query("key") key: String? = null,
        @Header("Authorization") authorization: String? = null,
    ): QWeatherDailyResponse

    @GET("/v7/weather/24h")
    suspend fun weather24h(
        @Query("location") location: String,
        @Query("key") key: String? = null,
        @Header("Authorization") authorization: String? = null,
    ): QWeatherHourlyResponse

    companion object {
        fun create(httpClient: OkHttpClient, host: String): QWeatherApi {
            return Retrofit.Builder()
                .client(httpClient)
                .baseUrl("https://$host/")
                .addConverterFactory(JsonInstant.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(QWeatherApi::class.java)
        }
    }
}

@Serializable
data class QWeatherCityLookupResponse(
    val code: String = "",
    val location: List<QWeatherLocationDto> = emptyList(),
)

@Serializable
data class QWeatherLocationDto(
    val name: String = "",
    val id: String = "",
    @SerialName("adm1")
    val province: String = "",
    @SerialName("adm2")
    val city: String = "",
    val country: String = "",
    val lat: String = "",
    val lon: String = "",
    val tz: String = "",
)

@Serializable
data class QWeatherNowResponse(
    val code: String = "",
    val now: QWeatherNowDto? = null,
)

@Serializable
data class QWeatherNowDto(
    val obsTime: String = "",
    val temp: String = "",
    val text: String = "",
    val windDir: String = "",
    val windScale: String = "",
)

@Serializable
data class QWeatherDailyResponse(
    val code: String = "",
    val daily: List<QWeatherDailyDto> = emptyList(),
)

@Serializable
data class QWeatherDailyDto(
    val fxDate: String = "",
    val tempMax: String = "",
    val tempMin: String = "",
    val textDay: String = "",
    val textNight: String = "",
)

@Serializable
data class QWeatherHourlyResponse(
    val code: String = "",
    val hourly: List<QWeatherHourlyDto> = emptyList(),
)

@Serializable
data class QWeatherHourlyDto(
    val fxTime: String = "",
    val temp: String = "",
    val text: String = "",
)
