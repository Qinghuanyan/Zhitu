package me.rerere.rikkahub.data.repository

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.api.AmapForecastDayDto
import me.rerere.rikkahub.data.api.AmapGeocodeDto
import me.rerere.rikkahub.data.api.AmapPoiDto
import me.rerere.rikkahub.data.api.AmapTip
import me.rerere.rikkahub.data.api.AmapWebServiceApi
import me.rerere.rikkahub.data.api.QWeatherApi
import me.rerere.rikkahub.data.api.QWeatherDailyDto
import me.rerere.rikkahub.data.model.TravelPlanningFacts
import me.rerere.rikkahub.data.model.TravelPoi
import me.rerere.rikkahub.data.model.TravelRecommendationCategory
import me.rerere.rikkahub.data.model.TravelRecommendationItem
import me.rerere.rikkahub.data.model.TravelRouteHint
import me.rerere.rikkahub.data.model.TravelSearchSuggestion
import me.rerere.rikkahub.data.model.TravelWeatherDay

class TravelPlanningDataRepository(
    private val amapApi: AmapWebServiceApi,
    private val qWeatherApi: QWeatherApi,
) {
    suspend fun searchDestinationSuggestions(query: String): List<TravelSearchSuggestion> {
        if (query.isBlank() || BuildConfig.AMAP_WEB_API_KEY.isBlank()) return emptyList()
        return runCatching {
            amapApi.inputTips(key = BuildConfig.AMAP_WEB_API_KEY, keywords = query.trim()).tips.mapNotNull(::tipToSuggestion)
        }.getOrDefault(emptyList())
    }

    suspend fun searchPois(keyword: String, cityOrLocation: String? = null): List<TravelPoi> {
        if (keyword.isBlank() || BuildConfig.AMAP_WEB_API_KEY.isBlank()) return emptyList()
        return runCatching {
            amapApi.searchText(key = BuildConfig.AMAP_WEB_API_KEY, keywords = keyword.trim(), city = cityOrLocation?.takeIf { it.isNotBlank() }, extensions = "all").pois.map(::poiToTravelPoi)
        }.getOrDefault(emptyList())
    }

    suspend fun searchNearbyPois(location: Pair<Double, Double>, category: TravelRecommendationCategory, keyword: String? = null): List<TravelRecommendationItem> {
        if (BuildConfig.AMAP_WEB_API_KEY.isBlank()) return emptyList()
        val (lat, lon) = location
        val keywords = keyword?.takeIf { it.isNotBlank() }?.let(::listOf) ?: defaultKeywords(category)
        val results = mutableListOf<TravelRecommendationItem>()
        keywords.forEach { term ->
            val items = runCatching {
                amapApi.searchAround(
                    key = BuildConfig.AMAP_WEB_API_KEY,
                    location = "$lon,$lat",
                    keywords = term,
                    extensions = "all",
                ).pois.map { poiToRecommendation(it, category) }
            }.getOrDefault(emptyList())
            results += items
            if (results.isNotEmpty()) return@forEach
        }
        return results
            .filter { it.lat != null && it.lon != null }
            .distinctBy { it.id }
    }

    suspend fun resolveAddress(address: String): TravelSearchSuggestion? {
        if (address.isBlank() || BuildConfig.AMAP_WEB_API_KEY.isBlank()) return null
        return runCatching { amapApi.geocode(key = BuildConfig.AMAP_WEB_API_KEY, address = address.trim()).geocodes.firstOrNull()?.let(::geocodeToSuggestion) }.getOrNull()
    }

    suspend fun resolveLatLon(lat: Double, lon: Double): TravelSearchSuggestion? {
        if (BuildConfig.AMAP_WEB_API_KEY.isBlank()) return null
        return runCatching {
            val regeo = amapApi.regeo(key = BuildConfig.AMAP_WEB_API_KEY, location = "$lon,$lat").regeocode ?: return@runCatching null
            val component = regeo.addressComponent
            TravelSearchSuggestion(
                id = "$lat,$lon",
                name = regeo.formatted_address.ifBlank { component?.city?.firstOrNull().orEmpty() },
                district = component?.district.orEmpty(),
                address = regeo.formatted_address,
                city = component?.city?.firstOrNull().orEmpty(),
                adCode = "",
                lat = lat,
                lon = lon,
            )
        }.getOrNull()
    }

    suspend fun getWeatherSummary(destination: String, days: Int?): List<TravelWeatherDay> {
        if (destination.isBlank()) return emptyList()
        val qWeather = runCatching {
            val city = qWeatherApi.cityLookup(location = destination.trim(), key = qWeatherQueryKey(), authorization = qWeatherAuthorization()).location.firstOrNull() ?: return@runCatching emptyList()
            val daily = if ((days ?: 0) in 1..3) qWeatherApi.weather3d(location = city.id, key = qWeatherQueryKey(), authorization = qWeatherAuthorization()).daily else qWeatherApi.weather7d(location = city.id, key = qWeatherQueryKey(), authorization = qWeatherAuthorization()).daily
            daily.map(::dailyToWeatherDay)
        }.getOrDefault(emptyList())
        if (qWeather.isNotEmpty()) return qWeather
        if (BuildConfig.AMAP_WEB_API_KEY.isBlank()) return emptyList()
        return runCatching {
            val suggestion = resolveAddress(destination) ?: searchDestinationSuggestions(destination).firstOrNull() ?: return@runCatching emptyList()
            val adCode = suggestion.adCode.takeIf { it.isNotBlank() } ?: return@runCatching emptyList()
            amapApi.weatherForecast(key = BuildConfig.AMAP_WEB_API_KEY, city = adCode).forecasts.firstOrNull()?.casts.orEmpty().map(::forecastToWeatherDay)
        }.getOrDefault(emptyList())
    }

    suspend fun getCurrentWeatherSummary(destination: String): String {
        if (destination.isBlank()) return ""
        val qWeather = runCatching {
            val city = qWeatherApi.cityLookup(location = destination.trim(), key = qWeatherQueryKey(), authorization = qWeatherAuthorization()).location.firstOrNull() ?: return@runCatching ""
            val now = qWeatherApi.weatherNow(location = city.id, key = qWeatherQueryKey(), authorization = qWeatherAuthorization()).now ?: return@runCatching ""
            buildString {
                append(now.text.ifBlank { "天气" })
                if (now.temp.isNotBlank()) append(" ${now.temp}°C")
                if (now.windDir.isNotBlank()) append(" · ${now.windDir} ${now.windScale}级")
            }
        }.getOrDefault("")
        if (qWeather.isNotBlank()) return qWeather
        if (BuildConfig.AMAP_WEB_API_KEY.isBlank()) return ""
        return runCatching {
            val suggestion = resolveAddress(destination) ?: searchDestinationSuggestions(destination).firstOrNull() ?: return@runCatching ""
            val adCode = suggestion.adCode.takeIf { it.isNotBlank() } ?: return@runCatching ""
            val live = amapApi.weatherLive(key = BuildConfig.AMAP_WEB_API_KEY, city = adCode).lives.firstOrNull() ?: return@runCatching ""
            buildString {
                append(live.weather.ifBlank { "天气" })
                if (live.temperature.isNotBlank()) append(" ${live.temperature}°C")
                if (live.winddirection.isNotBlank()) append(" · ${live.winddirection} ${live.windpower}级")
            }
        }.getOrDefault("")
    }
    suspend fun buildRouteHints(poiSequence: List<TravelPoi>, city: String? = null): List<TravelRouteHint> {
        if (BuildConfig.AMAP_WEB_API_KEY.isBlank() || poiSequence.size < 2) return emptyList()
        return poiSequence.zipWithNext().mapNotNull { (from, to) ->
            val fromLat = from.lat ?: return@mapNotNull null
            val fromLon = from.lon ?: return@mapNotNull null
            val toLat = to.lat ?: return@mapNotNull null
            val toLon = to.lon ?: return@mapNotNull null
            val distance = haversineMeters(fromLat, fromLon, toLat, toLon)
            val route = if (distance <= 1500) {
                runCatching {
                    val path = amapApi.walkingRoute(key = BuildConfig.AMAP_WEB_API_KEY, origin = "$fromLon,$fromLat", destination = "$toLon,$toLat").route?.paths?.firstOrNull()
                    TravelRouteHint(from.id, to.id, "walking", path?.distance?.toIntOrNull(), path?.duration?.toIntOrNull(), walkingSummary(path?.distance?.toIntOrNull(), path?.duration?.toIntOrNull(), from.name, to.name))
                }.getOrNull()
            } else {
                runCatching {
                    val routeCity = city?.takeIf { it.isNotBlank() } ?: from.address.takeIf { it.isNotBlank() } ?: "Beijing"
                    val transit = amapApi.transitRoute(key = BuildConfig.AMAP_WEB_API_KEY, origin = "$fromLon,$fromLat", destination = "$toLon,$toLat", city = routeCity).route?.transits?.firstOrNull()
                    TravelRouteHint(from.id, to.id, "transit", transit?.distance?.toIntOrNull(), transit?.duration?.toIntOrNull(), transitSummary(transit?.distance?.toIntOrNull(), transit?.duration?.toIntOrNull(), from.name, to.name))
                }.getOrNull()
            }
            route ?: TravelRouteHint(
                from.id,
                to.id,
                if (distance <= 1500) "walking" else "generic",
                distance.roundToInt(),
                null,
                if (distance <= 1500) "从${from.name}步行到${to.name}" else "从${from.name}前往${to.name}，建议优先地铁/公交，不便时可打车"
            )
        }
    }

    suspend fun buildPlanningFacts(destination: String, days: Int?, origin: String? = null, transportPreferences: List<String> = emptyList()): TravelPlanningFacts {
        val destinationFacts = searchDestinationSuggestions(destination).take(3)
        val resolved = destinationFacts.firstOrNull() ?: resolveAddress(destination)
        val location = resolved?.lat?.let { lat -> resolved.lon?.let { lon -> lat to lon } }
        val hotels = location?.let { searchNearbyPois(it, TravelRecommendationCategory.hotel) }.orEmpty()
        val foods = location?.let { searchNearbyPois(it, TravelRecommendationCategory.food) }.orEmpty()
        val activities = location?.let { searchNearbyPois(it, TravelRecommendationCategory.activity) }.orEmpty()
        val candidatePois = (hotels.map(::recommendationToPoi) + foods.map(::recommendationToPoi) + activities.map(::recommendationToPoi)).distinctBy { it.id }
        return TravelPlanningFacts(
            destinationFacts = destinationFacts,
            candidatePois = candidatePois,
            nearbyHotels = hotels,
            nearbyFoods = foods,
            nearbyActivities = activities,
            commercialHotels = hotels.filter { it.priceHint.isNotBlank() || it.ratingText.isNotBlank() },
            commercialActivities = activities.filter { it.priceHint.isNotBlank() || it.ratingText.isNotBlank() },
            intercityTransportHints = buildIntercityTransportHints(origin, destination, transportPreferences),
            dailyWeather = getWeatherSummary(destination, days),
            routeHints = buildRouteHints(candidatePois.take(4), resolved?.city),
        )
    }

    private fun tipToSuggestion(tip: AmapTip): TravelSearchSuggestion? {
        if (tip.name.isBlank()) return null
        val (lat, lon) = parseLocation(tip.location)
        return TravelSearchSuggestion(tip.id.ifBlank { tip.name }, tip.name, tip.district, tip.address, tip.city.firstOrNull().orEmpty(), tip.adcode, lat, lon)
    }

    private fun geocodeToSuggestion(dto: AmapGeocodeDto): TravelSearchSuggestion {
        val (lat, lon) = parseLocation(dto.location)
        return TravelSearchSuggestion(dto.location.ifBlank { dto.formatted_address }, dto.formatted_address.ifBlank { dto.district.ifBlank { dto.city.firstOrNull().orEmpty() } }, dto.district, dto.formatted_address, dto.city.firstOrNull().orEmpty(), dto.adcode, lat, lon)
    }

    private fun poiToTravelPoi(poi: AmapPoiDto): TravelPoi {
        val (lat, lon) = parseLocation(poi.location)
        return TravelPoi(poi.id.ifBlank { poi.name }, poi.name, poi.type.ifBlank { poi.typecode }, lat, lon, listOf(poi.businessArea, poi.cityName, poi.district, poi.address).filter { it.isNotBlank() }.distinct().joinToString(" "))
    }

    private fun poiToRecommendation(poi: AmapPoiDto, category: TravelRecommendationCategory): TravelRecommendationItem {
        val (lat, lon) = parseLocation(poi.location)
        val inventoryHint = poi.bizExt?.openTime2?.takeIf { it.isNotBlank() } ?: poi.bizExt?.openTime?.takeIf { it.isNotBlank() } ?: ""
        return TravelRecommendationItem(
            id = poi.id.ifBlank { poi.name },
            category = category,
            title = poi.name,
            subtitle = poi.address,
            tags = buildPoiTags(poi),
            reason = buildReason(category, poi),
            priceHint = buildPriceHint(category, poi),
            ratingText = poi.bizExt?.rating.orEmpty(),
            area = listOf(poi.businessArea, poi.cityName, poi.district).filter { it.isNotBlank() }.distinct().joinToString(" "),
            inventoryHint = inventoryHint,
            bookingUrl = poi.website.takeIf { it.isNotBlank() } ?: "",
            source = "amap",
            lat = lat,
            lon = lon,
        )
    }

    private fun recommendationToPoi(item: TravelRecommendationItem): TravelPoi = TravelPoi(item.id, item.title, item.category.name, item.lat, item.lon, item.subtitle, item.id)

    private fun dailyToWeatherDay(daily: QWeatherDailyDto): TravelWeatherDay = TravelWeatherDay(
        daily.fxDate,
        buildString {
            append(daily.textDay.ifBlank { "天气" })
            if (daily.tempMin.isNotBlank() || daily.tempMax.isNotBlank()) {
                append(" ${daily.tempMin.ifBlank { "-" }}~${daily.tempMax.ifBlank { "-" }}°C")
            }
        },
        daily.tempMin,
        daily.tempMax,
    )
    private fun forecastToWeatherDay(day: AmapForecastDayDto): TravelWeatherDay = TravelWeatherDay(
        day.date,
        buildString {
            append(day.dayweather.ifBlank { day.nightweather.ifBlank { "天气" } })
            if (day.nightweather.isNotBlank() && day.nightweather != day.dayweather) append(" 转 ${day.nightweather}")
            if (day.nighttemp.isNotBlank() || day.daytemp.isNotBlank()) append(" ${day.nighttemp.ifBlank { "-" }}~${day.daytemp.ifBlank { "-" }}°C")
        },
        day.nighttemp,
        day.daytemp,
    )
    private fun buildReason(category: TravelRecommendationCategory, poi: AmapPoiDto): String {
        val area = poi.businessArea.ifBlank { poi.district.ifBlank { poi.cityName } }.ifBlank { "目的地附近" }
        return when (category) {
            TravelRecommendationCategory.hotel -> "${area}可入住，适合作为行程落脚点"
            TravelRecommendationCategory.food -> "${area}附近可到达的真实餐饮点"
            TravelRecommendationCategory.activity -> "${area}附近值得安排的活动/景点"
        }
    }
    private fun defaultKeywords(category: TravelRecommendationCategory): List<String> = when (category) {
        TravelRecommendationCategory.hotel -> listOf("酒店", "宾馆", "民宿")
        TravelRecommendationCategory.food -> listOf("美食", "餐厅", "小吃")
        TravelRecommendationCategory.activity -> listOf("景点", "旅游景点", "景区")
    }
    private fun walkingSummary(distance: Int?, duration: Int?, fromName: String, toName: String): String {
        val d = distance?.let { "%.1f公里".format(it / 1000.0) } ?: "短距离"
        val t = duration?.let { "${max(1, it / 60)}分钟" } ?: "步行可达"
        return "从${fromName}步行到${toName}，约${t}（${d}）"
    }
    private fun transitSummary(distance: Int?, duration: Int?, fromName: String, toName: String): String {
        val d = distance?.let { "%.1f公里".format(it / 1000.0) } ?: "中长距离"
        val t = duration?.let { "${max(1, it / 60)}分钟" } ?: "建议公交/地铁"
        return "从${fromName}前往${toName}，公交/地铁约${t}（${d}）"
    }
    private fun buildPoiTags(poi: AmapPoiDto): List<String> = buildList { addAll(poi.type.split("|").take(3).map { it.substringAfterLast(";") }); poi.keytag.takeIf { it.isNotBlank() }?.let(::add) }.filter { it.isNotBlank() }.distinct().take(4)
    private fun buildPriceHint(category: TravelRecommendationCategory, poi: AmapPoiDto): String {
        val lowestPrice = poi.bizExt?.lowestPrice.orEmpty()
        val cost = poi.bizExt?.cost.orEmpty()
        return when {
            lowestPrice.isNotBlank() -> "参考价 ¥$lowestPrice 起"
            cost.isNotBlank() -> when (category) {
                TravelRecommendationCategory.hotel -> "参考价 ¥$cost"
                TravelRecommendationCategory.food -> "人均 ¥$cost"
                TravelRecommendationCategory.activity -> "参考价 ¥$cost"
            }
            else -> ""
        }
    }
    private fun buildIntercityTransportHints(origin: String?, destination: String, transportPreferences: List<String>): List<String> {
        if (origin.isNullOrBlank() || destination.isBlank() || origin == destination) return emptyList()
        val preferred = transportPreferences.filter { it.isNotBlank() }.joinToString("、")
        return listOf(
            buildString {
                append("从$origin 前往 $destination 的到达/返程交通需提前确认")
                if (preferred.isNotBlank()) append("，优先考虑：$preferred")
            }
        )
    }
    private fun qWeatherAuthorization(): String? { val token = BuildConfig.QWEATHER_AUTH_TOKEN.trim(); return token.takeIf { it.isNotBlank() }?.let { "Bearer $it" } }
    private fun qWeatherQueryKey(): String? { val token = BuildConfig.QWEATHER_AUTH_TOKEN.trim(); return BuildConfig.QWEATHER_API_KEY.takeIf { token.isBlank() && it.isNotBlank() } }
    private fun parseLocation(location: String): Pair<Double?, Double?> { if (location.isBlank() || !location.contains(",")) return null to null; val lon = location.substringBefore(",").toDoubleOrNull(); val lat = location.substringAfter(",").toDoubleOrNull(); return lat to lon }
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val r = 6371000.0; val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1); val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0); val c = 2 * atan2(sqrt(a), sqrt(1 - a)); return r * c }
}
