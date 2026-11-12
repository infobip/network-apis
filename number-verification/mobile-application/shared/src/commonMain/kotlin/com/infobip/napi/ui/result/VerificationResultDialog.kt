package com.infobip.napi.ui.result

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.infobip.napi.resources.Res
import com.infobip.napi.resources.check_circle
import com.infobip.napi.model.domain.VerificationOutcome
import com.infobip.napi.ui.components.CopyableTextField
import com.infobip.napi.ui.components.InfoRow
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationResultDialog(
    outcome: VerificationOutcome,
    debug: Boolean,
    onFinish: () -> Unit,
    onVerifyPhoneNumber: (() -> Unit)? = null,
) {
    val title = if (outcome is VerificationOutcome.Error) "Error" else "Result"

    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(title) }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                ResultIcon(outcome)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = resultTitle(outcome),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                if (outcome.msisdn.isNotBlank() && outcome !is VerificationOutcome.PhoneNumber) {
                    InfoRow("MSISDN", outcome.msisdn)
                }
                if (debug) {
                    outcome.deviceIp?.let { InfoRow("Device IP", it) }
                    outcome.devicePort?.let { InfoRow("Device Port", it.toString()) }
                    outcome.token?.let {
                        Spacer(Modifier.height(16.dp))
                        CopyableTextField(label = "Request ID", value = it)
                    }
                    outcome.nonce?.let {
                        Spacer(Modifier.height(16.dp))
                        CopyableTextField(label = "Nonce", value = it)
                    }
                    if (outcome is VerificationOutcome.Error) {
                        outcome.additionalError?.let {
                            Spacer(Modifier.height(16.dp))
                            CopyableTextField(
                                label = "Error details",
                                value = it,
                                singleLine = false,
                                maxLines = ERROR_DETAILS_MAX_LINES,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("FINISH")
                }
                if (onVerifyPhoneNumber != null) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onVerifyPhoneNumber,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("VERIFY PHONE NUMBER")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private const val ERROR_DETAILS_MAX_LINES = 12

/** Yellow is not part of the Material scheme, so the invalid state carries its own colour. */
private val InvalidAmber = Color(0xFFF5A623)

/**
 * The Material "error" icon: a filled disc with the exclamation punched out.
 * material-icons-core does not ship it, so the official 24dp path is inlined.
 * https://fonts.google.com/icons?selected=Material+Icons:error
 */
private val ErrorCircle: ImageVector by lazy {
    ImageVector.Builder(
        name = "ErrorCircle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Outer disc.
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveTo(6.48f, 22f, 12f, 22f)
            reflectiveCurveTo(22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            // Dot.
            moveTo(13f, 17f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            // Stem.
            moveTo(13f, 13f)
            horizontalLineToRelative(-2f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            close()
        }
    }.build()
}

@Composable
private fun ResultIcon(outcome: VerificationOutcome) {
    val size = Modifier.size(120.dp)
    when (outcome) {
        // The check mark keeps the existing artwork.
        is VerificationOutcome.Valid, is VerificationOutcome.PhoneNumber -> Image(
            painter = painterResource(Res.drawable.check_circle),
            contentDescription = null,
            modifier = size,
        )

        // Drawn rather than an asset so it matches the check mark's filled-circle style.
        is VerificationOutcome.Invalid -> FilledCircleIcon(
            icon = Icons.Filled.Close,
            circleColor = InvalidAmber,
            modifier = size,
        )

        // The exclamation is punched out of the icon, so a white circle behind it shows
        // through as a white mark on the red disc.
        is VerificationOutcome.Error -> FilledCircle(
            color = Color.White,
            modifier = size,
        ) {
            Icon(
                imageVector = ErrorCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Centres [content] on a filled circle, matching the check mark artwork. */
@Composable
private fun FilledCircle(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** A white glyph centred on a filled circle. */
@Composable
private fun FilledCircleIcon(
    icon: ImageVector,
    circleColor: Color,
    modifier: Modifier = Modifier,
) {
    FilledCircle(color = circleColor, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            // The glyph sits inside the circle rather than touching its edge.
            modifier = Modifier.fillMaxSize(0.6f),
        )
    }
}

private fun resultTitle(outcome: VerificationOutcome): String = when (outcome) {
    is VerificationOutcome.Valid -> "Device phone verified: true"
    is VerificationOutcome.Invalid -> "Device phone verified: false"
    is VerificationOutcome.PhoneNumber -> "Phone Number: ${outcome.msisdn}"
    is VerificationOutcome.Error -> outcome.message
}
