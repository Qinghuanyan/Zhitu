package me.rerere.rikkahub.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface AmapWebServiceApi {
    @GET("/v3/assistant/inputtips")
    suspend fun inputTips(
        @Query("key") key: String,
        @Query("keywords") keywords: String,
        @Query("city") city: String? = null,
        @Query("datatype") datatype: String = "all",
    ): AmapInputTipsResponse

    @GET("/v3/place/text")
    suspend fun searchText(
        @Query("key") key: String,
        @Query("keywords") keywords: String,
        @Query("city") city: String? = null,
        @Query("offset") offset: Int = 10,
        @Query("page") page: Int = 1,
        @Query("extensions") extensions: String = "base",
    ): AmapPoiSearchResponse

    @GET("/v3/place/around")
    suspend fun searchAround(
        @Query("key") key: String,
        @Query("location") location: String,
        @Query("keywords") keywords: String? = null,
        @Query("types") types: String? = null,
        @Query("radius") radius: Int = 3000,
        @Query("offset") offset: Int = 10,
        @Query("page") page: Int = 1,
        @Query("extensions") extensions: String = "base",
    ): AmapPoiSearchResponse

    @GET("/v3/geocode/geo")
    suspend fun geocode(
        @Query("key") key: String,
        @Query("address") address: String,
    ): AmapGeocodeResponse

    @GET("/v3/geocode/regeo")
    suspend fun regeo(
        @Query("key") key: String,
        @Query("location") location: String,
        @Query("extensions") extensions: String = "all",
    ): AmapRegeoResponse

    @GET("/v3/direction/walking")
    suspend fun walkingRoute(
        @Query("key") key: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
    ): AmapDirectionResponse

    @GET("/v3/direction/transit/integrated")
    suspend fun transitRoute(
        @Query("key") key: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("city") city: String,
        @Query("cityd") cityd: String? = city,
    ): AmapTransitDirectionResponse

    @GET("/v3/weather/weatherInfo")
    suspend fun weatherLive(
        @Query("key") key: String,
        @Query("city") city: String,
        @Query("extensions") extensions: String = "base",
    ): AmapWeatherLiveResponse

    @GET("/v3/weather/weatherInfo")
    suspend fun weatherForecast(
        @Query("key") key: String,
        @Query("city") city: String,
        @Query("extensions") extensions: String = "all",
    ): AmapWeatherForecastResponse

    companion object {
        fun create(httpClient: OkHttpClient): AmapWebServiceApi {
            return Retrofit.Builder()
                .client(httpClient)
                .baseUrl("https://restapi.amap.com/")
                .addConverterFactory(JsonInstant.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(AmapWebServiceApi::class.java)
        }
    }
}

@Serializable
data class AmapInputTipsResponse(
    val status: String = "",
    val info: String = "",
    val count: String = "0",
    val tips: List<AmapTip> = emptyList(),
)

@Serializable
data class AmapTip(
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val id: String = "",
    val name: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val district: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val address: String = "",
    val adcode: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val location: String = "",
    @Serializable(with = FlexibleAmapStringListSerializer::class)
    val city: List<String> = emptyList(),
)

object FlexibleAmapStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleAmapString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> primitiveContent(element)
            is JsonArray -> element.firstOrNull()?.let { nested ->
                if (nested is JsonPrimitive) primitiveContent(nested) else ""
            }.orEmpty()
            is JsonObject -> ""
            JsonNull -> ""
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    private fun primitiveContent(primitive: JsonPrimitive): String {
        return if (primitive.isString) primitive.content else primitive.toString()
    }
}

object FlexibleAmapStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleAmapStringList", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return listOf(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                val value = if (element.isString) element.content else element.toString()
                value.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
            }
            is JsonArray -> element.mapNotNull { nested ->
                (nested as? JsonPrimitive)?.let { primitive ->
                    val value = if (primitive.isString) primitive.content else primitive.toString()
                    value.takeIf { it.isNotBlank() }
                }
            }
            is JsonObject, JsonNull -> emptyList()
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(ListSerializer(String.serializer()), value)
    }
}

@Serializable
data class AmapPoiSearchResponse(
    val status: String = "",
    val info: String = "",
    val count: String = "0",
    val pois: List<AmapPoiDto> = emptyList(),
)

@Serializable
data class AmapPoiDto(
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val id: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val name: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val address: String = "",
    @SerialName("adname")
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val district: String = "",
    @SerialName("cityname")
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val cityName: String = "",
    @SerialName("pname")
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val provinceName: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val location: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val type: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val typecode: String = "",
    @SerialName("business_area")
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val businessArea: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val keytag: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val tag: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val website: String = "",
    @SerialName("biz_ext")
    val bizExt: AmapBizExtDto? = null,
)

@Serializable
data class AmapBizExtDto(
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val rating: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val cost: String = "",
    @SerialName("lowest_price")
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val lowestPrice: String = "",
    @SerialName("open_time")
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val openTime: String = "",
    @SerialName("opentime2")
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val openTime2: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val star: String = "",
)

@Serializable
data class AmapGeocodeResponse(
    val status: String = "",
    val info: String = "",
    val geocodes: List<AmapGeocodeDto> = emptyList(),
)

@Serializable
data class AmapGeocodeDto(
    val formatted_address: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val location: String = "",
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val adcode: String = "",
    @Serializable(with = FlexibleAmapStringListSerializer::class)
    val city: List<String> = emptyList(),
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val district: String = "",
)

@Serializable
data class AmapRegeoResponse(
    val status: String = "",
    val info: String = "",
    val regeocode: AmapRegeocodeDto? = null,
)

@Serializable
data class AmapRegeocodeDto(
    val formatted_address: String = "",
    val addressComponent: AmapAddressComponentDto? = null,
)

@Serializable
data class AmapAddressComponentDto(
    @Serializable(with = FlexibleAmapStringListSerializer::class)
    val city: List<String> = emptyList(),
    @Serializable(with = FlexibleAmapStringSerializer::class)
    val district: String = "",
    val province: String = "",
    val township: String = "",
)

@Serializable
data class AmapDirectionResponse(
    val status: String = "",
    val info: String = "",
    val route: AmapRouteDto? = null,
)

@Serializable
data class AmapRouteDto(
    val paths: List<AmapPathDto> = emptyList(),
)

@Serializable
data class AmapPathDto(
    val distance: String = "",
    val duration: String = "",
)

@Serializable
data class AmapTransitDirectionResponse(
    val status: String = "",
    val info: String = "",
    val route: AmapTransitRouteDto? = null,
)

@Serializable
data class AmapTransitRouteDto(
    val transits: List<AmapTransitDto> = emptyList(),
)

@Serializable
data class AmapTransitDto(
    val distance: String = "",
    val duration: String = "",
    val cost: String = "",
)

@Serializable
data class AmapWeatherLiveResponse(
    val status: String = "",
    val info: String = "",
    val lives: List<AmapWeatherLiveDto> = emptyList(),
)

@Serializable
data class AmapWeatherLiveDto(
    val province: String = "",
    val city: String = "",
    val adcode: String = "",
    val weather: String = "",
    val temperature: String = "",
    val winddirection: String = "",
    val windpower: String = "",
    val humidity: String = "",
    val reporttime: String = "",
)

@Serializable
data class AmapWeatherForecastResponse(
    val status: String = "",
    val info: String = "",
    val forecasts: List<AmapForecastBlockDto> = emptyList(),
)

@Serializable
data class AmapForecastBlockDto(
    val city: String = "",
    val adcode: String = "",
    val province: String = "",
    val reporttime: String = "",
    val casts: List<AmapForecastDayDto> = emptyList(),
)

@Serializable
data class AmapForecastDayDto(
    val date: String = "",
    val dayweather: String = "",
    val nightweather: String = "",
    val daytemp: String = "",
    val nighttemp: String = "",
    val daywind: String = "",
    val nightwind: String = "",
    val daypower: String = "",
    val nightpower: String = "",
)
