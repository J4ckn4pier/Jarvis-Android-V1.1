from pathlib import Path

settings = Path("app/src/main/java/com/jarvis/mobile/SettingsActivity.java").read_text()
session = Path("app/src/main/java/com/jarvis/mobile/assistant/JarvisVoiceSession.java").read_text()

assert 'row("Voice Model"' not in settings, "Voice Model surface is misleading: runtime does not select a TTS voice/model"
assert 'row("Voice Speed"' in settings, "Expected truthful Voice Speed settings surface"
assert 'setTitle("Voice Speed")' in settings, "Voice Speed dialog title must match actual behavior"
assert 'putFloat("voice_rate"' in settings, "Voice Speed must persist the runtime-consumed speech-rate preference"
assert 'getFloat("voice_rate"' in session and 'setSpeechRate(rate)' in session, "Production voice runtime must consume voice_rate"
