# Hexy Launcher 2.0

An innovative Android launcher featuring a **hexagonal grid layout** with intelligent app sorting based on color, usage frequency, and recency.

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26-blue.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)

## ✨ Features

### 🎯 Smart Hexagonal Grid

- **Unique Layout**: Apps displayed in a hexagonal grid pattern instead of traditional rows/columns
- **Axial Coordinates**: Optimized mathematical rendering using axial coordinate system
- **Infinite Scroll**: Pan and navigate through all your apps seamlessly

### 🧠 Intelligent Sorting

- **Center Focus**: Your most-used app occupies the center hexagon
- **Recent Access**: Inner rings (1-2) display your 18 most recently used apps
- **Color Grouping**: Outer apps organized into 6 color buckets for intuitive visual clustering
- **Usage-Based**: Within each color group, frequently-used apps appear closer to center

### 🎨 Modern UI

- **System Icons**: Full, high-quality app icons without clipping or masking
- **Color Analysis**: Automatic dominant color extraction using Palette API
- **Smooth Interactions**: Gesture-based scrolling, tap to launch, long-press for options
- **Contextual Menu**: Hide apps or trigger uninstall directly from the grid

### ⚙️ Configurable Settings

- **Hex Spacing**: Adjust distance between hexagons (50-150dp)
- **Icon Size**: Scale app icons (50-150%)
- **Icon Padding**: Control spacing around each icon (0-20dp)

### 🌐 PWA & Shortcuts Support

- Integrated `ShortcutManager` for pinned web apps and system shortcuts
- Full support for Progressive Web Apps installed via Chrome/Edge

## 🚀 Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 26+ (Android 8.0 Oreo)
- Kotlin 1.9+

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/marcinkordas/hexylauncher2.0.git
   cd hexylauncher2.0
   ```

2. Open the project in Android Studio

3. Sync Gradle files (Android Studio will prompt you)

4. Run on device or emulator (API 26+)

### First Launch Setup

1. Grant **Usage Access** permission when prompted (required for usage statistics)
2. Press **Home** button and select "Hexy Launcher" as your default launcher
3. Access **Settings** via the gear icon to customize spacing and icon sizes

## 📐 Technical Architecture

### Core Components

- **HexCoordinate**: Axial coordinate system with ring calculations
- **HexGridCalculator**: Hex↔Pixel conversions and spiral generation
- **AppSorter**: Intelligent app placement algorithm
- **ColorExtractor**: Palette API integration with 6-bucket hue mapping
- **HexagonalGridView**: Custom View with gesture detection and rendering

### Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (ViewModel + LiveData)
- **UI**: Custom Views with Canvas drawing
- **Async**: Kotlin Coroutines
- **Color Analysis**: AndroidX Palette
- **Settings**: SharedPreferences with PreferenceManager

## 📱 Screenshots

_(Coming soon - build and run to see it in action!)_

## 🛠️ Configuration

Settings are accessible via the toolbar menu:

- **Hexagon Spacing**: Controls grid density
- **Icon Size**: Adjusts how large app icons appear
- **Icon Padding**: Adds breathing room around icons

All settings persist and apply immediately.

## 📄 Documentation

- [DEVELOPMENT_SPEC.md](DEVELOPMENT_SPEC.md) - Complete technical specification
- [TASK_LIST.md](TASK_LIST.md) - Development task breakdown

## 🤝 Contributing

Contributions are welcome! Feel free to open issues or submit PRs.

## 📝 License

This project is open source. See LICENSE file for details.

## 🙏 Acknowledgments

Built with inspiration from modern home screen designs and hexagonal grid mathematics.
