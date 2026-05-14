package me.rerere.rikkahub.data.api

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TravelApiDtoParsingTest {
    @Test
    fun amapInputTipsResponse_parsesSuggestions() {
        val payload = """
            {
              "status": "1",
              "info": "OK",
              "count": "1",
              "tips": [
                {
                  "id": "B0FFG12345",
                  "name": "外滩",
                  "district": "黄浦区",
                  "address": "中山东一路",
                  "adcode": "310101",
                  "location": "121.490317,31.241701",
                  "city": ["上海市"]
                }
              ]
            }
        """.trimIndent()

        val response = JsonInstant.decodeFromString<AmapInputTipsResponse>(payload)

        assertEquals("1", response.status)
        assertEquals(1, response.tips.size)
        assertEquals("外滩", response.tips.first().name)
        assertEquals("121.490317,31.241701", response.tips.first().location)
    }

    @Test
    fun amapPoiSearchResponse_parsesPois() {
        val payload = """
            {
              "status": "1",
              "info": "OK",
              "count": "1",
              "pois": [
                {
                  "id": "B00155",
                  "name": "上海迪士尼乐园",
                  "address": "川沙新镇黄赵路310号",
                  "adname": "浦东新区",
                  "cityname": "上海市",
                  "pname": "上海市",
                  "location": "121.657410,31.144188",
                  "type": "风景名胜;游乐园",
                  "typecode": "110202"
                }
              ]
            }
        """.trimIndent()

        val response = JsonInstant.decodeFromString<AmapPoiSearchResponse>(payload)

        assertEquals("上海迪士尼乐园", response.pois.first().name)
        assertEquals("浦东新区", response.pois.first().district)
        assertEquals("风景名胜;游乐园", response.pois.first().type)
    }

    @Test
    fun qweatherDailyResponse_parsesForecast() {
        val payload = """
            {
              "code": "200",
              "daily": [
                {
                  "fxDate": "2026-05-10",
                  "tempMax": "28",
                  "tempMin": "21",
                  "textDay": "多云",
                  "textNight": "晴"
                }
              ]
            }
        """.trimIndent()

        val response = JsonInstant.decodeFromString<QWeatherDailyResponse>(payload)

        assertEquals("200", response.code)
        assertEquals(1, response.daily.size)
        assertEquals("2026-05-10", response.daily.first().fxDate)
        assertEquals("多云", response.daily.first().textDay)
        assertNotNull(response.daily.first().tempMax)
    }
}
