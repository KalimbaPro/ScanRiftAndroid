# ScanRift Android

A card collection manager, scanner, and deck builder for the **Riftbound** trading card game.

## Features

### Card Scanning
- Real-time card recognition using ML Kit OCR and CameraX
- Automatic card code extraction with confidence scoring
- Motion detection for stable captures
- Manual search and correction fallback

### Collection Management
- Track owned cards with quantities, conditions (Mint, Near Mint, etc.), and foil variants
- Organize cards into custom lists with color theming
- Grid and list view with search, sort, and multi-filter support
- Multi-select for bulk operations
- Ownership filters (All, Owned, Wishlist, etc.)

### Deck Builder
- Build decks with real-time validation against Riftbound rules
- Legend/Champion selection with domain identity checking
- Section management: Main (40+), Rune (12), Battlefield (3), Sideboard (up to 8)
- Copy limits, signature card limits, and domain constraints enforced

### Import & Export
- Export collections to CSV, JSON, or Riftbound.gg format
- Import from supported formats
- Share via system share sheet

### Data Sync
- Syncs card database from the [Riftcodex](https://api.riftcodex.com) API
- Falls back to bundled JSON data when offline
- Paginated sync with progress tracking

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM with ViewModels and StateFlow |
| Database | Room 2.8 |
| Networking | Retrofit 2.11 + Gson |
| Camera | CameraX 1.4 |
| OCR | ML Kit Text Recognition |
| Image Loading | Coil 2.7 |
| Preferences | DataStore |
| Min SDK | 26 (Android 8.0) |

## Project Structure

```
app/src/main/java/com/scanrift/android/
├── data/
│   ├── local/          # Room database, DAOs, entities, converters
│   ├── remote/         # Retrofit API interface and DTOs
│   └── repository/     # Data access and user preferences
├── service/
│   ├── scanning/       # OCR, card matching, motion detection, camera
│   ├── export/         # CSV, JSON, Riftbound.gg exporters
│   ├── import_/        # Collection import
│   ├── feedback/       # Haptic and sound feedback
│   └── DeckValidator.kt
├── ui/
│   ├── screens/        # Collection, Scanner, Decks, Settings, Onboarding
│   ├── components/     # Shared UI components
│   ├── navigation/     # Navigation graph and routes
│   └── theme/          # Colors, typography, theming
└── util/               # Constants and configuration
```

## Getting Started

### Prerequisites
- Android Studio Ladybug or later
- JDK 17
- Android SDK 35

### Build & Run
1. Clone the repository
   ```bash
   git clone https://github.com/<your-username>/ScanRiftAndroid.git
   ```
2. Open the project in Android Studio
3. Sync Gradle and run on a device or emulator (API 26+)

### Permissions
The app requires:
- **Camera** — for card scanning
- **Internet** — for syncing the card database
- **Vibrate** — for haptic feedback

## License

All rights reserved.
