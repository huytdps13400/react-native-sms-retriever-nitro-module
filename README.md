# @huymobile/react-native-sms-retriever-nitro-module

A React Native library for Android SMS Retriever API with support for both Nitro Modules and TurboModules (new architecture). This library allows you to automatically retrieve SMS messages containing OTP codes without requiring SMS permissions.

[![npm version](https://badge.fury.io/js/%40huymobile%2Freact-native-sms-retriever-nitro-module.svg)](https://badge.fury.io/js/%40huymobile%2Freact-native-sms-retriever-nitro-module)
[![npm downloads](https://img.shields.io/npm/dm/@huymobile/react-native-sms-retriever-nitro-module.svg)](https://www.npmjs.com/package/@huymobile/react-native-sms-retriever-nitro-module)

## 📱 Demo

<div align="center">
  
![SMS Retriever Demo](react-native-sms-android.gif)

*Automatic OTP detection without SMS permissions*

</div>
## Features

- ✅ **Android SMS Retriever API** - Automatically retrieve SMS messages
- ✅ **New Architecture Support** - Built with Nitro Modules and TurboModules
- ✅ **TypeScript Support** - Full TypeScript definitions included
- ✅ **React Hook** - Easy-to-use `useSMSRetriever` hook
- ✅ **Android-Only** - Optimized specifically for Android SMS Retriever API
- ✅ **No Permissions Required** - Uses Google Play Services SMS Retriever API
- ✅ **Automatic OTP Extraction** - Smart pattern matching for OTP codes

## Installation

```sh
# Using npm
npm install @huymobile/react-native-sms-retriever-nitro-module

# Using yarn
yarn add @huymobile/react-native-sms-retriever-nitro-module

# Using bun
bun add @huymobile/react-native-sms-retriever-nitro-module
```

### Android Setup

The library uses autolinking, so no additional setup is required for React Native 0.76+.

## Usage

### Using the Hook (Recommended)

```tsx
import React from 'react';
import { View, Text, TouchableOpacity, Alert } from 'react-native';
import { useSMSRetriever } from '@huymobile/react-native-sms-retriever-nitro-module';

export default function App() {
  const {
    appHash,
    smsCode,
    isLoading,
    isListening,
    error,
    isReady,
    clearError,
  } = useSMSRetriever({
    timeoutMs: 30000, // 30 seconds
    onSuccess: (otp) => {
      Alert.alert('Success', `OTP received: ${otp}`);
    },
    onError: (error) => {
      Alert.alert('Error', `${error.type}: ${error.message}`);
    },
  });

  return (
    <View style={{ flex: 1, padding: 20 }}>
      <Text>App Hash: {appHash}</Text>
      <Text>Status: {isListening ? 'Listening...' : 'Ready'}</Text>
      {smsCode && <Text>OTP: {smsCode}</Text>}
      {error && <Text style={{ color: 'red' }}>Error: {error}</Text>}
    </View>
  );
}
```

### Using the Native Module Directly

```tsx
import NativeSMSRetriever from '@huymobile/react-native-sms-retriever-nitro-module';

// Get app hash for SMS message
const appHash = await NativeSMSRetriever.getAppHash();
console.log('App Hash:', appHash);

// Start listening for SMS
try {
  const otp = await NativeSMSRetriever.startSMSListenerWithPromise(30000);
  console.log('OTP received:', otp);
} catch (error) {
  console.error('SMS retrieval failed:', error);
}

// Stop listening
NativeSMSRetriever.stopSMSListener();

// Listen for events
const subscription = NativeSMSRetriever.onSMSRetrieved((otp) => {
  console.log('OTP received via event:', otp);
});

// Clean up
subscription.remove();
```

## SMS Message Format

To use the SMS Retriever API, your SMS message must:

1. **Contain the app hash** at the end of the message
2. **Be sent from a phone number** (not an email or other service)
3. **Be received within the timeout period** (default: 30 seconds)

Example SMS format:

```
Your OTP is 123456 FA+9qCX9VSu
```

Where `FA+9qCX9VSu` is your app hash (obtained from `getAppHash()`).

## API Reference

### useSMSRetriever Hook

#### Options

- `timeoutMs?: number` - Timeout in milliseconds (default: 30000)
- `autoStart?: boolean` - Automatically start listening on mount (default: true)
- `onSuccess?: (otp: string) => void` - Success callback
- `onError?: (error: SMSError) => void` - Error callback

#### Return Value

- `appHash: string` - Your app's hash for SMS messages
- `smsCode: string` - The extracted OTP code
- `isLoading: boolean` - Whether the module is initializing
- `isListening: boolean` - Whether currently listening for SMS
- `error: string | null` - Current error message
- `status: SMSStatus | null` - Current status object
- `isReady: boolean` - Whether the module is ready to use
- `hasError: boolean` - Whether there's an error
- `startListening: () => Promise<string>` - Start listening for SMS
- `stopListening: () => void` - Stop listening
- `refreshStatus: () => Promise<void>` - Refresh status
- `clearError: () => void` - Clear current error
- `reset: () => void` - Reset all state

### Native Module Methods

- `getAppHash(): Promise<string>` - Get the app hash for SMS messages
- `startSMSListener(): void` - Start listening (fire-and-forget)
- `startSMSListenerWithPromise(timeoutMs?: number): Promise<string>` - Start listening with promise
- `stopSMSListener(): void` - Stop listening
- `getStatus(): Promise<SMSStatus>` - Get current status
- `onSMSRetrieved: EventEmitter<string>` - Listen for successful SMS retrieval
- `onSMSError: EventEmitter<SMSError>` - Listen for errors

## Platform Support

- ✅ **Android** - Full SMS Retriever API support
- ❌ **iOS** - Not supported (SMS Retriever API is Android-only)

## Requirements

- React Native 0.76+ (New Arch)
- Android API level 19+
- Google Play Services (for SMS Retriever API)
- Nitro Modules (optional, for improved performance)

## Troubleshooting

### Common Issues

1. **SMS not detected**: Ensure your SMS contains the app hash at the end
2. **Timeout errors**: Increase the timeout or check if Google Play Services is available
3. **Permission errors**: SMS Retriever API doesn't require SMS permissions
4. **Build errors**: Make sure you're using React Native 0.60+ with autolinking

### Debug Tips

- Use `getAppHash()` to get the correct hash for your app
- Check the console logs for detailed error messages
- Ensure your app is signed with the correct keystore
- Test with the debug keystore first before using release keystore

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with ❤️ by [huymobile](https://github.com/huytdps13400)
