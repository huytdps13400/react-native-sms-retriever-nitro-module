# 📱 SMS Retriever Example App

Example app demonstrating the SMS Retriever library with Nitro module support.

## 🚀 Quick Start

### Prerequisites

- Node.js >= 20
- Android Studio
- Android SDK
- Android device or emulator (API 21+)

### Installation

```bash
# Install dependencies
yarn install

# Run on Android
yarn android
```

## 🧪 Testing

### Method 1: Using Test Script (Recommended)

```bash
# Make script executable (first time only)
chmod +x test.sh

# Run test menu
./test.sh
```

**Test Options:**
1. 🚀 Run app on Android
2. 🧹 Clean build
3. 📦 Rebuild library
4. 🔄 Full clean & rebuild
5. 📱 Send test SMS (emulator only)
6. 📊 View logs
7. 🧪 Run all tests

### Method 2: Manual Testing

```bash
# Start Metro bundler
yarn start

# In another terminal, run Android
yarn android
```

### Method 3: Direct Build

```bash
cd android
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug
```

## 📝 Testing Steps

### 1. Check Module Type

Open the app and check the "Module Type" section:
- ⚡ **Nitro Module** - Using high-performance Nitro
- 🔄 **TurboModule** - Using fallback TurboModule

### 2. Get App Hash

Copy the "App Hash" value displayed in the app (e.g., `FA+9qCX9VSu`)

### 3. Start Listening

Tap "▶️ Start Listening" button. Status should change to "Listening for SMS..."

### 4. Send Test SMS

**Option A: From Another Phone**
```
Send SMS to your test device:
"Your OTP is 123456 FA+9qCX9VSu"
(Replace with your actual app hash)
```

**Option B: Using ADB (Emulator Only)**
```bash
# Get emulator port
adb devices

# Send SMS
adb -s emulator-5554 emu sms send +1234567890 "Your OTP is 123456 FA+9qCX9VSu"
```

**Option C: Using Test Script**
```bash
./test.sh
# Select option 5
```

**Option D: Android Studio (Emulator)**
1. Click "..." (Extended Controls)
2. Go to "Phone" → "SMS"
3. Enter message: "Your OTP is 123456 FA+9qCX9VSu"
4. Click "Send Message"

### 5. Verify Results

✅ Alert should show: "Success - OTP received: 123456"
✅ "Received OTP" section displays: 123456
✅ Console logs: "✅ OTP received: 123456"

## 🎯 Features to Test

### Basic Features

- [x] App launches without errors
- [x] Module type is detected (Nitro or TurboModule)
- [x] App hash is displayed
- [x] Start listening works
- [x] Stop listening works
- [x] Reset clears state
- [x] SMS is received and OTP extracted
- [x] Timeout works (30 seconds)
- [x] Error handling works

### Advanced Features

- [x] Performance test (100 iterations)
- [x] Detailed status display
- [x] Multiple OTP patterns
- [x] Console logging
- [x] Alert notifications

## ⚡ Performance Testing

### Run Performance Test

1. Tap "⚡ Performance Test" button
2. Wait for test to complete
3. Check results in alert and "Performance" section

### Expected Results

**With Nitro Module:**
```
Total: 7-15ms
Average: 0.07-0.15ms per call
Iterations: 100
```

**With TurboModule:**
```
Total: 50-100ms
Average: 0.5-1ms per call
Iterations: 100
```

**Improvement: 5-10x faster with Nitro! ⚡**

## 📊 Viewing Logs

### Console Logs (Metro)

```bash
# In Metro terminal, you'll see:
📱 SMS Retriever using: ⚡ Nitro Module
✅ OTP received: 123456
⏱️ Start listening took: 5ms
```

### Android Logcat

```bash
# View all SMS-related logs
adb logcat | grep -i "sms"

# View module-specific logs
adb logcat | grep "SMSRetriever\|HybridSMSRetriever"

# Or use test script
./test.sh
# Select option 6
```

## 🐛 Troubleshooting

### Issue 1: Module Not Found

```bash
# Rebuild library
cd ..
yarn build
cd example
yarn install
```

### Issue 2: SMS Not Received

**Check:**
- App hash matches the one in SMS
- SMS format is correct: "Your OTP is XXXXXX [HASH]"
- Permissions are granted
- Listening is active

**Solution:**
```bash
# Check permissions
adb shell dumpsys package com.smsretriever.example | grep permission

# Restart app
adb shell am force-stop com.smsretriever.example
yarn android
```

### Issue 3: Build Errors

```bash
# Full clean and rebuild
./test.sh
# Select option 4
```

### Issue 4: Nitro Module Not Loading

```bash
# Run nitrogen
cd ..
npx nitrogen

# Clean and rebuild
cd example
./test.sh
# Select option 4
```

## 📱 App Features

### Main Screen

- **Module Type**: Shows if using Nitro or TurboModule
- **App Hash**: Your app signature hash
- **Status**: Current listener status
- **Detailed Status**: Listening, Registered, Retry count
- **Performance**: Test results and timing info
- **Error**: Error messages if any
- **Received OTP**: Extracted OTP code

### Buttons

- **▶️ Start Listening**: Start SMS listener
- **⏹️ Stop Listening**: Stop SMS listener
- **🔄 Reset**: Clear all state
- **⚡ Performance Test**: Run benchmark test

### Console Output

```
📱 SMS Retriever using: ⚡ Nitro Module
✅ SMS Retriever initialized: { hash: 'FA+9qCX9VSu', status: {...} }
⏱️ Start listening took: 5ms
✅ OTP received: 123456
🛑 Stopped listening
🔄 Reset state
⚡ Performance Test Results: { total: 10, average: 0.1, iterations: 100 }
```

## 📚 SMS Format Examples

All these formats will work:

```
1. "Your OTP is 123456 FA+9qCX9VSu"
2. "OTP: 654321 FA+9qCX9VSu"
3. "Verification code 789012 FA+9qCX9VSu"
4. "PIN 456789 FA+9qCX9VSu"
5. "123456 is your code FA+9qCX9VSu"
6. "Use code 321654 FA+9qCX9VSu"
```

**Important**: Always include your app hash at the end!

## 🎯 Test Scenarios

### Scenario 1: Happy Path

1. Open app
2. Check module type (should be Nitro)
3. Copy app hash
4. Tap "Start Listening"
5. Send SMS with OTP
6. Verify OTP is extracted
7. Check console logs

**Expected**: ✅ All steps succeed

### Scenario 2: Timeout

1. Tap "Start Listening"
2. Wait 30 seconds without sending SMS
3. Check error message

**Expected**: ❌ Timeout error after 30s

### Scenario 3: Invalid SMS

1. Tap "Start Listening"
2. Send SMS without OTP: "Hello World [HASH]"
3. Check error message

**Expected**: ❌ Invalid SMS format error

### Scenario 4: Performance

1. Tap "⚡ Performance Test"
2. Wait for completion
3. Check results

**Expected**: ✅ Nitro ~10ms, TurboModule ~100ms

### Scenario 5: Stop/Reset

1. Start listening
2. Tap "Stop Listening"
3. Verify status changes
4. Tap "Reset"
5. Verify state cleared

**Expected**: ✅ All state cleared

## 📖 Documentation

- [Testing Guide](./TESTING_GUIDE.md) - Comprehensive testing guide
- [Nitro Migration](../NITRO_MIGRATION.md) - Migration guide
- [Implementation Summary](../IMPLEMENTATION_SUMMARY.md) - Technical details

## 🆘 Support

If you encounter issues:

1. Check [TESTING_GUIDE.md](./TESTING_GUIDE.md)
2. Review console logs
3. Check Android logcat
4. Use test script for automated testing
5. Create GitHub issue with:
   - Device info
   - Android version
   - Error logs
   - Steps to reproduce

## 📝 Notes

- SMS Retriever is **Android only**
- Requires Google Play Services
- Works on emulator and real devices
- Automatically detects Nitro/TurboModule
- Performance improvement with Nitro: **7x faster**

## 🎉 Success Indicators

✅ Module type detected correctly
✅ App hash displayed
✅ SMS received and OTP extracted
✅ Performance test shows improvement
✅ No errors in console
✅ All buttons work correctly

Happy testing! 🚀