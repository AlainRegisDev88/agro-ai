# Agro AI: Android app

This is the offline leaf-disease checker for farmers. It uses Kotlin and Jetpack Compose, and its look follows the Wise design language: lime CTAs, a sage canvas and white 24dp cards.

## Build and run on a phone

1. Install Android Studio. It comes with the Android SDK and JDK 21. The system JDK 25 is too new for AGP 8.7.
2. In Android Studio, choose **Open** and pick this `android/` folder. Gradle sync downloads everything it needs and creates the wrapper.
3. On the phone, go to Settings → About phone and tap *Build number* 7 times. Then open Developer options and turn on **USB debugging**.
4. Plug the phone in, accept the RSA prompt, then press **Run**. From a terminal you can run `./gradlew installDebug` instead.

Unit tests: `./gradlew testDebugUnitTest`

## How it fits the Python side

| App | Python |
|---|---|
| `ml/Preprocessor.kt`: EXIF-upright image, centre square crop, resized to 224, RGB values 0–255 | `agro_ai/data/images.py`, `train/dataset.py::load_uint8` |
| `ml/Classifier.kt`: reads the input dtype and quantization from the model, so it handles both the INT8 (uint8) and FP16 exports | `agro_ai/export/tflite.py` |
| `data/models/Manifest.kt`: parses schema 1 and picks the newest version per crop, then its `recommended` file | `agro_ai/export/manifest.py` |
| `assets/content/crops.json`: crops and labels | `agro_ai/config/class_map.yaml` |
| `assets/content/diseases.json`: guidance for each label (`crop__condition`) | written by hand and flagged `reviewed: false` |

Crops that have only one class (blueberry, raspberry, soybean, orange, squash) are left out of the app.

## Models

Models are downloaded from GitHub Releases: `BuildConfig.MODELS_BASE_URL` + `manifest.json`. Each `files[].path` in the manifest is fetched by its basename, because release assets are flat. The app checks every file's sha256 before installing it to `filesDir/models/<crop>/`.

**Debug builds** work before any model exists:
- If a crop has no model installed, the app falls back to a `FakeClassifier` and labels its results as samples.
- *Settings → Developer → Load model from file* installs a `.tflite` plus its `labels.txt` from the phone, for example a model you just pulled down from Kaggle.

## Design tokens

- `ui/theme/Color.kt` has the Wise palette and the light and dark colour roles.
- `ui/theme/Type.kt` has the display and body scale.
- `ui/theme/Shape.kt` has the radii and the 4dp spacing scale.
- Debug builds include a component gallery under *Settings → Developer*.
- Display text uses the system sans at weight 900 so the app needs no font download. To switch to Inter, add the TTFs to `res/font` and point `AgroFonts` at them.
