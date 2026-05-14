package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TravelPlanningState {
    Idle,
    ExtractingBrief,
    DraftBrief,
    ReadyToGenerate,
    GeneratingPlan,
    Generated,
    Failed,
}

@Serializable
enum class TravelPlanStatus {
    draft_brief,
    ready_to_generate,
    generated,
    failed,
}

@Serializable
enum class TravelRecommendationCategory {
    hotel,
    food,
    activity,
}

@Serializable
enum class TravelItemCategory {
    transport,
    sightseeing,
    food,
    hotel,
    activity,
    free_time,
    shopping,
    other,
}

@Serializable
data class TravelPlanningBrief(
    val destination: String = "",
    val origin: String = "",
    val dateRange: String = "",
    val days: Int? = null,
    val travelerCount: Int? = null,
    val budgetLevel: String = "",
    val budgetText: String = "",
    val travelStyleTags: List<String> = emptyList(),
    val transportPreferences: List<String> = emptyList(),
    val hardConstraints: List<String> = emptyList(),
    val userIntentSummary: String = "",
) {
    fun canGenerate(): Boolean {
        val hasDestination = destination.isNotBlank()
        val hasDateInfo = dateRange.isNotBlank() || (days ?: 0) > 0
        val hasNeed = travelStyleTags.isNotEmpty() ||
            hardConstraints.isNotEmpty() ||
            userIntentSummary.isNotBlank()
        return hasDestination && hasDateInfo && hasNeed
    }
}

@Serializable
data class TravelRecommendationItem(
    val id: String,
    val category: TravelRecommendationCategory,
    val title: String,
    val subtitle: String = "",
    val tags: List<String> = emptyList(),
    val reason: String = "",
    val priceHint: String = "",
    val ratingText: String = "",
    val area: String = "",
    val inventoryHint: String = "",
    val bookingUrl: String = "",
    val source: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val sourceMessageIds: List<String> = emptyList(),
)

@Serializable
data class TravelItineraryItem(
    val id: String,
    val timeSlot: String = "",
    val title: String,
    val description: String = "",
    val category: TravelItemCategory = TravelItemCategory.other,
    val poiRefId: String? = null,
    val estimatedCost: String = "",
    val transportHint: String = "",
)

@Serializable
data class TravelItineraryDay(
    val dayIndex: Int,
    val title: String,
    val dateText: String = "",
    val weatherHint: String = "",
    val items: List<TravelItineraryItem> = emptyList(),
)

@Serializable
data class TravelPoi(
    val id: String,
    val name: String,
    val category: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String = "",
    val linkedRecommendationId: String? = null,
    val linkedItineraryItemId: String? = null,
)

@Serializable
data class TravelPlan(
    val conversationId: String,
    val brief: TravelPlanningBrief? = null,
    val hotels: List<TravelRecommendationItem> = emptyList(),
    val foods: List<TravelRecommendationItem> = emptyList(),
    val activities: List<TravelRecommendationItem> = emptyList(),
    val pois: List<TravelPoi> = emptyList(),
    val itineraryDays: List<TravelItineraryDay> = emptyList(),
    val generatedAt: Long? = null,
    val generationVersion: Int = 1,
    val status: TravelPlanStatus = TravelPlanStatus.draft_brief,
) {
    fun withBrief(newBrief: TravelPlanningBrief, status: TravelPlanStatus = this.status): TravelPlan {
        return copy(
            brief = newBrief,
            status = status,
        )
    }
}

@Serializable
data class TravelGeneratedPayload(
    val hotels: List<TravelRecommendationItem> = emptyList(),
    val foods: List<TravelRecommendationItem> = emptyList(),
    val activities: List<TravelRecommendationItem> = emptyList(),
    val pois: List<TravelPoi> = emptyList(),
    val itineraryDays: List<TravelItineraryDay> = emptyList(),
)
