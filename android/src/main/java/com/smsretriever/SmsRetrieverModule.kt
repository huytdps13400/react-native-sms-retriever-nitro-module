package com.smsretriever

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.smsretriever.NativeSMSRetrieverSpec
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.auth.api.phone.SmsRetrieverClient
import java.util.concurrent.TimeUnit

data class SMSData(
    val otpValue: String = "",
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

enum class SMSErrorType {
    TIMEOUT,
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
    INVALID_SMS_FORMAT,
    UNKNOWN_ERROR
}

class SMSRetrieverModule(reactContext: ReactApplicationContext) : NativeSMSRetrieverSpec(reactContext) {

    companion object {
        private const val TAG = "SMSRetrieverModule"
        private const val DEFAULT_TIMEOUT_MS = 30000L // 30 seconds
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private var isRegistered: Boolean = false
    private var isListening: Boolean = false
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var retryCount: Int = 0
    private var currentPromise: Promise? = null

    private val smsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Intent received: ${intent.action}")
            
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val status = extras?.getParcelable<Status>(SmsRetriever.EXTRA_STATUS)

                if (status == null) {
                    Log.e(TAG, "Status is null in SMS intent")
                    handleError(SMSErrorType.UNKNOWN_ERROR, "Invalid SMS status")
                    return
                }

                Log.d(TAG, "SMS Status: ${status.statusCode}")
                
                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val sms = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
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
                        handleSuccess(otp)
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        Log.w(TAG, "SMS retrieval timeout")
                        handleError(SMSErrorType.TIMEOUT, "SMS retrieval timeout")
                    }
                    CommonStatusCodes.API_NOT_CONNECTED -> {
                        Log.e(TAG, "Google Play Services not connected")
                        handleError(SMSErrorType.SERVICE_UNAVAILABLE, "Google Play Services not available")
                    }
                    else -> {
                        Log.e(TAG, "Unknown SMS status code: ${status.statusCode}")
                        handleError(SMSErrorType.UNKNOWN_ERROR, "Unknown error: ${status.statusCode}")
                    }
                }
            }
        }
    }

    private fun extractOTPFromSMS(sms: String?): String? {
        if (sms.isNullOrBlank()) {
            Log.w(TAG, "SMS is null or blank")
            return null
        }

        Log.d(TAG, "Extracting OTP from SMS: $sms")
        
        // Multiple OTP patterns for better coverage
        val otpPatterns = listOf(
            "\\b\\d{4,6}\\b",           // 4-6 digit numbers with word boundaries
            "(?i)(?:otp|code|verification|pin)[\\s:]*([0-9]{4,6})", // OTP with prefix
            "([0-9]{4,6})",             // Simple 4-6 digit numbers
            "\\d{4,8}"                  // Extended range for some services
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

    private fun handleSuccess(otp: String) {
        Log.d(TAG, "SMS retrieval successful: $otp")
        cleanup()
        emitOnSMSRetrieved(otp)
        currentPromise?.resolve(otp)
        currentPromise = null
    }

    private fun handleError(errorType: SMSErrorType, message: String) {
        Log.e(TAG, "SMS retrieval error: $message")
        cleanup()
        
        val errorMap = Arguments.createMap().apply {
            putString("type", errorType.name)
            putString("message", message)
            putInt("retryCount", retryCount)
        }
        
        emitOnSMSError(errorMap as com.facebook.react.bridge.ReadableMap)
        currentPromise?.reject(errorType.name, message)
        currentPromise = null
    }

    private fun cleanup() {
        isListening = false
        timeoutRunnable?.let { runnable: Runnable ->
            timeoutHandler?.removeCallbacks(runnable)
        }
        timeoutRunnable = null
    }

    private fun setupTimeout(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            Log.w(TAG, "SMS retrieval timeout after ${timeoutMs}ms")
            handleError(SMSErrorType.TIMEOUT, "SMS retrieval timeout")
        }
        timeoutRunnable?.let { runnable: Runnable ->
            timeoutHandler?.postDelayed(runnable, timeoutMs)
        }
    }

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
                setupTimeout()
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

    private fun registerBroadcastReceiver() {
        if (!isRegistered) {
            try {
                val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                reactApplicationContext.registerReceiver(smsBroadcastReceiver, intentFilter)
                isRegistered = true
                Log.d(TAG, "SMS BroadcastReceiver registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SMS BroadcastReceiver", e)
                handleError(SMSErrorType.UNKNOWN_ERROR, "Failed to register SMS receiver: ${e.message}")
            }
        }
    }

    override fun stopSMSListener() {
        Log.d(TAG, "Stopping SMS listener...")
        
        try {
            if (isRegistered) {
                reactApplicationContext.unregisterReceiver(smsBroadcastReceiver)
                isRegistered = false
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

    // Implement the abstract method from the generated spec
    override fun startSMSListenerWithPromise(timeoutMs: Double?, promise: Promise) {
        val timeout = timeoutMs?.toLong() ?: DEFAULT_TIMEOUT_MS
        
        if (isListening) {
            Log.w(TAG, "SMS listener is already active")
            promise.reject("ALREADY_LISTENING", "SMS listener is already active")
            return
        }

        currentPromise = promise
        retryCount = 0
        
        Log.d(TAG, "Starting SMS listener with promise support...")
        
        try {
            val client: SmsRetrieverClient = SmsRetriever.getClient(reactApplicationContext)
            val task = client.startSmsRetriever()
        
            task.addOnSuccessListener {
                Log.d(TAG, "SMS Retriever started successfully")
                registerBroadcastReceiver()
                isListening = true
                setupTimeout(timeout)
                Log.d(TAG, "SMS listener is now active and waiting for SMS...")
            }
        
            task.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start SMS Retriever: ${exception.message}", exception)
                promise.reject("START_FAILED", "Failed to start SMS Retriever: ${exception.message}")
                currentPromise = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting SMS listener", e)
            promise.reject("EXCEPTION", "Exception starting SMS listener: ${e.message}")
            currentPromise = null
        }
    }

    // Implement the abstract method from the generated spec
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
}