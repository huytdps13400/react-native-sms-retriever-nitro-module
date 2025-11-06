import { Platform, NativeModules, NativeEventEmitter } from 'react-native';
import type { SMSError, SMSStatus } from './types';

const LINKING_ERROR =
  `The package '@huymobile/react-native-sms-retriever-nitro-module' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- Run 'pod install'\n", default: '' }) +
  '- Rebuild the app after installing the package\n' +
  '- You are running on a physical device (not simulator)';

const SMSRetrieverModule = NativeModules.SMSRetriever
  ? NativeModules.SMSRetriever
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const eventEmitter = new NativeEventEmitter(SMSRetrieverModule);

export interface TurboModuleSMSRetrieverInterface {
  startSMSListener(): void;
  startSMSListenerWithPromise(timeoutMs?: number): Promise<string>;
  stopSMSListener(): void;
  getAppHash(): Promise<string>;
  getStatus(): Promise<SMSStatus>;
  onSMSRetrieved(callback: (otp: string) => void): () => void;
  onSMSError(callback: (error: SMSError) => void): () => void;
}

export const TurboModuleSMSRetriever: TurboModuleSMSRetrieverInterface = {
  startSMSListener: () => SMSRetrieverModule.startSMSListener(),

  startSMSListenerWithPromise: (timeoutMs?: number) =>
    SMSRetrieverModule.startSMSListenerWithPromise(timeoutMs),

  stopSMSListener: () => SMSRetrieverModule.stopSMSListener(),

  getAppHash: () => SMSRetrieverModule.getAppHash(),

  getStatus: () => SMSRetrieverModule.getStatus(),

  onSMSRetrieved: (callback: (otp: string) => void) => {
    const subscription = eventEmitter.addListener('onSMSRetrieved', callback as any);
    return () => subscription.remove();
  },

  onSMSError: (callback: (error: SMSError) => void) => {
    const subscription = eventEmitter.addListener('onSMSError', callback as any);
    return () => subscription.remove();
  },
};
