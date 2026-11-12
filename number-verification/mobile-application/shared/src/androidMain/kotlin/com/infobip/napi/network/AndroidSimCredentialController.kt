package com.infobip.napi.network

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.credentials.CredentialManager
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.infobip.napi.di.AndroidContext
import com.infobip.napi.model.domain.SimCredentialError

class AndroidSimCredentialController(context: Context) : SimCredentialController {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val subscriptionManager =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

    override val supportsSimCredential: Boolean = true

    override suspend fun activeCarrierIds(): List<Int> {
        val carrierIds = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> emptyList()
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> listOf(telephonyManager.simCarrierId)
            else -> activeSubscriptionCarrierIds()
        }
        return carrierIds.filter { it != TelephonyManager.UNKNOWN_CARRIER_ID }.distinct()
    }

    @OptIn(ExperimentalDigitalCredentialApi::class)
    override suspend fun requestCredential(credentialRequest: String): String {
        val activity = AndroidContext.activity
            ?: throw SimCredentialException(SimCredentialError.NO_ACTIVITY)
        val request = GetCredentialRequest(listOf(GetDigitalCredentialOption(requestJson = credentialRequest)))
        val response = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw SimCredentialException(SimCredentialError.CANCELLED, credentialError = e.describe())
        } catch (e: NoCredentialException) {
            throw SimCredentialException(SimCredentialError.NO_CREDENTIAL, credentialError = e.describe())
        } catch (e: GetCredentialUnsupportedException) {
            throw SimCredentialException(SimCredentialError.UNSUPPORTED, credentialError = e.describe())
        } catch (e: GetCredentialException) {
            throw SimCredentialException(SimCredentialError.ERROR, credentialError = e.describe())
        }
        val credential = response.credential as? DigitalCredential
            ?: throw SimCredentialException(
                SimCredentialError.ERROR,
                credentialError = "Credential Manager returned an unexpected credential type: ${response.credential.type}",
            )
        return credential.credentialJson
    }

    /** The whole exception as Java prints it (class, message, stack trace, causes), plus its type, which that leaves out. */
    private fun GetCredentialException.describe(): String =
        "Credential Manager threw an exception: ${stackTraceToString()}Exception type: $type"

    private fun activeSubscriptionCarrierIds(): List<Int> {
        val slotCount =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephonyManager.activeModemCount
            else @Suppress("DEPRECATION") telephonyManager.phoneCount

        val carrierIds = mutableListOf<Int>()
        for (slot in 0 until slotCount) {
            val subscriptionId = subscriptionId(slot)
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) continue
            carrierIds.add(telephonyManager.createForSubscriptionId(subscriptionId).simCarrierId)
        }
        return carrierIds
    }

    private fun subscriptionId(slot: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            SubscriptionManager.getSubscriptionId(slot)
        } else {
            @Suppress("DEPRECATION")
            subscriptionManager.getSubscriptionIds(slot)?.firstOrNull() ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
}
