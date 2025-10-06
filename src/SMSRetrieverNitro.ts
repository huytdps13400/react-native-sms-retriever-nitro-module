// src/SMSRetrieverNitro.ts
import { Platform } from 'react-native';
import { NitroModules } from 'react-native-nitro-modules';
import type { SMSRetriever } from './SMSRetriever.nitro';
import type { SMSError } from './types';
import type { SMSStatus } from './types';

// Fallback to TurboModule
let TurboModuleSMSRetriever: any = null;
if (Platform.OS === 'android') {
  try {
    const module = require('./NativeSMSRetriever');
    TurboModuleSMSRetriever = module.default;
  } catch (error) {
    console.warn('TurboModule not available:', error);
  }
}

class SMSRetrieverNitro {
  private nitroModule: SMSRetriever | null = null;
  private isNitroAvailable = false;

  constructor() {
    this.initializeNitro();
  }

  private async initializeNitro() {
    try {
      if (Platform.OS === 'android') {
        this.nitroModule =
          NitroModules.createHybridObject<SMSRetriever>('SMSRetriever');
        this.isNitroAvailable = true;
      }
    } catch (error) {
      console.warn('Nitro not available, using TurboModule:', error);
      this.isNitroAvailable = false;
    }
  }

  async getAppHash(): Promise<string> {
    if (this.isNitroAvailable && this.nitroModule) {
      return await this.nitroModule.getAppHash();
    } else if (TurboModuleSMSRetriever) {
      return await TurboModuleSMSRetriever.getAppHash();
    }
    throw new Error('SMS Retriever is not available');
  }

  async startListeningWithPromise(timeoutMs: number = 30000): Promise<string> {
    if (this.isNitroAvailable && this.nitroModule) {
      return await this.nitroModule.startListeningWithPromise(timeoutMs);
    } else if (TurboModuleSMSRetriever) {
      return await TurboModuleSMSRetriever.startSMSListenerWithPromise(
        timeoutMs
      );
    }
    throw new Error('SMS Retriever is not available');
  }

  startListening(): void {
    if (this.isNitroAvailable && this.nitroModule) {
      this.nitroModule.startListening();
    } else if (TurboModuleSMSRetriever) {
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
