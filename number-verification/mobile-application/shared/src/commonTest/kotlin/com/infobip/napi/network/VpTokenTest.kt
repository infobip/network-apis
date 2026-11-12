package com.infobip.napi.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VpTokenTest {

    @Test
    fun extract_should_ReadFirstEntry_when_CredentialJsonHasVpToken() {
        val json = """
            {
              "protocol": "openid4vp-v1-unsigned",
              "data": { "vp_token": { "credential-provider.example": ["eyJhbG.YSGpScQ~eyJhbGc.CD9zg"] } }
            }
        """.trimIndent()

        assertEquals("eyJhbG.YSGpScQ~eyJhbGc.CD9zg", VpToken.extract(json))
    }

    @Test
    fun extract_should_ReturnNull_when_DataIsMissing() {
        assertNull(VpToken.extract("""{"protocol":"openid4vp-v1-unsigned"}"""))
    }

    @Test
    fun extract_should_ReturnNull_when_VpTokenIsMissing() {
        assertNull(VpToken.extract("""{"data":{}}"""))
    }

    @Test
    fun extract_should_ReturnNull_when_EntryIsEmpty() {
        assertNull(VpToken.extract("""{"data":{"vp_token":{"credential-provider.example":[]}}}"""))
    }

    @Test
    fun extract_should_ReturnNull_when_NotJson() {
        assertNull(VpToken.extract("not json"))
    }

    @Test
    fun hasError_should_ReturnTrue_when_ErrorAtTopLevel() {
        assertTrue(VpToken.hasError("""{"error":"server_error","error_description":"FAILED_TO_GET_TEMP_TOKEN"}"""))
    }

    @Test
    fun hasError_should_ReturnTrue_when_ErrorUnderData() {
        assertTrue(VpToken.hasError("""{"protocol":"openid4vp-v1-unsigned","data":{"error":"access_denied"}}"""))
    }

    @Test
    fun hasError_should_ReturnTrue_when_OnlyDescription() {
        assertTrue(VpToken.hasError("""{"error_description":"FAILED_TO_GET_TEMP_TOKEN"}"""))
    }

    @Test
    fun hasError_should_ReturnFalse_when_CredentialJsonHasVpToken() {
        assertFalse(VpToken.hasError("""{"data":{"vp_token":{"credential-provider.example":["eyJhbG.YSGpScQ~eyJhbGc.CD9zg"]}}}"""))
    }

    @Test
    fun hasError_should_ReturnFalse_when_ErrorIsBlankOrNull() {
        assertFalse(VpToken.hasError("""{"error":"","error_description":null}"""))
    }

    @Test
    fun hasError_should_ReturnFalse_when_NotJson() {
        assertFalse(VpToken.hasError("not json"))
    }
}
