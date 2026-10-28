#!/usr/bin/env python3
"""Offline structure/smoke checks; actual Android tests run in GitHub Actions."""
from pathlib import Path
r = Path(__file__).resolve().parent.parent
paths = [
    'settings.gradle.kts', 'app/build.gradle.kts',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/dev/chesspocket/MainActivity.kt',
    'app/src/main/java/dev/chesspocket/ChessViewModel.kt',
    'app/src/main/java/dev/chesspocket/ImportService.kt',
    'app/src/main/java/dev/chesspocket/ChessModels.kt',
    'app/src/main/java/dev/chesspocket/Stockfish.kt',
    'app/src/main/java/dev/chesspocket/LocalStorage.kt',
    'app/src/test/java/dev/chesspocket/PgnTest.kt',
    '.github/workflows/android-debug.yml',
]
for name in paths:
    p = r / name
    assert p.is_file() and p.stat().st_size > 0, name
assert 'assembleDebug' in (r/'.github/workflows/android-debug.yml').read_text()
assert 'Hash value 4' in (r/'app/src/main/java/dev/chesspocket/Stockfish.kt').read_text()
assert 'getJSONArray("archives")' in (r/'app/src/main/java/dev/chesspocket/ImportService.kt').read_text()
print(f'PASS: {len(paths)} required project files; debug workflow and engine settings found')
