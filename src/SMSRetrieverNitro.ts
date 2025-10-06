// src/SMSRetrieverNitro.ts
import { Platform } from 'react-native';
import { NitroModules } from 'react-native-nitro-modules';
import type { SMSRetriever } from './SMSRetriever.nitro';
import type { SMSError } from './types';
import type { SMSStatus } from './types';
import { TurboModuleSMSRetriever } from './TurboModuleFallback';

class SMSRetrieverNitro {
  private nitroModule: SMSRetriever | null = null;
  private isNitroAvailable = false;

  constructor() {
    this.initializeNitro();
  }

  private initializeNitro() {
    if (Platform.OS !== 'android') return;
    setImmediate(() => {
      try {
        console.log('🔍 Attempting to initialize Nitro Module...');
        this.nitroModule =
          NitroModules.createHybridObject<SMSRetriever>('SMSRetriever');
        this.isNitroAvailable = true;
        console.log('✅ Successfully initialized Nitro Module!');
        console.log(`📊 Nitro Module Info:
  - isListening: ${this.nitroModule!.isListening}
  - isRegistered: ${this.nitroModule!.isRegistered}`);
      } catch (error) {
        console.log(
          '⚠️ Nitro Module not available, falling back to TurboModule'
        );
        console.log('📝 Error details:', error);
        this.isNitroAvailable = false;
        this.nitroModule = null;
      }
    });
  }

  async getAppHash(): Promise<string> {
    if (this.isNitroAvailable && this.nitroModule) {
      console.log('📱 [Nitro] Getting app hash...');
      return await this.nitroModule.getAppHash();
    } else if (TurboModuleSMSRetriever) {
      console.log('📱 [TurboModule] Getting app hash...');
      return await TurboModuleSMSRetriever.getAppHash();
    }
    throw new Error('SMS Retriever is not available');
  }

  async startListeningWithPromise(timeoutMs: number = 30000): Promise<string> {
    if (this.isNitroAvailable && this.nitroModule) {
      console.log(
        `🚀 [Nitro] Starting listener with promise (timeout: ${timeoutMs}ms)`
      );
      return await this.nitroModule.startListeningWithPromise(timeoutMs);
    } else if (TurboModuleSMSRetriever) {
      console.log(
        `🚀 [TurboModule] Starting listener with promise (timeout: ${timeoutMs}ms)`
      );
      return await TurboModuleSMSRetriever.startSMSListenerWithPromise(
        timeoutMs
      );
    }
    throw new Error('SMS Retriever is not available');
  }

  startListening(): void {
    if (this.isNitroAvailable && this.nitroModule) {
      console.log('🚀 [Nitro] Starting listener...');
      this.nitroModule.startListening();
    } else if (TurboModuleSMSRetriever) {
      console.log('🚀 [TurboModule] Starting listener...');
      TurboModuleSMSRetriever.startSMSListener();
    }
  }

  stopListening(): void {
    if (this.isNitroAvailable && this.nitroModule) {
      this.nitroModule.stopListening();
    } else if (TurboModuleSMSRetriever) {
      TurboModuleSMSRetriever.stopSMSListener();
    }
  }

  async getStatus(): Promise<SMSStatus> {
    if (this.isNitroAvailable && this.nitroModule) {
      return await this.nitroModule.getStatus();
    } else if (TurboModuleSMSRetriever) {
      return await TurboModuleSMSRetriever.getStatus();
    }
    throw new Error('SMS Retriever is not available');
  }

  onSMSRetrieved(callback: (otp: string) => void) {
    if (this.isNitroAvailable && this.nitroModule) {
      return this.nitroModule.onSMSRetrieved(callback);
    } else if (TurboModuleSMSRetriever) {
      return TurboModuleSMSRetriever.onSMSRetrieved(callback);
    }
    return () => {};
  }

  onSMSError(callback: (error: SMSError) => void) {
    if (this.isNitroAvailable && this.nitroModule) {
      return this.nitroModule.onSMSError(callback);
    } else if (TurboModuleSMSRetriever) {
      return TurboModuleSMSRetriever.onSMSError(callback);
    }
    return () => {};
  }
}

export default SMSRetrieverNitro;
