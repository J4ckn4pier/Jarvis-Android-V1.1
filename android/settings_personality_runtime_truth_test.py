from pathlib import Path

settings = Path("app/src/main/java/com/jarvis/mobile/SettingsActivity.java").read_text(encoding="utf-8")
production_root = Path("app/src/main/java")

visible = 'row("Personality"' in settings
consumer_files = []
for path in production_root.rglob("*.java"):
    if path.name == "SettingsActivity.java":
        continue
    text = path.read_text(encoding="utf-8")
    if 'personality_label' in text:
        consumer_files.append(str(path))

assert (not visible) or consumer_files, (
    "Visible Personality setting persists personality_label but no non-Settings production runtime consumes it. "
    "Either wire it into runtime behavior or remove the unsupported control."
)

print("Settings Personality runtime truth verified", consumer_files)
