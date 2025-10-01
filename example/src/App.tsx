import { useState } from 'react';
import {
  Text,
  View,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Platform,
} from 'react-native';
import { useSMSRetriever } from '../../src/index';

export default function App() {
  const [, setOtp] = useState<string>('');
  const {
    appHash,
    smsCode,
    isLoading,
    isListening,
    error,
    isReady,
    startListening,
    stopListening,
    clearError,
    reset,
  } = useSMSRetriever({
    timeoutMs: 30000,
    onSuccess: (code: string) => {
      setOtp(code);
      Alert.alert('Success', `OTP received: ${code}`);
    },
    onError: (err: any) => {
      Alert.alert('Error', `${err.type}: ${err.message}`);
    },
  });

  const handleStartListening = async () => {
    try {
      clearError();
      setOtp('');
      await startListening();
    } catch (err) {
      Alert.alert('Error', `Failed to start listening: ${err}`);
    }
  };

  const handleStopListening = () => {
    stopListening();
  };

  const handleReset = () => {
    reset();
    setOtp('');
  };

  if (Platform.OS === 'ios') {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>SMS Retriever Example</Text>
        <Text style={styles.warning}>
          SMS Retriever is only supported on Android. Please run this example on
          an Android device or emulator.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>SMS Retriever Example</Text>

      <View style={styles.section}>
        <Text style={styles.label}>App Hash:</Text>
        <Text style={styles.value}>{appHash || 'Loading...'}</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>Status:</Text>
        <Text style={styles.value}>
          {isLoading
            ? 'Loading...'
            : isListening
              ? 'Listening for SMS...'
              : 'Ready'}
        </Text>
      </View>

      {error && (
        <View style={styles.section}>
          <Text style={styles.errorLabel}>Error:</Text>
          <Text style={styles.errorValue}>{error}</Text>
        </View>
      )}

      {smsCode && (
        <View style={styles.section}>
          <Text style={styles.label}>Received OTP:</Text>
          <Text style={styles.otpValue}>{smsCode}</Text>
        </View>
      )}

      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={[
            styles.button,
            styles.startButton,
            (!isReady || isListening) && styles.disabledButton,
          ]}
          onPress={handleStartListening}
          disabled={!isReady || isListening}
        >
          <Text style={styles.buttonText}>Start Listening</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.button,
            styles.stopButton,
            !isListening && styles.disabledButton,
          ]}
          onPress={handleStopListening}
          disabled={!isListening}
        >
          <Text style={styles.buttonText}>Stop Listening</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, styles.resetButton]}
          onPress={handleReset}
        >
          <Text style={styles.buttonText}>Reset</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.instructions}>
        <Text style={styles.instructionsTitle}>Instructions:</Text>
        <Text style={styles.instructionsText}>
          1. Make sure your app is signed with the debug keystore{'\n'}
          2. Use the app hash above in your SMS message{'\n'}
          3. Send an SMS with the format: "Your OTP is 123456 FA+9qCX9VSu"
          (replace with your app hash){'\n'}
          4. The OTP will be automatically extracted and displayed
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 30,
    color: '#333',
  },
  warning: {
    fontSize: 16,
    textAlign: 'center',
    color: '#ff6b6b',
    padding: 20,
    backgroundColor: '#ffe0e0',
    borderRadius: 8,
    marginTop: 20,
  },
  section: {
    marginBottom: 20,
    padding: 15,
    backgroundColor: 'white',
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 5,
  },
  value: {
    fontSize: 14,
    color: '#666',
    fontFamily: 'monospace',
  },
  errorLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#ff6b6b',
    marginBottom: 5,
  },
  errorValue: {
    fontSize: 14,
    color: '#ff6b6b',
  },
  otpValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#4CAF50',
    textAlign: 'center',
    letterSpacing: 2,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 30,
    flexWrap: 'wrap',
  },
  button: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
    marginBottom: 10,
    minWidth: 100,
  },
  startButton: {
    backgroundColor: '#4CAF50',
  },
  stopButton: {
    backgroundColor: '#ff6b6b',
  },
  resetButton: {
    backgroundColor: '#2196F3',
  },
  disabledButton: {
    backgroundColor: '#ccc',
  },
  buttonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
  instructions: {
    padding: 15,
    backgroundColor: 'white',
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  instructionsTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 10,
  },
  instructionsText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
});
