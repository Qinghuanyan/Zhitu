package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TravelSearchSuggestion(
    val id: String,
    val name: String,
    val district: String = "",
    val address: String = "",
    val city: String = "",
    val adCode: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class TravelRouteHint(
    val fromPoiId: String,
    val toPoiId: String,
    val mode: String,
    val distanceMeters: Int? = null,
    val durationSeconds: Int? = null,
    val summary: String,
)

@Serializable
data class TravelWeatherDay(
    val date: String,
    val summary: String,
    val tempMin: String = "",
    val tempMax: String = "",
)

@Serializable
data class TravelPlanningFacts(
    val destinationFacts: List<TravelSearchSuggestion> = emptyList(),
    val candidatePois: List<TravelPoi> = emptyList(),
    val nearbyHotels: List<TravelRecommendationItem> = emptyList(),
    val nearbyFoods: List<TravelRecommendationItem> = emptyList(),
    val nearbyActivities: List<TravelRecommendationItem> = emptyList(),
    val commercialHotels: List<TravelRecommendationItem> = emptyList(),
    val commercialActivities: List<TravelRecommendationItem> = emptyList(),
    val intercityTransportHints: List<String> = emptyList(),
    val dailyWeather: List<TravelWeatherDay> = emptyList(),
    val routeHints: List<TravelRouteHint> = emptyList(),
)
