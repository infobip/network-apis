package com.infobip.napi.ui.phonenumber

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.settings.SettingsState
import com.infobip.napi.ui.verify.FakeAuthorizationProvider
import com.infobip.napi.ui.verify.FakeSimBasedRepository
import com.infobip.napi.ui.verify.VerificationUiState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneNumberViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settings(apiKey: String? = "key-1") = SettingsState(apiKey = apiKey)

    private fun outcome() = VerificationOutcome.PhoneNumber(msisdn = "+38591234567", token = "request-1")

    @Test
    fun submit_should_SetIdleWithError_when_ApiKeyMissing() {
        val repository = FakeSimBasedRepository(outcome())
        val vm = PhoneNumberViewModel(repository, FakeAuthorizationProvider())

        vm.submit(settings(apiKey = null))

        val state = assertIs<VerificationUiState.Idle>(vm.state.value)
        assertEquals("Authorization not configured", state.msisdnError)
        assertEquals(0, repository.getPhoneNumberCount)
    }

    @Test
    fun submit_should_TransitionToFailure_when_TokenRefreshFails() = runTest(dispatcher) {
        val repository = FakeSimBasedRepository(outcome())
        val vm = PhoneNumberViewModel(repository, FakeAuthorizationProvider(failure = IllegalStateException("boom")))

        vm.submit(settings())
        advanceUntilIdle()

        val failure = assertIs<VerificationUiState.Failure>(vm.state.value)
        assertEquals("Could not refresh the access token", failure.outcome.message)
        assertEquals("boom", failure.outcome.additionalError)
        assertEquals(0, repository.getPhoneNumberCount)
    }

    @Test
    fun submit_should_TransitionToSuccess_when_PhoneNumberReturned() = runTest(dispatcher) {
        val repository = FakeSimBasedRepository(outcome())
        val vm = PhoneNumberViewModel(repository, FakeAuthorizationProvider())

        vm.submit(settings())
        assertIs<VerificationUiState.Submitting>(vm.state.value)

        advanceUntilIdle()

        val success = assertIs<VerificationUiState.Success>(vm.state.value)
        assertIs<VerificationOutcome.PhoneNumber>(success.outcome)
        assertEquals(1, repository.getPhoneNumberCount)
        assertEquals(AppConfig.BACKEND_DOMAIN, repository.lastDomain)
        assertEquals("Bearer key-1", repository.lastAuthorization)
    }

    @Test
    fun submit_should_TransitionToFailure_when_ResultIsError() = runTest(dispatcher) {
        val error = VerificationOutcome.Error(msisdn = "", message = "Get phone number failed")
        val vm = PhoneNumberViewModel(FakeSimBasedRepository(error), FakeAuthorizationProvider())

        vm.submit(settings())
        advanceUntilIdle()

        val failure = assertIs<VerificationUiState.Failure>(vm.state.value)
        assertEquals("Get phone number failed", failure.outcome.message)
    }

    @Test
    fun consumeResult_should_ResetToIdle_when_Called() = runTest(dispatcher) {
        val vm = PhoneNumberViewModel(FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit(settings())
        advanceUntilIdle()
        assertIs<VerificationUiState.Success>(vm.state.value)

        vm.consumeResult()

        assertIs<VerificationUiState.Idle>(vm.state.value)
    }
}
