package com.smsretriever

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class SMSRetrieverPackage : BaseReactPackage() {

  companion object {
    init {
      try {
        // Nitrogen autolinking will handle registration automatically
        val onLoadClass = Class.forName("com.margelo.nitro.com.smsretriever.NitroSMSRetrieverOnLoad")
        val initMethod = onLoadClass.getMethod("initializeNative")
        initMethod.invoke(null)
        android.util.Log.i("SMSRetrieverPackage", "✅ Nitro C++ library loaded and HybridObject auto-registered")
      } catch (e: ClassNotFoundException) {
        android.util.Log.w("SMSRetrieverPackage", "Nitro modules not available, using TurboModule fallback")
      } catch (e: Exception) {
        android.util.Log.w("SMSRetrieverPackage", "Failed to load Nitro module: ${e.message}")
      }
    }
  }

  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == NativeSmsRetrieverSpec.NAME) {
      SMSRetrieverModule(reactContext)
    } else {
      null
    }
  }

  override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
    mapOf(
      NativeSmsRetrieverSpec.NAME to ReactModuleInfo(
        name = NativeSmsRetrieverSpec.NAME,
        className = SMSRetrieverModule::class.java.name,
        canOverrideExistingModule = false,
        needsEagerInit = false,
        isCxxModule = false,
        isTurboModule = true
      )
    )
  }  
}