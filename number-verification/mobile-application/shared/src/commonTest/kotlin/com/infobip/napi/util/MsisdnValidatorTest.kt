package com.infobip.napi.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MsisdnValidatorTest {

    @Test
    fun validate_should_ReturnRequired_when_ValueIsNull() {
        assertEquals("Required", MsisdnValidator.validate(null))
    }

    @Test
    fun validate_should_ReturnRequired_when_ValueIsEmpty() {
        assertEquals("Required", MsisdnValidator.validate(""))
    }

    @Test
    fun validate_should_ReturnNull_when_ValueIsValidInternationalNumber() {
        listOf(
            "1234567",
            "+1234567",
            "385911234567",
            "+385911234567",
            "123456789012345",
            "+123456789012345",
        ).forEach { assertNull(MsisdnValidator.validate(it), "expected $it to be valid") }
    }

    @Test
    fun validate_should_ReturnInvalidFormat_when_ValueIsNotValidInternationalNumber() {
        listOf(
            "123456",
            "+123456",
            "1234567890123456",
            "+1234567890123456",
            "0123456789",
            "+0123456789",
            "00385911234567",
            "12345abc90",
            "385 91 123 4567",
            "385-91-1234567",
            "++385911234567",
            "385911234567+",
            "+",
        ).forEach { assertEquals("Invalid MSISDN format", MsisdnValidator.validate(it), "expected $it to be invalid") }
    }
}
