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
        val nitroClass = Class.forName("com.margelo.nitro.core.HybridObjectRegistry")
        val registerMethod = nitroClass.getMethod(
          "registerHybridObjectConstructor",
          String::class.java,
          java.util.function.Supplier::class.java
        )
        val hybridClass = Class.forName("com.smsretriever.HybridSMSRetriever")
        val supplier = java.util.function.Supplier<Any> {
          hybridClass.getDeclaredConstructor().newInstance()
        }
        registerMethod.invoke(null, "SMSRetriever", supplier)
        android.util.Log.i("SMSRetrieverPackage", "✅ Nitro module registered successfully")
      } catch (e: ClassNotFoundException) {
        android.util.Log.w("SMSRetrieverPackage", "Nitro modules not available, using TurboModule fallback")
      } catch (e: Exception) {
        android.util.Log.w("SMSRetrieverPackage", "Failed to register Nitro module: ${e.message}")
      }
    }
  }

  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
    if (name == NativeSmsRetrieverSpec.NAME) {
      SMSRetrieverModule(reactContext)
    } else {
      null
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