# VICE CHANGER

**AI-Free Real-Time Female Voice Changer** — an Android voice changer for gaming and messaging.
No machine learning, no server, no account, no internet permission.

- Package: `com.vicechanger.app`
- Min SDK 29 (Android 10), target/compile SDK 36
- Kotlin + Jetpack Compose (Material 3), Gradle Kotlin DSL
- Developed by Jay J. Ababon — (c) 2026 VICE CHANGER

---

## What it actually does

**Live Voice** — a real-time pipeline on a dedicated audio thread:

```
microphone (AudioRecord, VOICE_COMMUNICATION)
   -> rumble filter
   -> pitch shift: phase vocoder (time stretch) + cubic resampler
   -> spectral stage: noise gate + independent formant warping
   -> EQ (chest resonance / presence / brightness)
   -> saturation -> optional robot modulation
   -> compressor -> output trim -> limiter
   -> AudioTrack (low-latency mode)
```

- Pitch and formant are shifted **independently**. A pitch multiply alone sounds like a
  chipmunk; the formant warp moves the vocal-tract resonances, which is what carries the
  female read.
- The formant warp uses a real-cepstrum lifter to isolate the spectral envelope, resamples
  that envelope along frequency, and re-applies it with the original phases.
- Everything is streaming: fixed 5-10 ms blocks, no allocation in the audio path, no work on
  the main thread.
- The limiter is the last stage, so no preset (including Custom Girl with every slider at
  maximum) can clip the output.

**Ten voices** — Natural Girl, Soft Girl, Cute Girl, Gamer Girl, Mature Woman, Bright Girl,
Deep Female, Anime Style, Robot Girl, Custom Girl. Presets are data (`VoicePreset`) mapped to
DSP parameters in one place (`VoiceTransformer`), so the picker, the sliders and the audio path
cannot drift apart.

**Custom Girl** — pitch, formant, brightness, resonance and effect-intensity sliders, with
saved custom voices stored on the device.

**Voice Message** — record, transform, preview, save (MediaStore, `Music/VICECHANGER`), share
through Android's own share sheet.

**Mobile Legends Mode** — a real device routing check plus a "Test Voice" that records four
seconds, transforms and plays back what a teammate would hear.

**Messenger Voice Mode** — record, transform, preview, and either hand the file to Messenger
(`ACTION_SEND` with `com.facebook.orca`) or to the generic share sheet.

---

## How to hear it (read this before judging the voice)

Monitoring decides whether you actually hear a female voice, and it is the part that made an
earlier build sound "like me, nothing happened":

- **Use headphones.** With a headset connected, echo reduction is switched off automatically and
  the monitor runs at full level - the configuration the DSP is tuned for. The transformed voice
  is then clearly audible.
- **On the phone speaker you always hear two things at once**: the transformed voice coming out
  of the speaker, and your own voice arriving directly through your head (bone conduction). The
  Live screen tells you which mode you are in, and echo reduction is bounded to 12 dB so the
  transformed voice can never be ducked into the background exactly while you speak.
- **Settings -> Diagnostics -> `RUN DSP SELF-TEST + PLAY RESULT`** proves the chain with no
  microphone involved: it synthesises a vowel, runs it through the real DSP chain with the voice
  you selected, prints the measured pitch / envelope / level change and plays the result.
  Measured on a Huawei P20 Pro (Android 10): `180.0 Hz -> 247.4 Hz (5.5 semitones measured, 5.5
  requested)`, `vocal tract envelope 716 Hz -> 1107 Hz`, `pipeline delay 20 ms`.
- If the self-test plays a shifted voice but Live Mode still sounds like you, the problem is
  monitoring (headset vs speaker, monitor volume), not the audio engine - which is exactly why
  the self-test exists.

---

## What it does NOT do (please read)

- **It cannot replace your microphone inside another app.** Mobile Legends, Messenger calls,
  Discord and every other app read the microphone stream from the system. A normal (non-rooted)
  Android app has no API to insert audio into that stream - and this is **not a permission you can
  grant**: an overlay (`SYSTEM_ALERT_WINDOW`), a foreground service, or any other Android
  permission changes nothing about another app's microphone. VICE CHANGER says so on screen
  instead of pretending, and the routing probe shows the honest verdict:
  *"Your device does not allow third-party microphone routing for this mode."*
- **No root exploits, no accessibility abuse, no hidden background microphone.** The engine is
  stopped when you leave the voice screens and again when the app goes to the background.
- **No latency marketing.** Live Mode shows the *measured* pipeline delay, computed from the
  real buffer occupancies of the capture buffer, the DSP stages and the output mixer.
  When the engine is stopped it shows `---`, not a number.
- **Noise suppression is a spectral gate, not a voice detector.** Measured with this build:
  steady hiss drops about 3 dB at the default setting, a perfectly steady tone settles at the
  gate floor (about 7 dB down), and speech-like bursts pass untouched (0.2 dB). A steady tone is
  indistinguishable from steady noise to a gate, and the app documents that rather than
  over-promising.
- **Echo reduction is a half-duplex ducker** for speaker monitoring: it ducks the microphone
  while the output is loud. It is not acoustic echo cancellation, and the UI says so.

---

## Permissions

`RECORD_AUDIO` only. No contacts, SMS, call logs, location, camera, storage or accessibility
permission. There is no `INTERNET` permission in the manifest at all, so nothing can leave the
device even by accident.

---

## Build

```bash
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<android-sdk>       # platforms android-36, build-tools 36.0.0
./gradlew assembleDebug                 # debug APK
./gradlew testDebugUnitTest             # 122 unit tests
./gradlew assembleRelease               # signed release APK (needs keystore.properties)
```

Release signing reads `keystore.properties` at the repo root (gitignored):

```properties
storeFile=vicechanger-release.jks
storePassword=...
keyAlias=vicechanger
keyPassword=...
```

Without that file the release build still runs, but the APK is left unsigned.

---

## Tests

`./gradlew testDebugUnitTest` — 107 JVM tests, no device required:

| Area | Covered |
|---|---|
| FFT | matches a naive DFT, inverse round trip, bin placement, DC |
| Overlap-add machinery | bit-exact passthrough at hop 256 and at the non-COLA hop 384, 20 s drift check |
| Pitch shift | +7 and -5 semitones measured on the output spectrum, 0 semitones is transparent |
| Formant warp | raises the spectral centroid without moving the harmonics; pitch stays independent |
| Noise gate | reduces steady noise, does not shift or destroy a tone, passes speech bursts, off = bypass |
| Dynamics | limiter ceiling, compressor gain reduction, saturation bounds, echo ducker, robot effect |
| Audio chain | every built-in preset finite, bounded and non-silent; extreme parameter sets stay finite |
| Presets | exactly ten, unique ids, clamped, robot wiring, custom save/load/delete round trips |
| Storage | settings codec round trip + clamping, WAV encode/parse/stream-write, file naming and numbering |
| Errors | every user-facing failure message exists, is specific and ends with a period |

Behaviour on a real device (microphone routing, actual acoustic output, share sheets) is
**not** covered by these tests — see the limitations above and test on hardware.

---

## Architecture

```
ui/            Compose screens, components (icons drawn with Canvas), theme
viewmodel/     AppViewModel (navigation, settings, voices), LiveViewModel, MessageViewModel
audio/         AudioEngine (thread + lifecycle), AudioProcessor (chain), AudioInput/Output,
               AudioDeviceMonitor, RoutingCapabilityProbe, AudioFailure (message contract)
audio/dsp/     Fft, Biquad, EqBank, StftStage, PhaseVocoderStage, SpectralStage,
               StreamingResampler, SampleFifo, Dynamics, LevelMeter
voice/         VoicePreset, VoicePresetManager, VoiceTransformer, OfflineRenderer
recording/     VoiceRecorder, WavCodec, AudioFileStore
settings/      AppSettings, SettingsManager (pure codec + store)
utils/         KeyValueStore, ShareIntents
```

The UI never touches audio directly: UI -> ViewModel -> AudioEngine -> AudioProcessor. The live
engine and the offline renderer share the exact same `AudioProcessor`, so a voice message sounds
like what Live Mode plays.
