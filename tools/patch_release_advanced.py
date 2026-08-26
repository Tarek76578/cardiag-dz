#!/usr/bin/env python3
from pathlib import Path
import sys

# Advanced no-Gradle patching for the verified Compose Release APK.
# This operates on apktool-generated smali, not source Kotlin and not arbitrary DEX bytes.
# Only exact UI string constants are changed; navigation/business logic is untouched.

REPLACEMENTS = {
    'Accueil': 'Home',
    'Garage': 'My Vehicles',
    'Historique': 'History',
}

TARGET_MARKERS = (
    'CarDiagExactApp',
    'HomeScreen',
    'ExactVehicleProfileScreen',
    'GuidedDiagnosisScreen',
)


def patch_tree(root: Path):
    files = list(root.rglob('*.smali'))
    target_files = []
    for p in files:
        try:
            text = p.read_text(encoding='utf-8', errors='ignore')
        except OSError:
            continue
        if any(marker in text for marker in TARGET_MARKERS):
            target_files.append((p, text))

    if not target_files:
        raise SystemExit('No verified CarDiag Compose smali classes found; refusing advanced patch.')

    counts = {k: 0 for k in REPLACEMENTS}
    changed_files = 0
    for p, text in target_files:
        original = text
        for old, new in REPLACEMENTS.items():
            # Patch only const-string values, never arbitrary identifiers/opcodes.
            old_line = f'const-string'
            lines = text.splitlines(keepends=True)
            out = []
            for line in lines:
                if old_line in line and f'"{old}"' in line:
                    line = line.replace(f'"{old}"', f'"{new}"')
                    counts[old] += 1
                out.append(line)
            text = ''.join(out)
        if text != original:
            p.write_text(text, encoding='utf-8')
            changed_files += 1

    if not any(counts.values()):
        raise SystemExit(f'No expected UI string anchors found in verified Compose classes: {counts}')

    print(f'Advanced Compose smali patch complete: files={changed_files}, replacements={counts}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: patch_release_advanced.py <decoded-apk-dir>')
    patch_tree(Path(sys.argv[1]))
