package com.margelo.nitro.com.smsretriever

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.auth.api.phone.SmsRetrieverClient
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.margelo.nitro.com.smsretriever.HybridSMSRetrieverSpec
import com.margelo.nitro.com.smsretriever.SMSError
import com.margelo.nitro.com.smsretriever.SMSStatus
import com.margelo.nitro.com.smsretriever.SMSErrorType
import com.margelo.nitro.core.Promise
import com.smsretriever.AppSignatureHelper

class HybridSMSRetriever : HybridSMSRetrieverSpec() {

    companion object {
        private const val TAG = "HybridSMSRetriever"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val RETRIEVER_WINDOW_MS = 5 * 60 * 1000L 
        @Volatile
        @JvmStatic
        var applicationContext: Context? = null
    }

    private val context: Context
        get() = applicationContext
            ?: throw IllegalStateException("Context not initialized. Set HybridSMSRetriever.applicationContext in your Module/Application.")

    private var isRegisteredField: Boolean = false
    private var isListeningField: Boolean = false
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var hardStopHandler: Handler? = null
    private var hardStopRunnable: Runnable? = null
    private var retryCountField: Int = 0

    private val smsCallbacks = mutableListOf<(String) -> Unit>()
    private val errorCallbacks = mutableListOf<(SMSError) -> Unit>()

    override val isListening: Boolean
        get() = isListeningField

    override val isRegistered: Boolean
        get() = isRegisteredField

    private val smsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "Intent received: $action")

            if (SmsRetriever.SMS_RETRIEVED_ACTION == action) {
                val extras = intent.extras
                val status: Status? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        extras?.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        extras?.getParcelable(SmsRetriever.EXTRA_STATUS)
                    }

                if (status == null) {
                    Log.e(TAG, "Status is null in SMS intent (API=${Build.VERSION.SDK_INT})")
                    return
                }

                Log.d(TAG, "SMS Status: ${status.statusCode}")

                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val sms = extras?.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                        if (sms.isNullOrBlank()) {
                            Log.e(TAG, "SMS message is null or empty")
                            handleError(SMSErrorType.INVALID_SMS_FORMAT, "Empty SMS message")
                            return
                        }
                        val otp = extractOTPFromSMS(sms)
                        if (otp.isNullOrBlank()) {
                            Log.e(TAG, "Could not extract OTP from SMS: $sms")
                            handleError(SMSErrorType.INVALID_SMS_FORMAT, "No valid OTP found in SMS")
                            return
                        }
                        Log.d(TAG, "OTP extracted successfully: $otp")
                        handleSuccess(otp, fromBroadcast = true)
                    }

                    CommonStatusCodes.TIMEOUT -> {
                        Log.w(TAG, "Play Services retriever TIMEOUT")
                        handleError(SMSErrorType.TIMEOUT, "Retriever window ended")
                    }

                    CommonStatusCodes.API_NOT_CONNECTED -> {
                        Log.e(TAG, "Google Play Services not connected")
                        handleError(SMSErrorType.SERVICE_UNAVAILABLE, "Google Play Services not available")
                    }

                    else -> {
                        Log.e(TAG, "Unknown SMS status code: ${status.statusCode}")
                        handleError(SMSErrorType.UNKNOWN_ERROR, "Unknown status: ${status.statusCode}")
                    }
                }
            }
        }
    }

    override fun getAppHash(): Promise<String> {
        val promise = Promise<String>()
        try {
            val ctx = context
            Log.d(TAG, "Getting app hash...")
            val appSignatureHelper = AppSignatureHelper(ctx)
            val appHashes = appSignatureHelper.getAppSignatures()

            if (appHashes.isEmpty()) {
                Log.w(TAG, "No app signatures found")
                promise.reject(Exception("No app signatures found"))
                return promise
            }

            val primaryHash = appHashes.first()
            Log.d(TAG, "App hash retrieved successfully: $primaryHash")
            promise.resolve(primaryHash)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app hash", e)
            promise.reject(e)
        }
        return promise
    }

    override fun startListening() {
        if (isListeningField) {
            Log.w(TAG, "SMS listener is already active")
            return
        }

        Log.d(TAG, "Starting SMS listener...")

        try {
            val client: SmsRetrieverClient = SmsRetriever.getClient(context)
            val task = client.startSmsRetriever()

            task.addOnSuccessListener {
                Log.d(TAG, "SMS Retriever started successfully")
                registerBroadcastReceiver()
                isListeningField = true
                setupHardStopWindow()
                Log.d(TAG, "SMS listener is now active and waiting for SMS...")
            }

            task.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start SMS Retriever: ${exception.message}", exception)
                handleError(SMSErrorType.SERVICE_UNAVAILABLE, "Failed to start SMS Retriever: ${exception.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting SMS listener", e)
            handleError(SMSErrorType.UNKNOWN_ERROR, "Exception starting SMS listener: ${e.message}")
        }
    }

    override fun startListeningWithPromise(timeoutMs: Double?): Promise<String> {
        val promise = Promise<String>()
        val uiTimeout = timeoutMs?.toLong() ?: DEFAULT_TIMEOUT_MS

        if (isListeningField) {
            Log.w(TAG, "SMS listener is already active")
            promise.reject(Exception("SMS listener is already active"))
            return promise
        }

        retryCountField = 0
        Log.d(TAG, "Starting SMS listener with promise support...")

        try {
            val client: SmsRetrieverClient = SmsRetriever.getClient(context)
            val task = client.startSmsRetriever()

            task.addOnSuccessListener {
                Log.d(TAG, "SMS Retriever started successfully")
                registerBroadcastReceiver()
                isListeningField = true
                setupHardStopWindow()
                setupTimeoutWithPromise(uiTimeout, promise)
                Log.d(TAG, "SMS listener is now active and waiting for SMS...")
            }

            task.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start SMS Retriever: ${exception.message}", exception)
                promise.reject(exception)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting SMS listener", e)
            promise.reject(e)
        }

        return promise
    }

    override fun stopListening() {
        Log.d(TAG, "Stopping SMS listener...")

        try {
            if (isRegisteredField) {
                context.unregisterReceiver(smsBroadcastReceiver)
                isRegisteredField = false
                Log.d(TAG, "SMS BroadcastReceiver unregistered successfully")
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "SMS BroadcastReceiver was not registered: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering SMS BroadcastReceiver", e)
        } finally {
            cleanup()
            Log.d(TAG, "SMS listener stopped")
        }
    }

    override fun getStatus(): Promise<SMSStatus> {
        val promise = Promise<SMSStatus>()
        try {
            val status = SMSStatus(
                isListening = isListeningField,
                isRegistered = isRegisteredField,
                retryCount = retryCountField.toDouble()
            )
            promise.resolve(status)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status", e)
            promise.reject(e)
        }
        return promise
    }

    override fun onSMSRetrieved(callback: (String) -> Unit): () -> Unit {
        smsCallbacks.add(callback)
        Log.d(TAG, "SMS callback registered, total: ${smsCallbacks.size}")
        return {
            smsCallbacks.remove(callback)
            Log.d(TAG, "SMS callback removed, remaining: ${smsCallbacks.size}")
        }
    }

    override fun onSMSError(callback: (SMSError) -> Unit): () -> Unit {
        errorCallbacks.add(callback)
        Log.d(TAG, "Error callback registered, total: ${errorCallbacks.size}")
        return {
            errorCallbacks.remove(callback)
            Log.d(TAG, "Error callback removed, remaining: ${errorCallbacks.size}")
        }
    }

    private fun extractOTPFromSMS(sms: String?): String? {
        if (sms.isNullOrBlank()) {
            Log.w(TAG, "SMS is null or blank")
            return null
        }

        Log.d(TAG, "Extracting OTP from SMS: $sms")

        val otpPatterns = listOf(
            "\\b\\d{4,6}\\b",
            "(?i)(?:otp|code|verification|pin)[\\s:]*([0-9]{4,6})",
            "([0-9]{4,6})",
            "\\d{4,8}"
        )

        for (pattern in otpPatterns) {
            val regex = pattern.toRegex()
            val match = regex.find(sms)
            if (match != null) {
                val otp = match.groupValues.lastOrNull { it.matches("\\d{4,8}".toRegex()) }
                if (!otp.isNullOrBlank()) {
                    Log.d(TAG, "OTP found with pattern '$pattern': $otp")
                    return otp
                }
            }
        }

        Log.w(TAG, "No valid OTP pattern found in SMS")
        return null
    }

    private fun handleSuccess(otp: String, fromBroadcast: Boolean) {
        Log.d(TAG, "SMS retrieval successful: $otp")

        safeUnregister()

        cleanup() 

        smsCallbacks.forEach { callback ->
            try {
                callback(otp)
            } catch (e: Exception) {
                Log.e(TAG, "Error in SMS callback", e)
            }
        }
    }

    private fun handleError(errorType: SMSErrorType, message: String) {
        Log.e(TAG, "SMS retrieval error: $message")

        if (errorType == SMSErrorType.TIMEOUT || errorType == SMSErrorType.SERVICE_UNAVAILABLE) {
            safeUnregister()
        }

        cleanup()

        val error = SMSError(
            type = errorType,
            message = message,
            retryCount = retryCountField.toDouble()
        )

        errorCallbacks.forEach { callback ->
            try {
                callback(error)
            } catch (e: Exception) {
                Log.e(TAG, "Error in error callback", e)
            }
        }
    }

    private fun cleanup() {
        isListeningField = false

        timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        timeoutRunnable = null
        timeoutHandler = null

        hardStopRunnable?.let { hardStopHandler?.removeCallbacks(it) }
        hardStopRunnable = null
        hardStopHandler = null
    }

    private fun setupTimeoutWithPromise(timeoutMs: Long, promise: Promise<String>) {
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            Log.w(TAG, "UI/promise timeout after ${timeoutMs}ms")
            try {
                promise.reject(Exception("SMS retrieval UI timeout"))
            } catch (_: Exception) {}
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMs)
    }

    private fun setupHardStopWindow() {
        hardStopHandler = Handler(Looper.getMainLooper())
        hardStopRunnable = Runnable {
            if (isListeningField) {
                Log.w(TAG, "Retriever window ended (~5min)")
                handleError(SMSErrorType.TIMEOUT, "Retriever window ended")
            }
        }
        hardStopHandler?.postDelayed(hardStopRunnable!!, RETRIEVER_WINDOW_MS)
    }

    private fun registerBroadcastReceiver() {
        if (isRegisteredField) return
        try {
            val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    smsBroadcastReceiver,
                    intentFilter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(smsBroadcastReceiver, intentFilter)
            }
            isRegisteredField = true
            Log.d(TAG, "SMS BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SMS BroadcastReceiver", e)
            handleError(SMSErrorType.UNKNOWN_ERROR, "Failed to register SMS receiver: ${e.message}")
        }
    }

    private fun safeUnregister() {
        if (!isRegisteredField) return
        try {
            context.unregisterReceiver(smsBroadcastReceiver)
            isRegisteredField = false
            Log.d(TAG, "SMS BroadcastReceiver unregistered (safeUnregister)")
        } catch (e: Exception) {
            Log.w(TAG, "safeUnregister failed: ${e.message}")
        }
    }
}
