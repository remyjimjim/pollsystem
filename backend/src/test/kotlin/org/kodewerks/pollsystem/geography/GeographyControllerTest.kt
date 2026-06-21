package org.kodewerks.pollsystem.geography

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class GeographyControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `GET states is public and returns the seeded list`() {
        mockMvc.perform(get("/api/states"))
            .andExpect(status().isOk)
            // V2 seeds 50 states + DC = 51
            .andExpect(jsonPath("$.length()").value(51))
            .andExpect(jsonPath("$[?(@.initial=='CA')].name").value("California"))
    }

    @Test
    fun `GET counties returns counties for a state`() {
        // First, find California's id from the states list
        val statesJson = mockMvc.perform(get("/api/states"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val caId = Regex("""\{"id":(\d+),"name":"California"""")
            .find(statesJson)?.groupValues?.get(1)?.toLong()
            ?: error("California not found in seeded states")

        mockMvc.perform(get("/api/counties").param("state_id", caId.toString()))
            .andExpect(status().isOk)
            // V2 seeds 5 CA counties; V8 adds the rest from the Census list.
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(5)))
            .andExpect(jsonPath("$[?(@.name=='Los Angeles')]").exists())
    }

    @Test
    fun `POST zipcodes accepts a countyIds body and returns zipcodes for those counties`() {
        // Look up county ids for CA / Los Angeles
        val statesJson = mockMvc.perform(get("/api/states")).andReturn().response.contentAsString
        val caId = Regex("""\{"id":(\d+),"name":"California"""")
            .find(statesJson)?.groupValues?.get(1)?.toLong()!!

        val countiesJson = mockMvc.perform(get("/api/counties").param("state_id", caId.toString()))
            .andReturn().response.contentAsString
        val laId = Regex("""\{"id":(\d+),"stateId":\d+,"name":"Los Angeles"""")
            .find(countiesJson)?.groupValues?.get(1)?.toLong()!!

        mockMvc.perform(
            post("/api/zipcodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countyIds":[$laId]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[?(@.zipcode=='90001')]").exists())
    }

    @Test
    fun `POST zipcodes with an unknown countyId returns 200 and an empty array`() {
        // The service-side guard short-circuits when the resolved id list
        // matches no county_zips rows — caller sees an empty array, not a
        // 4xx, so the picker can disable downstream sections without an
        // error toast.
        mockMvc.perform(
            post("/api/zipcodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"countyIds":[999999]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `POST zipcodes with an empty body returns 200 and an empty array`() {
        // Match the GET-era contract: a request with no countyIds /
        // stateIds / prefix yields no zips rather than an error.
        mockMvc.perform(
            post("/api/zipcodes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
