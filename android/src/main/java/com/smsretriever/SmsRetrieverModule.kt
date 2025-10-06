package com.smsretriever

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.auth.api.phone.SmsRetrieverClient
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import java.util.concurrent.TimeUnit
import com.smsretriever.NativeSmsRetrieverSpec
import com.smsretriever.AppSignatureHelper

enum class SMSErrorType {
    TIMEOUT,
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
    INVALID_SMS_FORMAT,
    UNKNOWN_ERROR
}

class SMSRetrieverModule(reactContext: ReactApplicationContext) : NativeSmsRetrieverSpec(reactContext) {

    companion object {
        private const val TAG = "SMSRetrieverModule"
        private const val DEFAULT_UI_TIMEOUT_MS = 30_000L      
        private const val RETRIEVER_WINDOW_MS = 5 * 60 * 1000L
    }

    init {
        try {
            val hybridClass = Class.forName("com.margelo.nitro.com.smsretriever.HybridSMSRetriever")
            val contextField = hybridClass.getDeclaredField("applicationContext")
            contextField.isAccessible = true
            contextField.set(null, reactApplicationContext.applicationContext)
            Log.i(TAG, "✅ Set HybridSMSRetriever.applicationContext")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Nitro Hybrid not found, Turbo fallback will work")
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting Nitro applicationContext: ${e.message}")
        }
    }

    private var isRegistered: Boolean = false
    private var isListening: Boolean = false
    private var retryCount: Int = 0

    private var uiTimeoutHandler: Handler? = null
    private var uiTimeoutRunnable: Runnable? = null
    private var hardStopHandler: Handler? = null
    private var hardStopRunnable: Runnable? = null

    private var currentPromise: Promise? = null

    private val smsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Intent received: ${intent.action}")

            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
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
                            handleError(SMSErrorType.INVALID_SMS_FORMAT, "Empty SMS message", unregister = true)
                            return
                        }
                        val otp = extractOTPFromSMS(sms)
                        if (otp.isNullOrBlank()) {
                            Log.e(TAG, "Could not extract OTP from SMS: $sms")
                            handleError(SMSErrorType.INVALID_SMS_FORMAT, "No valid OTP found in SMS", unregister = true)
                            return
                        }
                        Log.d(TAG, "OTP extracted successfully: $otp")
                        handleSuccess(otp, unregister = true)
                    }

                    CommonStatusCodes.TIMEOUT -> {
                        Log.w(TAG, "Play Services retriever TIMEOUT")
                        handleError(SMSErrorType.TIMEOUT, "Retriever window ended", unregister = true)
                    }

                    CommonStatusCodes.API_NOT_CONNECTED -> {
                        Log.e(TAG, "Google Play Services not connected")
                        handleError(SMSErrorType.SERVICE_UNAVAILABLE, "Google Play Services not available", unregister = true)
                    }

                    else -> {
                        Log.e(TAG, "Unknown SMS status code: ${status.statusCode}")
                        handleError(SMSErrorType.UNKNOWN_ERROR, "Unknown status: ${status.statusCode}", unregister = false)
                    }
                }
            }
        }
    }

    // ---------- Public API (TurboModule Spec) ----------

    override fun startSMSListener() {
        if (isListening) {
            Log.w(TAG, "SMS listener is already active")
            return
        }
        Log.d(TAG, "Starting SMS listener...")

        try {
            val client: SmsRetrieverClient = SmsRetriever.getClient(reactApplicationContext)
            val task = client.startSmsRetriever()

            task.addOnSuccessListener {
                Log.d(TAG, "SMS Retriever started successfully")
                registerBroadcastReceiver()
                isListening = true
                setupHardStopWindow()
                Log.d(TAG, "SMS listener is now active and waiting for SMS...")
            }

            task.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start SMS Retriever: ${exception.message}", exception)
                handleError(SMSErrorType.SERVICE_UNAVAILABLE, "Failed to start SMS Retriever: ${exception.message}", unregister = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting SMS listener", e)
            handleError(SMSErrorType.UNKNOWN_ERROR, "Exception starting SMS listener: ${e.message}", unregister = true)
        }
    }

    override fun startSMSListenerWithPromise(timeoutMs: Double?, promise: Promise) {
        val uiTimeout = timeoutMs?.toLong() ?: DEFAULT_UI_TIMEOUT_MS

        if (isListening) {
            Log.w(TAG, "SMS listener is already active")
            promise.reject("ALREADY_LISTENING", "SMS listener is already active")
            return
        }

        currentPromise = promise
        retryCount = 0
        Log.d(TAG, "Starting SMS listener with promise support (uiTimeout=${uiTimeout}ms)...")

        try {
            val client: SmsRetrieverClient = SmsRetriever.getClient(reactApplicationContext)
            val task = client.startSmsRetriever()

            task.addOnSuccessListener {
                Log.d(TAG, "SMS Retriever started successfully")
                registerBroadcastReceiver()
                isListening = true
                setupHardStopWindow()            
                setupUITimeout(uiTimeout, promise) 
                Log.d(TAG, "SMS listener is now active and waiting for SMS...")
            }

            task.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start SMS Retriever: ${exception.message}", exception)
                currentPromise = null
                promise.reject("START_FAILED", "Failed to start SMS Retriever: ${exception.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting SMS listener", e)
            currentPromise = null
            promise.reject("EXCEPTION", "Exception starting SMS listener: ${e.message}")
        }
    }

    override fun stopSMSListener() {
        Log.d(TAG, "Stopping SMS listener...")
        safeUnregister()
        cleanup(all = true)
        Log.d(TAG, "SMS listener stopped")
    }

    override fun getAppHash(promise: Promise) {
        try {
            Log.d(TAG, "Getting app hash...")
            val appSignatureHelper = AppSignatureHelper(this.reactApplicationContext)
            val appHashes = appSignatureHelper.getAppSignatures()

            if (appHashes.isEmpty()) {
                Log.w(TAG, "No app signatures found")
                promise.reject("NO_SIGNATURES", "No app signatures found")
                return
            }

            val primaryHash = appHashes.first()
            Log.d(TAG, "App hash retrieved successfully: $primaryHash")
            promise.resolve(primaryHash)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app hash", e)
            promise.reject("HASH_ERROR", "Failed to get app hash: ${e.message}")
        }
    }

    override fun getStatus(promise: Promise) {
        try {
            val statusMap = Arguments.createMap().apply {
                putBoolean("isListening", isListening)
                putBoolean("isRegistered", isRegistered)
                putInt("retryCount", retryCount)
            }
            promise.resolve(statusMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status", e)
            promise.reject("STATUS_ERROR", "Failed to get status: ${e.message}")
        }
    }

    // ---------- Internal helpers ----------

    private fun registerBroadcastReceiver() {
        if (isRegistered) return
        try {
            val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reactApplicationContext.registerReceiver(
                    smsBroadcastReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                reactApplicationContext.registerReceiver(smsBroadcastReceiver, filter)
            }
            isRegistered = true
            Log.d(TAG, "SMS BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SMS BroadcastReceiver", e)
        }
    }

    private fun safeUnregister() {
        if (!isRegistered) return
        try {
            reactApplicationContext.unregisterReceiver(smsBroadcastReceiver)
            isRegistered = false
            Log.d(TAG, "SMS BroadcastReceiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "SMS BroadcastReceiver was not registered: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering SMS BroadcastReceiver", e)
        }
    }

    private fun setupUITimeout(timeoutMs: Long, promise: Promise) {
        uiTimeoutHandler = Handler(Looper.getMainLooper())
        uiTimeoutRunnable = Runnable {
            Log.w(TAG, "UI/promise timeout after ${timeoutMs}ms")
            if (currentPromise == promise) {
                try {
                    currentPromise?.reject("UI_TIMEOUT", "SMS retrieval UI timeout")
                } catch (_: Exception) {}
                currentPromise = null
            }
        }
        uiTimeoutHandler?.postDelayed(uiTimeoutRunnable!!, timeoutMs)
    }

    private fun setupHardStopWindow() {
        hardStopHandler = Handler(Looper.getMainLooper())
        hardStopRunnable = Runnable {
            if (isListening) {
                Log.w(TAG, "Retriever window ended (~5min)")
                handleError(SMSErrorType.TIMEOUT, "Retriever window ended", unregister = true)
            }
        }
        hardStopHandler?.postDelayed(hardStopRunnable!!, RETRIEVER_WINDOW_MS)
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

    private fun emitEventOnSMSRetrieved(otp: String) {
        val params: WritableMap = Arguments.createMap().apply { putString("otp", otp) }
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onSMSRetrieved", params)
    }

    private fun emitEventOnSMSError(errorMap: com.facebook.react.bridge.ReadableMap) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("onSMSError", errorMap)
    }

    private fun handleSuccess(otp: String, unregister: Boolean) {
        Log.d(TAG, "SMS retrieval successful: $otp")
        if (unregister) safeUnregister()
        cleanup(all = true)

        emitEventOnSMSRetrieved(otp)
        currentPromise?.resolve(otp)
        currentPromise = null
    }

    private fun handleError(errorType: SMSErrorType, message: String, unregister: Boolean) {
        Log.e(TAG, "SMS retrieval error: $message")
        if (unregister) safeUnregister()
        cleanup(all = true)

        val errorMap = Arguments.createMap().apply {
            putString("type", errorType.name)
            putString("message", message)
        }

        emitEventOnSMSError(errorMap as com.facebook.react.bridge.ReadableMap)
        currentPromise?.reject(errorType.name, message)
        currentPromise = null
    }

    private fun cleanup(all: Boolean) {
        isListening = false

        uiTimeoutRunnable?.let { uiTimeoutHandler?.removeCallbacks(it) }
        uiTimeoutRunnable = null
        uiTimeoutHandler = null

        hardStopRunnable?.let { hardStopHandler?.removeCallbacks(it) }
        hardStopRunnable = null
        hardStopHandler = null
    }
}
