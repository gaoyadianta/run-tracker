# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RunTrack is a fitness tracking Android app built with Jetpack Compose, MVVM architecture, and modern Android technologies. It features GPS-based running tracking, AI companion functionality with Coze platform integration, and comprehensive running statistics management.

**Project Name**: RunTrack (中文名: AI跑伴)
**Target**: Fitness tracking app with AI voice companion for running

## Architecture

### Package Structure
- `ai/` - AI companion integration with Coze platform for voice interaction
- `background/` - Background services for tracking continuation
- `common/` - Utility classes and extensions used across the app
- `data/` - Data layer including Room database, repositories, and tracking services
- `di/` - Hilt dependency injection modules
- `domain/` - Domain layer with use cases, interfaces, and business models
- `ui/` - UI layer with Compose screens, navigation, and ViewModels

### Key Technologies
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: MVVM + Clean Architecture with UDF (Unidirectional Data Flow)
- **Database**: Room with coroutines support
- **Maps**: Dual map support (Google Maps & Amap for China)
- **Location**: Android Location Services with FusedLocationProvider
- **AI Integration**: Coze platform with WebRTC for real-time voice interaction
- **DI**: Hilt for dependency injection
- **Async**: Kotlin Coroutines and Flow

## Build & Development Commands

### Building the App
```bash
# Clean and build
./gradlew clean build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug
```

### Running Tests
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests="com.sdevprem.runtrack.utils.DateUtilsKtTest"
```

### Code Quality
```bash
# No explicit lint/format commands found - use standard Android Studio tools or add ktlint/detekt if needed
```

## Configuration Requirements

### Map Integration
1. **Google Maps**: Add `MAPS_API_KEY=your_maps_api_key` to `local.properties`
2. **Amap**: Add `AMAP_API_KEY=your_amap_key` to `local.properties`

### AI Companion (Coze Platform)
Update `app/src/main/res/values/strings.xml`:
```xml
<string name="coze_access_token">pat_your_actual_token_here</string>
<string name="coze_base_url">https://api.coze.cn</string>
<string name="coze_bot_id">your_actual_bot_id</string>
<string name="coze_voice_id">your_actual_voice_id</string>
```

## Core Systems

### Tracking Architecture
- **TrackingManager**: Central coordinator for location tracking, timing, and background services
- **LocationTrackingManager**: Handles GPS location updates with dual provider support
- **BackgroundTrackingManager**: Manages foreground service for continuous tracking
- **TimeTracker**: Manages running time with pause/resume functionality

### AI Companion System
- **AIRunningCompanionManager**: Main interface for AI voice interactions
- **CozeAPIManager**: Handles Coze platform API communication
- **AudioRouteManager**: Manages audio output routing (speaker/headphones)
- **Voice Broadcasting**: Supports encouragement, professional advice, milestone celebrations, and training feedback

### Data Layer
- **Room Database**: Local storage with RunDao for run history
- **DataStore**: User preferences and settings
- **Repository Pattern**: Clean data access abstraction

## Development Guidelines

### Location Tracking
- Uses FusedLocationProviderClient for GPS data
- Supports both Google Maps and Amap (for Chinese users)
- Background tracking via foreground service
- Path points include empty markers for pause segments

### AI Integration
- Real-time voice interaction during runs
- Auto-connects AI when starting runs (can be manually disconnected)
- Generates run summaries with auto-disconnect after playback
- WebRTC-based audio communication

### UI Components
- Material3 design with dynamic color support
- Compose-based navigation with nested navigation
- Custom map providers (GoogleMapProvider, AmapProvider)
- Real-time data display with proper state management

## Important Notes

- **Permissions Required**: Location (fine), microphone, internet, audio settings
- **Map Provider Factory**: Automatically selects appropriate map provider
- **Background Processing**: Uses foreground service with notification for tracking
- **State Management**: StateFlow-based reactive state management
- **Error Handling**: Comprehensive error handling for AI connection failures
- **Battery Optimization**: Includes battery optimization dialog for reliable background operation

## Recent Features

The app recently integrated AI companion functionality with automatic run-AI linking, voice interaction during runs, and automatic summary generation with smart disconnection after playback completion.