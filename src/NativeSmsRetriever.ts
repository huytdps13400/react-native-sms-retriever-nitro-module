import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface SMSError {
  type:
    | 'TIMEOUT'
    | 'PERMISSION_DENIED'
    | 'SERVICE_UNAVAILABLE'
    | 'INVALID_SMS_FORMAT'
    | 'UNKNOWN_ERROR';
  message: string;
  retryCount: number;
}

export interface SMSStatus {
  isListening: boolean;
  isRegistered: boolean;
  retryCount: number;
}

// TurboModule Spec without EventEmitters (not supported by codegen)
// EventEmitters are handled in TurboModuleFallback.ts
export interface Spec extends TurboModule {
  startSMSListener(): void;
  startSMSListenerWithPromise(timeoutMs?: number): Promise<string>;
  stopSMSListener(): void;
  getAppHash(): Promise<string>;
  getStatus(): Promise<SMSStatus>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SMSRetriever') as Spec;
