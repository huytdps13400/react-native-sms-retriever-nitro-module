import type { HybridObject } from 'react-native-nitro-modules';

export type SMSErrorType =
  | 'TIMEOUT'
  | 'PERMISSION_DENIED'
  | 'SERVICE_UNAVAILABLE'
  | 'INVALID_SMS_FORMAT'
  | 'UNKNOWN_ERROR';

export interface SMSError {
  type: SMSErrorType;
  message: string;
  retryCount: number;
}

export interface SMSStatus {
  isListening: boolean;
  isRegistered: boolean;
  retryCount: number;
}

export interface SMSRetriever extends HybridObject<{ android: 'kotlin' }> {
  readonly isListening: boolean;
  readonly isRegistered: boolean;

  startListening(): void;
  startListeningWithPromise(timeoutMs?: number): Promise<string>;
  stopListening(): void;
  getAppHash(): Promise<string>;
  getStatus(): Promise<SMSStatus>;

  onSMSRetrieved(callback: (otp: string) => void): () => void;
  onSMSError(callback: (error: SMSError) => void): () => void;
}
