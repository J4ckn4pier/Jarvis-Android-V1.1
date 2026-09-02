from pathlib import Path

settings = Path("app/src/main/java/com/jarvis/mobile/SettingsActivity.java").read_text(encoding="utf-8")
production_root = Path("app/src/main/java")

visible = 'row("Profile"' in settings
consumer_files = []
for path in production_root.rglob("*.java"):
    if path.name == "SettingsActivity.java":
        continue
    text = path.read_text(encoding="utf-8")
    if 'profile_name' in text:
        consumer_files.append(str(path))

assert (not visible) or consumer_files, (
    "Visible Profile setting persists profile_name but no non-Settings production runtime consumes it. "
    "Either wire it into runtime behavior or remove the unsupported control."
)

print("Settings Profile runtime truth verified", consumer_files)
