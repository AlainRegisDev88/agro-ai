# agro-ai

Per-crop plant disease "expert" models for an offline Android app. The app ships
without models; a farmer picks their crops and the app downloads only those
`.tflite` experts (a few MB each), listed in `models/manifest.json`.

This repo holds the Python side:

1. **`dataset_factory.py`**: pulls the configured Kaggle/GitHub datasets, maps every
   raw label to a canonical `crop__condition`, validates, center-crops and
   resizes images to 224x224, removes duplicates, and writes one stratified
   train/val/test set per crop.
2. **`agro_ai.train.train`**: MobileNetV3-Large transfer learning (optional
   Coordinate Attention), with field-style augmentation.
3. **`agro_ai.export.tflite`**: FP16 and full-INT8 TFLite export, accuracy check of
   both, and the manifest entry the app reads.

## Where things are written

The repo lives in OneDrive, so images and models are written somewhere else:

| What | Default | Override |
|---|---|---|
| raw downloads, processed images | `C:/agro-data` (Kaggle: `/kaggle/working/agro-data`) | `AGRO_DATA_DIR` or `--data-dir` |
| trained models, `manifest.json` | `./models` (Kaggle: `/kaggle/working/models`) | `AGRO_MODELS_DIR` or `--models-dir` |

## Setup

Data pipeline (any Python 3.10+, including the local 3.14):

```bash
pip install -e ".[download,dev]"
```

Downloading outside Kaggle needs a Kaggle API token: kaggle.com → Settings →
API → *Create New Token*, save it as `%USERPROFILE%\.kaggle\kaggle.json`. For the
cassava competition, open its page and accept the rules first.

Training needs TensorFlow 2.16+, which does not support Python 3.14 yet and has
no GPU here, so training runs in a Kaggle notebook (below).

## Datasets

```bash
python dataset_factory.py --list-sources
```

| Source | Domain | Crops |
|---|---|---|
| cassava-leaf-disease-classification (competition) | field | cassava |
| smaranjitghose/corn-or-maize-leaf-disease-dataset | field | maize |
| shashwatwork/identifying-disease-in-tea-leafs | field | tea |
| nirmalsankalana/rocole-a-robusta-coffee-leaf-images-dataset | field | coffee |
| olaidegabriel/cashew-disease-detection-dataset | field | cashew |
| manhhoangvan/fieldplant (bounding boxes, cropped per box) | field | none until its labels are mapped |
| vipoooool/new-plant-diseases-dataset | lab | PlantVillage 14 crops |
| nirmalsankalana/plant-diseases-training-dataset | lab | PlantVillage-style |
| salmasyed1360/plant-diseases-100k-labelled-images | lab | PlantVillage-style |
| spmohanty/plantvillage-dataset (GitHub, disabled by default) | lab | PlantVillage 14 crops |

Sources live in [agro_ai/config/sources.yaml](agro_ai/config/sources.yaml) and the label
tables in [agro_ai/config/class_map.yaml](agro_ai/config/class_map.yaml). The label tables
were written from the dataset pages. **Check each source after its first download:**

```bash
python dataset_factory.py --inspect tea_sickness
```

This prints every raw label with its normalized key and where it maps. Rows marked
`** UNMAPPED **` are dropped (and listed in `report.json`) until you add them to
`class_map.yaml`. FieldPlant's table is empty on purpose: fill it from `--inspect fieldplant`.

## Building a crop dataset

```bash
python dataset_factory.py --crops cassava,maize
python dataset_factory.py --crops tea --limit 200          # quick smoke run
python dataset_factory.py --crops tomato --max-per-class 1500
```

Only sources whose labels map to the requested crops are downloaded. Output per crop
in `<data_dir>/processed/<crop>/`:

- `images/<condition>/<id>.jpg`: 224x224 RGB, center-cropped square
- `manifest.csv`: `path,label,source,domain,split,phash,original`
- `labels.txt`, `report.json`: counts, rejects, duplicates, unmapped labels, warnings

Rules worth knowing:

- **Test split = field photos only** whenever the crop has any, so the reported
  accuracy reflects what a phone camera sees. Classes with only lab images are
  flagged in the report.
- **Duplicates** (same perceptual hash) are kept once, preferring field images.
  This collapses identical copies across the overlapping PlantVillage uploads.
  Rotated or flipped copies in the augmented Kaggle version are *not* caught, so
  lab-only test numbers stay optimistic.
- Classes with fewer than 150 images are flagged.

## Training on Kaggle

Open [notebooks/kaggle_train_expert.ipynb](notebooks/kaggle_train_expert.ipynb) on Kaggle,
attach the datasets for your crop, set `CROP`, and run all. Or by hand:

```bash
python dataset_factory.py --crops cassava --no-download
python -m agro_ai.train.train --data /kaggle/working/agro-data/processed/cassava --mixed-precision
```

Lab-to-field transfer (pretrain on PlantVillage crops, fine-tune on field data):

```bash
python -m agro_ai.train.train --data .../processed/tomato .../processed/potato .../processed/apple --name pv_pretrain --no-export
python -m agro_ai.train.train --data .../processed/maize --init-from /kaggle/working/models/pv_pretrain/v1/model.keras
```

Each run writes `models/<crop>/v<N>/`:

| File | Purpose |
|---|---|
| `<crop>_int8.tflite` | default download: uint8 input, float32 probabilities |
| `<crop>_fp16.tflite` | fallback if INT8 loses more than 2 accuracy points |
| `<crop>_labels.txt` | class names in output order |
| `model.keras`, `metrics.json`, `history.json`, `train_config.json` | training record |

and adds an entry to `models/manifest.json` (labels, display names, input spec,
file sizes, sha256, which variant is recommended, measured accuracy).

## Model contract for the Android app

- Input `[1, 224, 224, 3]` RGB, pixel values 0-255 (uint8 for INT8, float32 for FP16).
  Center-crop the camera frame to a square and resize to 224. No mean/std
  normalization: it happens inside the model.
- Output `[1, num_classes]` float32 softmax, in the order of `labels`.

## Tests

```bash
pytest
```

The tests cover the data pipeline, metrics and manifest on synthetic images and run
without TensorFlow. Training and export run only on Kaggle and have not been run yet.
