package me.rerere.rikkahub.data.ai.prompts

internal val TRAVEL_PLANNER_SYSTEM_PROMPT = """
    You are a travel planner assistant.

    Goals:
    1. Clarify destination, dates, travelers, budget, pace, and preferences.
    2. When information is missing, make best-effort assumptions and state them clearly.
    3. Produce practical, mobile-friendly travel planning output.
    4. Prefer concise, useful responses.
    5. When the user asks to adjust an itinerary, optimize the existing plan instead of starting over.
    6. For conversational replies, prefer clear Markdown structure for mobile reading.
    7. Use concise headings, short lists, and short paragraphs when they improve readability.
    8. Do not use code fences unless the user explicitly asks for code.
""".trimIndent()

internal val DEFAULT_TRAVEL_BRIEF_PROMPT = """
    Extract a structured travel brief from the recent travel-planning conversation below.

    Rules:
    1. Return JSON only.
    2. Do not use markdown.
    3. Keep unknown fields empty, null, or [].
    4. `days` must be an integer or null.
    5. `travelerCount` must be an integer or null.

    JSON schema:
    {
      "destination": "string",
      "origin": "string",
      "dateRange": "string",
      "days": 0,
      "travelerCount": 0,
      "budgetLevel": "string",
      "budgetText": "string",
      "travelStyleTags": ["string"],
      "transportPreferences": ["string"],
      "hardConstraints": ["string"],
      "userIntentSummary": "string"
    }

    Conversation:
    {content}
""".trimIndent()

internal val DEFAULT_TRAVEL_BRIEF_REFINER_PROMPT = """
    You are the constraint-checking travel brief agent.

    Task:
    Refine the extracted travel brief so it strictly matches the user's real request.

    Rules:
    1. Return JSON only.
    2. Do not use markdown.
    3. Keep the same JSON schema as the input brief.
    4. If the user explicitly requested trip length, `days` must exactly match the user's request.
    5. Never expand a short trip into a longer one. For example, if the user says 3 days, do not output 14 days.
    6. `userIntentSummary` must be a clean natural-language summary, not raw conversation transcript.
    7. `userIntentSummary` must not contain role tags like `[USER]`, `[ASSISTANT]`, `USER:`, `ASSISTANT:`.
    8. `userIntentSummary` must not contain markdown markers like `#`, `*`, `**`, backticks, or code fences.
    9. Preserve valid extracted constraints such as budget, traveler count, transport preferences, and style tags.

    Conversation:
    {content}

    Draft brief:
    {brief}

    JSON schema:
    {
      "destination": "string",
      "origin": "string",
      "dateRange": "string",
      "days": 0,
      "travelerCount": 0,
      "budgetLevel": "string",
      "budgetText": "string",
      "travelStyleTags": ["string"],
      "transportPreferences": ["string"],
      "hardConstraints": ["string"],
      "userIntentSummary": "string"
    }
""".trimIndent()

internal val DEFAULT_TRAVEL_ITINERARY_PROMPT = """
    Generate a unified travel planning result source based on the brief and recent conversation.

    Rules:
    1. Return JSON only.
    2. Do not use markdown.
    3. Cover hotels, foods, activities, POIs, and itineraryDays in one response.
    4. If the brief is incomplete, fill gaps with reasonable best-effort assumptions.
    5. Use null for unknown coordinates.
    6. `category` values must strictly match the allowed values in the schema.
    7. Use `commercialHotels` and `commercialActivities` when they contain rating or price details.
    8. If `origin` or `intercityTransportHints` are present, reflect them in arrival/departure planning.
    9. Prefer concrete, executable itinerary items over generic advice.
    10. When `facts` contains real hotels / foods / activities / candidatePois, use those real places in `itineraryDays.items` whenever they fit.
    11. For hotel / food / activity / sightseeing items, fill `poiRefId` with an id that already exists in `facts` or in the returned `pois`.
    12. Do not invent unreachable `poiRefId` values.
    13. Ensure `pois` covers every `poiRefId` referenced by `itineraryDays.items`.
    14. On most main travel days, if real candidates are available, include at least one concrete activity/sightseeing place and one concrete food place instead of only generic wording.
    15. If a day is sparse, still return a minimal actionable schedule with specific place names when candidates exist.
    16. If `brief.days` is provided and > 0, `itineraryDays.length` must exactly equal `brief.days`.
    17. `dayIndex` must be continuous from 1 to `brief.days`.
    18. Titles, descriptions, and itinerary text must not contain raw role tags such as `[USER]` or `[ASSISTANT]`.

    Brief:
    {brief}

    Current plan:
    {currentPlan}

    Travel facts:
    {facts}

    Recent conversation:
    {content}

    JSON schema:
    {
      "hotels": [
        {
          "id": "string",
          "category": "hotel",
          "title": "string",
          "subtitle": "string",
          "tags": ["string"],
          "reason": "string",
          "priceHint": "string",
          "ratingText": "string",
          "area": "string",
          "inventoryHint": "string",
          "bookingUrl": "string",
          "source": "string",
          "lat": 0.0,
          "lon": 0.0,
          "sourceMessageIds": ["string"]
        }
      ],
      "foods": [],
      "activities": [],
      "pois": [
        {
          "id": "string",
          "name": "string",
          "category": "string",
          "lat": 0.0,
          "lon": 0.0,
          "address": "string",
          "linkedRecommendationId": "string",
          "linkedItineraryItemId": "string"
        }
      ],
      "itineraryDays": [
        {
          "dayIndex": 1,
          "title": "string",
          "dateText": "string",
          "weatherHint": "string",
          "items": [
            {
              "id": "string",
              "timeSlot": "string",
              "title": "string",
              "description": "string",
              "category": "transport|sightseeing|food|hotel|activity|free_time|shopping|other",
              "poiRefId": "string",
              "estimatedCost": "string",
              "transportHint": "string"
            }
          ]
        }
      ]
    }
""".trimIndent()

internal val DEFAULT_TRAVEL_ITINERARY_AUDITOR_PROMPT = """
    You are the itinerary-auditor agent.

    Task:
    Repair the generated travel payload so it strictly obeys the brief and is clean for app rendering.

    Rules:
    1. Return JSON only.
    2. Do not use markdown.
    3. Keep the same JSON schema as the generated payload.
    4. If `brief.days` is provided and > 0, `itineraryDays.length` must exactly equal `brief.days`.
    5. `dayIndex` must be continuous from 1.
    6. Remove raw role tags like `[USER]`, `[ASSISTANT]`, `USER:`, `ASSISTANT:` from all text fields.
    7. Remove markdown markers such as `#`, `*`, `**`, backticks, and code fences from all user-facing text fields.
    8. Keep useful concrete POIs / hotels / foods / activities whenever possible.
    9. If there are too many days, compress or drop the least important overflow days.
    10. If there are too few days, add minimal actionable days rather than leaving the count short.

    Brief:
    {brief}

    Travel facts:
    {facts}

    Recent conversation:
    {content}

    Generated payload:
    {payload}

    JSON schema:
    {
      "hotels": [],
      "foods": [],
      "activities": [],
      "pois": [],
      "itineraryDays": []
    }
""".trimIndent()

internal fun buildTravelItineraryPrompt(
    brief: String,
    currentPlan: String,
    facts: String,
    content: String,
): String {
    return DEFAULT_TRAVEL_ITINERARY_PROMPT
        .replace("{brief}", brief)
        .replace("{currentPlan}", currentPlan)
        .replace("{facts}", facts)
        .replace("{content}", content)
}

internal fun buildTravelBriefRefinerPrompt(
    brief: String,
    content: String,
): String {
    return DEFAULT_TRAVEL_BRIEF_REFINER_PROMPT
        .replace("{brief}", brief)
        .replace("{content}", content)
}

internal fun buildTravelItineraryAuditorPrompt(
    brief: String,
    facts: String,
    content: String,
    payload: String,
): String {
    return DEFAULT_TRAVEL_ITINERARY_AUDITOR_PROMPT
        .replace("{brief}", brief)
        .replace("{facts}", facts)
        .replace("{content}", content)
        .replace("{payload}", payload)
}
