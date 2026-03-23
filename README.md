# TravelX

A real-time location sharing and communication app for road trips.

## Features
- Real-time location sharing with trip participants
- Group chat functionality
- Walkie-talkie style audio communication
- Trip management and coordination
- Google Maps integration for navigation

## Setup Instructions

### Prerequisites
- Android Studio
- Google Maps API Key
- Firebase Project

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/RoadTripCompanion.git
   cd RoadTripCompanion
   ```

2. **Configure API Keys**
   - Copy `local.properties.template` to `local.properties`
   - Fill in your actual API keys in `local.properties`:
     ```properties
     GOOGLE_MAPS_API_KEY=your_google_maps_api_key_here
     FIREBASE_API_KEY=your_firebase_api_key_here
     ```

3. **Configure Firebase**
   - Copy `app/google-services.json.template` to `app/google-services.json`
   - Replace the template values with your actual Firebase project configuration
   - You can download the actual `google-services.json` from your Firebase console

4. **Build and Run**
   - Open the project in Android Studio
   - Sync the project with Gradle
   - Build and run the app

## Security Notes

- **NEVER commit `local.properties` or `google-services.json` to version control**
- These files contain sensitive API keys and should remain local to your development environment
- The `.gitignore` file is configured to exclude these sensitive files
- A pre-commit hook is included in `.git/hooks/pre-commit` to prevent accidental commits of sensitive data
- See [SECURITY.md](SECURITY.md) for comprehensive security guidelines

## API Keys Required

1. **Google Maps API Key**
   - Enable Google Maps SDK for Android
   - Enable Places API
   - Add to `local.properties` as `GOOGLE_MAPS_API_KEY`

2. **Firebase Configuration**
   - Create a Firebase project
   - Enable Authentication (Anonymous)
   - Enable Firestore Database
   - Download `google-services.json` and place in `app/` directory

## Contributing

When contributing to this project, ensure you:
1. Do not commit any API keys or sensitive configuration
2. Test your changes thoroughly
3. Follow the existing code style and patterns
