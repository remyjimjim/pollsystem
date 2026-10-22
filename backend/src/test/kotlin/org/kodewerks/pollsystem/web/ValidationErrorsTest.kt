package org.kodewerks.pollsystem.web

import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** Validation errors name the fields in plain words (the sign-up form shows the message as is). */
@AutoConfigureMockMvc
class ValidationErrorsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    private fun register(json: String) = mockMvc.post("/api/auth/register-checkout") {
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    @Test
    fun `a zipcode with a trailing space is reported as the zipcode`() {
        register("""{"email":"pat@example.test","phone":"(401) 992-4938","zipcode":"98264 ","ageConfirmed":true}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Please check your zipcode (5 digits).") }
            jsonPath("$.path") { value("/api/auth/register-checkout") }
        }
    }

    @Test
    fun `several bad fields are always listed in the form's order, whatever order the validator reports them`() {
        // The validator's order varies between runs (it failed CI on 3 runs), so check it many times.
        repeat(25) {
            register("""{"zipcode":"9826","phone":"call me","email":"not-an-email","ageConfirmed":true}""").andExpect {
                jsonPath("$.message") { value("Please check your email address, phone number and zipcode (5 digits).") }
            }
        }
    }

    @Test
    fun `several bad fields are listed together`() {
        register("""{"email":"not-an-email","phone":"call me","zipcode":"98264","ageConfirmed":true}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Please check your email address and phone number.") }
        }
    }

    @Test
    fun `signing up without confirming you're 13 or older is refused`() {
        register("""{"email":"pat@example.test","phone":"(401) 992-4938","zipcode":"98264"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Please check your confirmation that you're 13 or older.") }
        }
        register("""{"email":"pat@example.test","phone":"(401) 992-4938","zipcode":"98264","ageConfirmed":false}""").andExpect {
            status { isBadRequest() }
        }
    }
}
