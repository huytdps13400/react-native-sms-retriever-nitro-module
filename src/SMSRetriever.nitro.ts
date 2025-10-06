import type { HybridObject } from 'react-native-nitro-modules';
import type { SMSStatus } from './types';
import type { SMSError } from './types';

export interface SMSRetriever extends HybridObject {
  readonly appHash: string;
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
