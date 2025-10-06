package com.smsretriever

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

class AppSignatureHelper(private val context: Context) {

    companion object {
        private const val HASH_TYPE = "SHA-256"
        private const val NUM_HASHED_BYTES = 9
        private const val NUM_BASE64_CHAR = 11
        private const val NAME = "AppSignatureHelper"
    }

    fun getAppSignatures(): List<String> {
        val appCodes = mutableListOf<String>()

        try {
            val packageName = context.packageName
            val packageManager = context.packageManager
            val signatures = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            ).signatures

            for (signature in signatures!!) {
                val hash = hash(packageName, signature.toCharsString())
                if (hash != null) {
                    appCodes.add(hash)
                    Log.d(NAME, "App Hash: $hash")
                }
            }
        } catch (e: Exception) {
            Log.e(NAME, "Error getting app signature", e)
        }

        return appCodes
    }

    private fun hash(packageName: String, signature: String): String? {
        val appInfo = "$packageName $signature"
        return try {
            val messageDigest = MessageDigest.getInstance(HASH_TYPE)
            messageDigest.update(appInfo.toByteArray())
            var hashSignature = messageDigest.digest()

            hashSignature = hashSignature.copyOfRange(0, NUM_HASHED_BYTES)
            val base64Hash = Base64.encodeToString(
                hashSignature,
                Base64.NO_PADDING or Base64.NO_WRAP
            )
            base64Hash.substring(0, NUM_BASE64_CHAR)
        } catch (e: Exception) {
            Log.e(NAME, "Hash generation failed", e)
            null
        }
    }
}