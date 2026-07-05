# 2D Sheep Game

An interactive, polished 2D mobile game for Android built from scratch in Java using a standard `SurfaceView` game loop. The game features a nonchalant sheep on a minimal background, responding to user touches with smooth physics-based animations, particle systems, and audio feedback.

---

## 🎮 Interactive Mechanics

1. **Press & Hold Center (Swell)**
   - Press and hold the middle of the sheep's body to swell the sheep to look "fat" using the `fat_sheep` sprite and a smooth scaling interpolation.
   - Releasing the hold immediately reverts the sheep to normal size and cancels any angry state.

2. **Drag & Release Headwool (Angry Mode)**
   - Tap and drag the head wool area. The headwool separate sprite (`headwool.png`) detaches and follows your finger. The sheep body switches to the `without_headwool_sheep` sprite.
   - Upon release, the wool smoothly glides back to the head's anchor point. 
   - Once it snaps back, a random angry sprite (either `gun_sheep` or `knife_sheep`) is chosen, and the `meh.mp3` sound is played.
   - The angry state persists for **2 seconds** before the sheep automatically cools down and reverts to its calm, nonchalant state.

3. **Leg Tapping (Jump)**
   - Tap either the left or right leg hitboxes to trigger a snappy, gravity-based jump.
   - The jump uses realistic acceleration physics. Upon landing, a burst of soft dust particles is emitted from the sheep's feet.

---

## ✨ Premium Polish & Visuals

- **Dynamic Drop Shadow**: A soft oval shadow is rendered underneath the sheep. The shadow dynamically grows as the sheep swells, and shrinks/fades away as the sheep jumps high.
- **Dust Puff Particles**: Spawned at impact points on landing, drifting outward and fading away with simulated friction.
- **Squishy Scale Lerp**: Uses frame-rate-independent linear interpolation (capping Euler factors) to make the transitions feel bouncy, elastic, and organic rather than rigid.
- **Dynamic Hitbox Scaling**: All hitboxes and anchor points dynamically resize and translate according to the current active sprite and vertical jump offset.

---

## 🛠️ Project Structure

The project has been organized with a standard Android View-based Java architecture (Compose templates have been removed for efficiency):

```
Sheep Game/
├── README.md
└── sheepgame/
    ├── app/
    │   ├── src/main/
    │   │   ├── AndroidManifest.xml   # Fullscreen config, locks orientation to portrait
    │   │   ├── java/com/example/sheepgame/
    │   │   │   ├── MainActivity.java # Fullscreen Activity initialization
    │   │   │   └── GameView.java     # Game loop thread, touch controls, physics & drawing
    │   │   └── res/
    │   │       ├── drawable/         # Game sprites (normal, fat, gun, knife, bald, wool)
    │   │       └── raw/              # Audio files (meh.mp3)
    │   └── build.gradle.kts
    └── settings.gradle.kts
```

---

## 🚀 Building & Running

### Prerequisites
- Android SDK installed.
- Gradle daemon active (included with gradlew wrapper).

### 1. Compile the APK
To compile and build the debug APK, run the following command from the `sheepgame` folder:
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`sheepgame/app/build/outputs/apk/debug/app-debug.apk`

### 2. Deploy to Emulator/Device
If you have a connected Android device or running emulator, deploy the application using:
```bash
# Verify connection
adb devices

# Install and run
android run
```
