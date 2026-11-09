package com.agroai.app

import com.agroai.app.data.models.Manifests
import com.agroai.app.data.models.sha256Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManifestTest {
    // Shape written by agro_ai/export/tflite.py + manifest.py.
    private val json = """
        {"schema": 1, "models": [
          {"name": "cassava", "version": 1, "crop": "cassava",
           "labels": ["cassava__healthy", "cassava__mosaic_disease"],
           "display_names": {"cassava__healthy": "Healthy", "cassava__mosaic_disease": "Mosaic disease"},
           "input": {"height": 224, "width": 224, "channels": 3, "color": "RGB", "range": "0-255", "preprocess": "..."},
           "output": {"type": "float32", "shape": [1, 2], "meaning": "softmax"},
           "files": [
             {"variant": "int8", "path": "cassava/v1/cassava_int8.tflite", "bytes": 4000000, "sha256": "aa", "input_dtype": "uint8"},
             {"variant": "fp16", "path": "cassava/v1/cassava_fp16.tflite", "bytes": 8000000, "sha256": "bb", "input_dtype": "float32"}
           ],
           "recommended": "fp16",
           "metrics": {"eval_split": "test", "eval_domains": ["field"], "keras_accuracy": 0.9, "int8_accuracy": 0.85, "fp16_accuracy": 0.89, "int8_macro_f1": 0.8},
           "created": "2026-10-01T00:00:00+00:00"},
          {"name": "cassava", "version": 2, "crop": "cassava", "labels": ["cassava__healthy", "cassava__mosaic_disease"],
           "files": [{"variant": "int8", "path": "cassava/v2/cassava_int8.tflite", "bytes": 4100000, "sha256": "cc"}],
           "recommended": "int8"}
        ]}
    """.trimIndent()

    @Test
    fun parsesAndPicksNewestVersion() {
        val manifest = Manifests.parse(json)
        assertEquals(2, Manifests.latest(manifest, "cassava")!!.version)
        assertNull(Manifests.latest(manifest, "maize"))
    }

    @Test
    fun recommendedFileAndAccuracy() {
        val v1 = Manifests.parse(json).models.first { it.version == 1 }
        val file = Manifests.recommendedFile(v1)
        assertEquals("fp16", file.variant)
        assertEquals(0.89, Manifests.accuracy(v1, file.variant)!!, 1e-9)
    }

    @Test
    fun releaseAssetNameIsBasename() {
        assertEquals("cassava_int8.tflite", Manifests.assetName("cassava/v1/cassava_int8.tflite"))
        assertEquals("manifest.json", Manifests.assetName("manifest.json"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        Manifests.parse("""{"schema": 2, "models": []}""")
    }

    @Test
    fun sha256OfKnownInput() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".byteInputStream()),
        )
    }
}
