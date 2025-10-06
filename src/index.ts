import { useEffect, useCallback, useRef, useState } from 'react';
import { Platform } from 'react-native';
import type { SMSError, SMSStatus } from './types';
import SMSRetrieverNitro from './SMSRetrieverNitro';

const smsRetriever = new SMSRetrieverNitro();

export interface UseSMSRetrieverOptions {
  timeoutMs?: number;
  autoStart?: boolean;
  onSuccess?: (otp: string) => void;
  onError?: (error: SMSError) => void;
}

export interface UseSMSRetrieverReturn {
  // State
  appHash: string;
  smsCode: string;
  isLoading: boolean;
  isListening: boolean;
  error: string | null;
  status: SMSStatus | null;

  // Actions
  startListening: () => Promise<string>;
  stopListening: () => void;
  refreshStatus: () => Promise<void>;
  clearError: () => void;
  reset: () => void;

  // Utilities
  isReady: boolean;
  hasError: boolean;
}

/**
 * Custom hook for SMS Retriever functionality
 * Supports both Nitro modules (new architecture) and TurboModules (old architecture)
 * Compatible with Expo
 */
export const useSMSRetriever = (
  options: UseSMSRetrieverOptions = {}
): UseSMSRetrieverReturn => {
  const { timeoutMs = 30000, autoStart = true, onSuccess, onError } = options;

  // State
  const [appHash, setAppHash] = useState<string>('');
  const [smsCode, setSmsCode] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isListening, setIsListening] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<SMSStatus | null>(null);

  // Refs for cleanup
  const isInitialized = useRef<boolean>(false);

  // Platform check - SMS Retriever is Android-only
  const isAndroid = Platform.OS === 'android';

  // Initialize the SMS Retriever
  const initialize = useCallback(async () => {
    if (isInitialized.current) return;

    try {
      setIsLoading(true);
      setError(null);

      if (!isAndroid) {
        setError('SMS Retriever is only supported on Android');
        setIsLoading(false);
        isInitialized.current = true;
        return;
      }

      const hash = await smsRetriever.getAppHash();
      setAppHash(hash);

      const currentStatus = await smsRetriever.getStatus();
      setStatus(currentStatus);

      console.log('SMS Retriever initialized:', {
        hash,
        status: currentStatus,
      });
    } catch (initError) {
      console.error('Failed to initialize SMS Retriever:', initError);
      setError(`Initialization failed: ${initError}`);
    } finally {
      setIsLoading(false);
      isInitialized.current = true;
    }
  }, [isAndroid]);

  // Start SMS listening
  const startListening = useCallback(async (): Promise<string> => {
    try {
      setError(null);
      setIsListening(true);

      if (!isAndroid) {
        throw new Error('SMS Retriever is only supported on Android');
      }

      const otp = await smsRetriever.startListeningWithPromise(timeoutMs);
      setSmsCode(otp);
      setIsListening(false);

      onSuccess?.(otp);

      return otp;
    } catch (startError) {
      console.error('Failed to start SMS listener:', startError);
      setError(`SMS retrieval failed: ${startError}`);
      setIsListening(false);
      throw startError;
    }
  }, [timeoutMs, onSuccess, isAndroid]);

  // Stop SMS listening
  const stopListening = useCallback(() => {
    try {
      if (isAndroid) {
        smsRetriever.stopListening();
      }
      setIsListening(false);
      setError(null);
      console.log('SMS listener stopped');
    } catch (stopError) {
      console.error('Failed to stop SMS listener:', stopError);
      setError(`Failed to stop listener: ${stopError}`);
    }
  }, [isAndroid]);

  // Refresh status
  const refreshStatus = useCallback(async () => {
    try {
      if (isAndroid) {
        const currentStatus = await smsRetriever.getStatus();
        setStatus(currentStatus);
      }
    } catch (statusError) {
      console.error('Failed to get status:', statusError);
    }
  }, [isAndroid]);

  // Clear error
  const clearError = useCallback(() => {
    setError(null);
  }, []);

  // Reset all state
  const reset = useCallback(() => {
    setSmsCode('');
    setError(null);
    setIsListening(false);
    stopListening();
  }, [stopListening]);

  // Setup event listeners
  useEffect(() => {
    if (!isInitialized.current || !isAndroid) return;

    const smsCleanup = smsRetriever.onSMSRetrieved((otp: string) => {
      setSmsCode(otp);
      setIsListening(false);
      setError(null);
      onSuccess?.(otp);
      console.log('SMS Code received via event:', otp);
    });

    const errorCleanup = smsRetriever.onSMSError((smsError: SMSError) => {
      const errorMessage = `${smsError.type}: ${smsError.message}`;
      setError(errorMessage);
      setIsListening(false);
      onError?.(smsError);
      console.error('SMS Error:', smsError);
    });

    return () => {
      smsCleanup();
      errorCleanup();
    };
  }, [onSuccess, onError, isAndroid]);

  // Initialize on mount
  useEffect(() => {
    initialize();

    return () => {
      stopListening();
    };
  }, [initialize, stopListening]);

  // Auto-start if enabled
  useEffect(() => {
    if (autoStart && isInitialized.current && !isListening && !error) {
      startListening().catch(console.error);
    }
  }, [autoStart, isListening, error, startListening]);

  // Computed values
  const isReady = isInitialized.current && !isLoading && !error;
  const hasError = error !== null;

  return {
    // State
    appHash,
    smsCode,
    isLoading,
    isListening,
    error,
    status,

    // Actions
    startListening,
    stopListening,
    refreshStatus,
    clearError,
    reset,

    // Utilities
    isReady,
    hasError,
  };
};

export default useSMSRetriever;

// Export types for external use
export type { SMSError, SMSStatus } from './types';
