# Real-Time Transcription Implementation Guide

## Quick Start

### 1. Prepare Your Model

```bash
# Convert your model to TFLite format
# Using Python:
python3 << 'EOF'
import tensorflow as tf

# Load your model
model = tf.keras.models.load_model('your_model.h5')

# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# Save
with open('speech_recognition.tflite', 'wb') as f:
    f.write(tflite_model)
EOF
```

### 2. Add Model to Assets

```bash
mkdir -p app/src/main/assets/models
cp speech_recognition.tflite app/src/main/assets/models/
```

### 3. Update Fragment

**In TranscribeFragment.kt**, the implementation is ready to use. Just ensure:
- Audio permission is granted
- Model file exists in assets
- Vocabulary matches your model

### 4. Test

```bash
# Build and run
./gradlew installDebug
adb shell am start -n com.example.cosmicwhisper/.presentation.activities.MainActivity
```

## Architecture Details

### Audio Pipeline

**Step 1: Capture (AudioRecorder)**
```kotlin
val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,
    SAMPLE_RATE = 16000,
    CHANNELS = MONO,
    FORMAT = PCM_16BIT,
    buffer_size
)
```

**Step 2: Process Frames**
- Frame size: 512 samples = 32ms @ 16kHz
- Buffer: Last 99 frames (sliding window)
- Speech detection: RMS energy > 300

**Step 3: Extract Features**
```kotlin
// Mel-Spectrogram (40 bins)
// Input shape: [40, 99] = [mel_bins, context_frames]
val melFeatures = extractMelSpectrogram(audioBuffer)
```

**Step 4: Run Model**
```kotlin
// TFLite inference
val inputArray = FloatArray(40 * 99)  // Flatten mel-spec
interpreter.run(inputArray, outputArray)
val tokenIds = parseOutput(outputArray)
```

**Step 5: Map to Words**
```kotlin
val words = tokenIds.map { vocabulary[it] }
val transcription = words.joinToString(" ")
onTranscriptionUpdate(transcription)  // UI callback
```

### Threading Model

```
┌─────────────────────────────────────────┐
│         Main Thread (UI)                 │
│  - Fragment lifecycle                   │
│  - TextView updates                     │
│  - User interactions                    │
└──────────────┬──────────────────────────┘
               │
               │ Launch coroutine
               ↓
┌─────────────────────────────────────────┐
│    Default Thread (Transcription)        │
│  - Audio recording                      │
│  - Model inference                      │
│  - Word assembly                        │
└──────────────┬──────────────────────────┘
               │
               │ Post callbacks
               ↓
┌─────────────────────────────────────────┐
│      Main Thread (UI Update)             │
│  - onTranscriptionUpdate()               │
│  - Auto-scroll text view                │
└─────────────────────────────────────────┘
```

### Database Schema

```sql
CREATE TABLE transcriptions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);

-- Queries:
SELECT * FROM transcriptions ORDER BY timestamp DESC;  -- History
INSERT INTO transcriptions (text, timestamp) VALUES (?, ?);
DELETE FROM transcriptions WHERE id = ?;
```

## Real-Time Characteristics

### Latency Breakdown

| Component | Time | Notes |
|-----------|------|-------|
| Audio capture | 32ms | 512 samples @ 16kHz |
| Feature extraction | 5ms | Mel-spectrogram computation |
| Model inference | 10-20ms | Varies by device |
| Word mapping | <1ms | Lookup operation |
| UI update | 1-5ms | Main thread post |
| **Total** | **50-60ms** | End-to-end |

### Memory Usage

```
Audio Buffer:     512 * 99 * 2 bytes = 100KB
Mel Features:     40 * 99 * 4 bytes = 16KB
TFLite Model:     Typically 10-200MB (model size dependent)
Word Buffer:      Typically <50KB
Database:         Grows with transcriptions

Total RAM Peak:   ~300-400MB
```

## Customization

### Change Model

```kotlin
// In RealtimeTranscriber.kt
private val modelPath = "models/your_model.tflite"

// Load different model
val interpreter = Interpreter(loadModelFile(modelPath))
```

### Custom Vocabulary

```kotlin
// In SpeechRecognitionEngine.kt
private fun initializeVocabulary() {
    val words = listOf(
        "word1", "word2", "word3", ...
    )
    vocabulary.clear()
    words.forEachIndexed { index, word ->
        vocabulary[word] = index
    }
}
```

### Adjust Detection Sensitivity

```kotlin
// In AudioRecorder.kt
const val SILENCE_THRESHOLD = 200  // Increase for louder threshold
const val SILENCE_FRAMES_THRESHOLD = 50  // More frames = longer pause

// In RealtimeTranscriber.kt
setConfidenceThreshold(0.6f)  // Higher = fewer false positives
```

## Testing

### Unit Tests

```kotlin
@Test
fun testVocabularyMapping() {
    val engine = SpeechRecognitionEngine(mockListener)
    val tokens = listOf(0, 1, 2)
    val words = engine.tokensToWords(tokens)
    assertEquals(listOf("hello", "world", "test"), words)
}

@Test
fun testSpeechDetection() {
    val recorder = AudioRecorder()
    val audioFrame = generateTestAudio(300)  // RMS = 300
    assertTrue(recorder.isSpeechDetected(audioFrame))
}
```

### Integration Tests

```kotlin
@Test
fun testRealTimeTranscription() = runTest {
    val transcriber = RealtimeTranscriber(
        context, 
        onUpdate = { /* verify text */ },
        onError = { fail(it) }
    )
    
    transcriber.initialize()
    transcriber.startTranscription()
    
    // Wait for results
    delay(5000)
    
    val result = transcriber.stopTranscription()
    assertTrue(result.isNotEmpty())
}
```

## Debugging

### Enable Detailed Logging

```kotlin
// In RealtimeTranscriber.kt
private fun logDebugInfo() {
    Log.d("Transcriber", "Frame: $frameCounter")
    Log.d("Transcriber", "Speech detected: $hasSpeechStarted")
    Log.d("Transcriber", "Current text: ${currentTranscription}")
    Log.d("Transcriber", "Word count: ${wordBuffer.size}")
}
```

### Check Model Loading

```kotlin
try {
    val interpreter = Interpreter(modelFile)
    Log.d("TFLite", "Model loaded: ${interpreter.inputTensor(0).dataType()}")
    Log.d("TFLite", "Input shape: ${interpreter.inputTensor(0).shape().joinToString("x")}")
} catch (e: Exception) {
    Log.e("TFLite", "Model loading failed", e)
}
```

### Profiling

```kotlin
import android.os.Trace

val startTime = System.nanoTime()
// Your code
val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
Log.d("Performance", "Operation took: ${elapsedMs}ms")
```

## Best Practices

✅ **DO:**
- Use 16kHz sample rate (standard for ASR)
- Batch process frames (4-8 frames) for accuracy
- Filter by confidence threshold
- Auto-scroll to latest transcription
- Save to database after recording
- Test on actual devices
- Use Kotlin coroutines for async work
- Handle permissions properly

❌ **DON'T:**
- Use 44.1kHz or high sample rates (unnecessary overhead)
- Update UI from recognition thread directly
- Leak audio recorder resources
- Ignore confidence scores
- Store entire audio buffer in memory
- Hardcode file paths
- Assume microphone is available

## Performance Optimization

### Profile Memory
```kotlin
val runtime = Runtime.getRuntime()
val usedMemory = runtime.totalMemory() - runtime.freeMemory()
Log.d("Memory", "Used: ${usedMemory / 1024 / 1024}MB")
```

### Reduce Model Size
```python
# Quantization (Python)
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
```

### Parallel Processing
```kotlin
// Process multiple frames simultaneously
val jobs = (0 until 4).map { i ->
    launch {
        processAudioFrame(frames[i])
    }
}
witContext(Dispatchers.Main) {
    // Update all results
}
```

---

**Last Updated:** April 2026
**Version:** 1.0
