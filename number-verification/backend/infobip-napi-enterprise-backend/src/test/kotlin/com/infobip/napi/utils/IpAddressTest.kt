package com.infobip.napi.utils

import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
class IpAddressTest {

    @Mock
    private lateinit var request: HttpServletRequest

    @ParameterizedTest
    @MethodSource("forwardedForValues")
    fun resolveIpAddress_should_ExtractAddressAndPort_when_ForwardedForValueProvided(
        forwardedFor: String,
        expected: IpAddress
    ) {
        assertThat(resolveIpAddress(forwardedFor)).isEqualTo(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["[2001:db8::1:5678", "1.2.3.4,", " , "])
    fun resolveIpAddress_should_ThrowException_when_ForwardedForValueIsMalformed(forwardedFor: String) {
        assertThatThrownBy { resolveIpAddress(forwardedFor) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun resolveIpAddress_should_UseLastForwardedForAddress_when_UsingProxy() {
        `when`(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 1.2.3.4:5678")

        val result = resolveIpAddress(request, usingProxy = true)

        assertThat(result).isEqualTo(IpAddress("1.2.3.4", 5678))
        verify(request, never()).remoteAddr
    }

    @Test
    fun resolveIpAddress_should_FallBackToRemoteAddress_when_UsingProxyAndForwardedForHeaderIsMissing() {
        `when`(request.getHeader("X-Forwarded-For")).thenReturn(null)
        `when`(request.remoteAddr).thenReturn("9.8.7.6")
        `when`(request.remotePort).thenReturn(4321)

        val result = resolveIpAddress(request, usingProxy = true)

        assertThat(result).isEqualTo(IpAddress("9.8.7.6", 4321))
    }

    @Test
    fun resolveIpAddress_should_FallBackToRemoteAddress_when_UsingProxyAndForwardedForHeaderIsMalformed() {
        `when`(request.getHeader("X-Forwarded-For")).thenReturn("[2001:db8::1:5678")
        `when`(request.remoteAddr).thenReturn("9.8.7.6")
        `when`(request.remotePort).thenReturn(4321)

        val result = resolveIpAddress(request, usingProxy = true)

        assertThat(result).isEqualTo(IpAddress("9.8.7.6", 4321))
    }

    @Test
    fun resolveIpAddress_should_UseRemoteAddressAndIgnoreHeader_when_NotUsingProxy() {
        `when`(request.remoteAddr).thenReturn("9.8.7.6")
        `when`(request.remotePort).thenReturn(4321)

        val result = resolveIpAddress(request, usingProxy = false)

        assertThat(result).isEqualTo(IpAddress("9.8.7.6", 4321))
        verify(request, never()).getHeader("X-Forwarded-For")
    }

    companion object {
        @JvmStatic
        fun forwardedForValues(): Stream<Arguments> = Stream.of(
            Arguments.of("1.2.3.4", IpAddress("1.2.3.4", 0)),
            Arguments.of("1.2.3.4:5678", IpAddress("1.2.3.4", 5678)),
            Arguments.of("1.2.3.4:5678, 10.0.0.1:80", IpAddress("10.0.0.1", 80)),
            Arguments.of(" 1.2.3.4 , 10.0.0.1 ", IpAddress("10.0.0.1", 0)),
            Arguments.of("[2001:db8::1]:5678", IpAddress("2001:db8::1", 5678)),
            Arguments.of("[2001:db8::1]", IpAddress("2001:db8::1", 0)),
            Arguments.of("2001:db8::1", IpAddress("2001:db8::1", 0)),
            Arguments.of("1.2.3.4:notaport", IpAddress("1.2.3.4", 0)),
            Arguments.of("1.2.3.4:70000", IpAddress("1.2.3.4", 0)),
        )
    }
}
