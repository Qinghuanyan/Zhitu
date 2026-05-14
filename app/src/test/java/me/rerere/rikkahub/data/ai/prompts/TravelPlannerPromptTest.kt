package me.rerere.rikkahub.data.ai.prompts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelPlannerPromptTest {
    @Test
    fun buildTravelItineraryPrompt_includesFactsAndConversation() {
        val prompt = buildTravelItineraryPrompt(
            brief = """{"destination":"上海","days":3}""",
            facts = """{"dailyWeather":[{"date":"2026-05-10","summary":"多云 21~28°C"}]}""",
            content = "用户想要亲子慢节奏出行",
        )

        assertTrue(prompt.contains("Travel facts:"))
        assertTrue(prompt.contains("dailyWeather"))
        assertTrue(prompt.contains("用户想要亲子慢节奏出行"))
        assertFalse(prompt.contains("{facts}"))
        assertFalse(prompt.contains("{content}"))
    }
}
