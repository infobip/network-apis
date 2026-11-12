package com.infobip.napi.ui.verify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.infobip.napi.config.AppConfig
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.model.domain.VerificationType
import com.infobip.napi.model.domain.VerifyMethod
import com.infobip.napi.settings.SettingsState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelTest {

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

    private fun outcome() = VerificationOutcome.Valid(msisdn = "38591234567", token = "tok-1")

    @Test
    fun submit_should_SetIdleWithError_when_MsisdnInvalid() {
        val repository = FakeVerificationRepository(outcome())
        val vm = VerificationViewModel(repository, FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit("invalid", VerifyMethod.SMV, settings())

        val state = assertIs<VerificationUiState.Idle>(vm.state.value)
        assertEquals("Invalid MSISDN format", state.msisdnError)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun clearMsisdnError_should_ResetToIdleWithoutError_when_ErrorShown() {
        val vm = VerificationViewModel(FakeVerificationRepository(outcome()), FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())
        vm.submit("invalid", VerifyMethod.SMV, settings())

        vm.clearMsisdnError()

        assertEquals(VerificationUiState.Idle(), vm.state.value)
    }

    @Test
    fun clearMsisdnError_should_KeepState_when_NotShowingError() {
        val vm = VerificationViewModel(FakeVerificationRepository(outcome()), FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())
        vm.submit("38591234567", VerifyMethod.SMV, settings())

        vm.clearMsisdnError()

        assertEquals(VerificationUiState.Submitting, vm.state.value)
    }

    @Test
    fun submit_should_SetIdleWithError_when_ApiKeyMissing() {
        val repository = FakeVerificationRepository(outcome())
        val vm = VerificationViewModel(repository, FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit("38591234567", VerifyMethod.SMV, settings(apiKey = null))

        val state = assertIs<VerificationUiState.Idle>(vm.state.value)
        assertEquals("Authorization not configured", state.msisdnError)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun submit_should_UseConfiguredBackendDomain_when_Verifying() = runTest(dispatcher) {
        val repository = FakeVerificationRepository(outcome())
        val vm = VerificationViewModel(repository, FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit("38591234567", VerifyMethod.SMV, settings())
        advanceUntilIdle()

        assertEquals(AppConfig.BACKEND_DOMAIN, repository.lastDomain)
        assertEquals("Bearer key-1", repository.lastAuthorization)
    }

    @Test
    fun submit_should_TransitionToSuccess_when_ResultIsValid() = runTest(dispatcher) {
        val repository = FakeVerificationRepository(outcome())
        val vm = VerificationViewModel(repository, FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit("38591234567", VerifyMethod.SMV, settings())
        assertIs<VerificationUiState.Submitting>(vm.state.value)

        advanceUntilIdle()

        val success = assertIs<VerificationUiState.Success>(vm.state.value)
        assertIs<VerificationOutcome.Valid>(success.outcome)
        assertEquals(1, repository.callCount)
        assertEquals(VerificationType.SMV, repository.lastType)
    }

    @Test
    fun submit_should_UseCamaraType_when_MethodIsCamara() = runTest(dispatcher) {
        val repository = FakeVerificationRepository(outcome())
        val vm = VerificationViewModel(repository, FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit("38591234567", VerifyMethod.CAMARA, settings())
        advanceUntilIdle()

        assertEquals(VerificationType.CAMARA, repository.lastType)
    }

    @Test
    fun submit_should_TransitionToFailure_when_ResultIsError() = runTest(dispatcher) {
        val error = VerificationOutcome.Error(msisdn = "38591234567", message = "bad key")
        val vm = VerificationViewModel(FakeVerificationRepository(error), FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit("38591234567", VerifyMethod.SMV, settings())
        advanceUntilIdle()

        val failure = assertIs<VerificationUiState.Failure>(vm.state.value)
        assertEquals("bad key", failure.outcome.message)
    }

    @Test
    fun consumeResult_should_ResetToIdle_when_Called() = runTest(dispatcher) {
        val vm = VerificationViewModel(FakeVerificationRepository(outcome()), FakeSimBasedRepository(outcome()), FakeAuthorizationProvider())

        vm.submit("38591234567", VerifyMethod.SMV, settings())
        advanceUntilIdle()
        assertIs<VerificationUiState.Success>(vm.state.value)

        vm.consumeResult()

        assertIs<VerificationUiState.Idle>(vm.state.value)
    }

    @Test
    fun submit_should_UseSimBasedRepository_when_MethodIsSimBased() = runTest(dispatcher) {
        val repository = FakeVerificationRepository(outcome())
        val simBasedRepository = FakeSimBasedRepository(outcome())
        val vm = VerificationViewModel(repository, simBasedRepository, FakeAuthorizationProvider())

        vm.submit("38591234567", VerifyMethod.SIM_BASED, settings())
        advanceUntilIdle()

        assertIs<VerificationUiState.Success>(vm.state.value)
        assertEquals(0, repository.callCount)
        assertEquals(1, simBasedRepository.verifyCount)
        assertEquals("38591234567", simBasedRepository.lastMsisdn)
        assertEquals(AppConfig.BACKEND_DOMAIN, simBasedRepository.lastDomain)
        assertEquals("Bearer key-1", simBasedRepository.lastAuthorization)
    }

    @Test
    fun submit_should_TransitionToFailure_when_SimBasedResultIsError() = runTest(dispatcher) {
        val error = VerificationOutcome.Error(msisdn = "38591234567", message = "No supported SIM found.")
        val vm = VerificationViewModel(
            FakeVerificationRepository(outcome()),
            FakeSimBasedRepository(error),
            FakeAuthorizationProvider(),
        )

        vm.submit("38591234567", VerifyMethod.SIM_BASED, settings())
        advanceUntilIdle()

        val failure = assertIs<VerificationUiState.Failure>(vm.state.value)
        assertEquals("No supported SIM found.", failure.outcome.message)
    }

    @Test
    fun submit_should_SetIdleWithError_when_SimBasedMsisdnInvalid() {
        val simBasedRepository = FakeSimBasedRepository(outcome())
        val vm = VerificationViewModel(FakeVerificationRepository(outcome()), simBasedRepository, FakeAuthorizationProvider())

        vm.submit("invalid", VerifyMethod.SIM_BASED, settings())

        assertIs<VerificationUiState.Idle>(vm.state.value)
        assertEquals(0, simBasedRepository.verifyCount)
    }
}
