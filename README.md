# JourneyHub

JourneyHub is an Android app for exploring destinations and planning public-transit journeys on Google Maps. Search for a destination, tap any point on the map, or use your current location as the route origin.

## Features

- Google Maps view centered on the default JourneyHub area
- Current-location support with precise or approximate Android location permission
- Destination search using Android geocoding
- Destination selection by tapping the map
- Public-transit directions from the current location
- Walking and transit route polylines with distinct colors
- Transit instructions showing line, arrival time, and stop count
- Scrollable route instructions and user-facing loading/error states

## Technology

- Java 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Android SDK 36
- Minimum Android SDK 30
- AndroidX and Material Components
- Google Maps SDK, Places SDK, Location Services, and Directions API

## Requirements

- Android Studio with a JDK 17 installation
- An Android SDK platform and build tools for API 36
- A Google Cloud project with billing enabled
- API access for:
  - Maps SDK for Android
  - Geocoding or Places services, as applicable to your project
  - Directions API

## Configuration

The app reads the Maps key through the Google Secrets Gradle Plugin. Create a `local.properties` file in the project root if your Android Studio setup does not already provide one:

```properties
MAPS_API_KEY=your_google_maps_api_key
```

Restrict the key in Google Cloud Console to the Android application package:

```text
com.example.journeyhub
```

Use the appropriate application restriction and API restrictions for your development and release keys. Never commit a real API key.

## Build and run

From the project root:

```powershell
.\gradlew.bat assembleDebug
```

To install on a connected device or emulator:

```powershell
.\gradlew.bat installDebug
```

Open the project in Android Studio and run the `app` configuration for an interactive session. Grant location access when prompted. Search for a destination or tap the map, then choose **Get Directions**.

## Project structure

```text
JourneyHub/
├── app/
│   ├── src/main/java/com/example/journeyhub/MainActivity.java
│   ├── src/main/res/layout/activity_main.xml
│   └── src/main/AndroidManifest.xml
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle
```

## Development notes

Network work for geocoding and transit directions runs off the main thread. Map rendering and UI updates return to the Android main thread. The app requires a valid Google Maps key and network access for search and directions.

The default route origin comes from the device's last known location. If no location is available yet, enable location services and try again after the map has obtained a fix.

## Validation

The project is configured for SDK 36 and Java 17. Run the debug build before testing on a device:

```powershell
.\gradlew.bat clean assembleDebug
```
