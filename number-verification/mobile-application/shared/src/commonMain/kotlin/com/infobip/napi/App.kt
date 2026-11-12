package com.infobip.napi

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.network.CellularController
import com.infobip.napi.network.SimCredentialController
import com.infobip.napi.resources.Res
import com.infobip.napi.resources.logo
import com.infobip.napi.settings.SettingsState
import com.infobip.napi.ui.components.LoadingOverlay
import com.infobip.napi.ui.home.HomeScreen
import com.infobip.napi.ui.navigation.Destination
import com.infobip.napi.ui.navigation.Operation
import com.infobip.napi.ui.phonenumber.PhoneNumberScreen
import com.infobip.napi.ui.phonenumber.PhoneNumberViewModel
import com.infobip.napi.ui.result.VerificationResultDialog
import com.infobip.napi.ui.settings.SettingsScreen
import com.infobip.napi.ui.settings.SettingsViewModel
import com.infobip.napi.ui.theme.AppTheme
import com.infobip.napi.ui.verify.VerificationScreen
import com.infobip.napi.ui.verify.VerificationUiState
import com.infobip.napi.ui.verify.VerificationViewModel
import androidx.compose.runtime.collectAsState
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val ExpandedWidthBreakpoint = 600.dp
private val ExpandedContentMaxWidth = 840.dp

@Composable
fun App() {
    AppTheme {
        val settingsViewModel = koinViewModel<SettingsViewModel>()
        val verificationViewModel = koinViewModel<VerificationViewModel>()
        val phoneNumberViewModel = koinViewModel<PhoneNumberViewModel>()
        val cellularController = koinInject<CellularController>()
        val simCredentialController = koinInject<SimCredentialController>()

        val settings by settingsViewModel.state.collectAsState()
        val verificationState by verificationViewModel.state.collectAsState()
        val phoneNumberState by phoneNumberViewModel.state.collectAsState()

        var current by remember { mutableStateOf(Destination.HOME) }
        var openOperation by remember { mutableStateOf<Operation?>(null) }
        // Filled in when "Verify phone number" opens number verification from a phone number result.
        var prefilledMsisdn by remember { mutableStateOf("") }
        val focusManager = LocalFocusManager.current
        val navigate: (Destination) -> Unit = { destination ->
            focusManager.clearFocus()
            current = destination
            openOperation = null
            prefilledMsisdn = ""
        }
        val openOperationScreen: (Operation) -> Unit = { operation ->
            focusManager.clearFocus()
            openOperation = operation
            prefilledMsisdn = ""
        }
        val closeOperation = {
            focusManager.clearFocus()
            openOperation = null
            prefilledMsisdn = ""
        }

        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = openOperation != null,
            onBackCompleted = closeOperation,
        )

        val onBack = if (openOperation != null) closeOperation else null

        val content: @Composable (Modifier) -> Unit = { contentModifier ->
            AppDestinationContent(
                current = current,
                openOperation = openOperation,
                settings = settings,
                verificationState = verificationState,
                phoneNumberState = phoneNumberState,
                supportsAutoCellular = cellularController.supportsAutoCellular,
                supportsSimCredential = simCredentialController.supportsSimCredential,
                prefilledMsisdn = prefilledMsisdn,
                settingsViewModel = settingsViewModel,
                verificationViewModel = verificationViewModel,
                phoneNumberViewModel = phoneNumberViewModel,
                onNavigate = navigate,
                onOpenOperation = openOperationScreen,
                modifier = contentModifier,
            )
        }

        BoxWithConstraints {
            if (maxWidth >= ExpandedWidthBreakpoint) {
                ExpandedLayout(current = current, onNavigate = navigate, onBack = onBack, content = content)
            } else {
                CompactLayout(current = current, onNavigate = navigate, onBack = onBack, content = content)
            }
        }

        val verificationOutcome = resultOutcome(verificationState)
        val phoneNumberOutcome = resultOutcome(phoneNumberState)
        if (verificationOutcome != null) {
            VerificationResultDialog(
                outcome = verificationOutcome,
                debug = settings.debug,
                onFinish = verificationViewModel::consumeResult,
            )
        } else if (phoneNumberOutcome != null) {
            VerificationResultDialog(
                outcome = phoneNumberOutcome,
                debug = settings.debug,
                onFinish = phoneNumberViewModel::consumeResult,
                onVerifyPhoneNumber = (phoneNumberOutcome as? VerificationOutcome.PhoneNumber)?.let { result ->
                    {
                        focusManager.clearFocus()
                        phoneNumberViewModel.consumeResult()
                        prefilledMsisdn = result.msisdn
                        openOperation = Operation.VERIFICATION
                    }
                },
            )
        }

        LoadingOverlay(
            visible = verificationState is VerificationUiState.Submitting ||
                phoneNumberState is VerificationUiState.Submitting,
        )
    }
}

private fun resultOutcome(state: VerificationUiState): VerificationOutcome? = when (state) {
    is VerificationUiState.Success -> state.outcome
    is VerificationUiState.Failure -> state.outcome
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(onBack: (() -> Unit)?) {
    TopAppBar(
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("Network APIs")
            }
        },
    )
}

@Composable
private fun CompactLayout(
    current: Destination,
    onNavigate: (Destination) -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(onBack) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = destination == current
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onNavigate(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun ExpandedLayout(
    current: Destination,
    onNavigate: (Destination) -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(onBack) },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavigationRail {
                Destination.entries.forEach { destination ->
                    val selected = destination == current
                    NavigationRailItem(
                        selected = selected,
                        onClick = { onNavigate(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                content(Modifier.widthIn(max = ExpandedContentMaxWidth).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun AppDestinationContent(
    current: Destination,
    openOperation: Operation?,
    settings: SettingsState,
    verificationState: VerificationUiState,
    phoneNumberState: VerificationUiState,
    supportsAutoCellular: Boolean,
    supportsSimCredential: Boolean,
    prefilledMsisdn: String,
    settingsViewModel: SettingsViewModel,
    verificationViewModel: VerificationViewModel,
    phoneNumberViewModel: PhoneNumberViewModel,
    onNavigate: (Destination) -> Unit,
    onOpenOperation: (Operation) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (current) {
        Destination.HOME -> when (openOperation) {
            null -> HomeScreen(
                settings = settings,
                supportsSimCredential = supportsSimCredential,
                onOpenOperation = onOpenOperation,
                onGoToSettings = { onNavigate(Destination.SETTINGS) },
                modifier = modifier,
            )

            Operation.VERIFICATION -> VerificationScreen(
                settings = settings,
                uiState = verificationState,
                supportsAutoCellular = supportsAutoCellular,
                supportsSimCredential = supportsSimCredential,
                initialMsisdn = prefilledMsisdn,
                onSubmit = { msisdn, method ->
                    verificationViewModel.submit(msisdn, method, settings)
                },
                onMsisdnChange = verificationViewModel::clearMsisdnError,
                onOpenSettings = { onNavigate(Destination.SETTINGS) },
                modifier = modifier,
            )

            Operation.GET_PHONE_NUMBER -> PhoneNumberScreen(
                settings = settings,
                uiState = phoneNumberState,
                onSubmit = { phoneNumberViewModel.submit(settings) },
                onOpenSettings = { onNavigate(Destination.SETTINGS) },
                modifier = modifier,
            )
        }

        Destination.SETTINGS -> {
            val loginState by settingsViewModel.loginState.collectAsState()
            SettingsScreen(
                settings = settings,
                loginState = loginState,
                onUpdateApiKey = settingsViewModel::updateApiKey,
                onLogin = settingsViewModel::login,
                onResetLoginState = settingsViewModel::resetLoginState,
                onUpdateDebug = settingsViewModel::updateDebug,
                modifier = modifier,
            )
        }
    }
}
