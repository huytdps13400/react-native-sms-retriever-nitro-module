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
