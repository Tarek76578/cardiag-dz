#!/usr/bin/env python3
from pathlib import Path
import sys

# This patch targets only the verified CarDiagDesignSystem constant block in the
# Release APK produced by run 32936420867. No method bodies/opcodes are changed.
PALETTE = {
    'FF1B2733': 'FF2563EB',  # primary
    'FF263746': 'FF1D4ED8',  # primary strong
    'FF8FA9BF': 'FF38BDF8',  # accent
    'FFB8CBD9': 'FF7DD3FC',  # accent bright
    'FF070B0F': 'FF070A0F',  # dark background
    'FF0D141B': 'FF0E141D',  # dark surface
    'FF131D26': 'FF151E2A',  # elevated surface
    'FF22303C': 'FF243244',  # dark border
    'FFE7EDF2': 'FFF1F5F9',  # dark foreground
    'FFA9B7C2': 'FFB8C4D1',  # dark muted foreground
    'FFE5EBF0': 'FFE8EEF5',  # light surface variant
    'FF17212A': 'FF111827',  # light foreground
    'FF52616D': 'FF526174',  # light muted foreground
    'FFD3DDE5': 'FFD7E0EA',  # light border
    'FFE85B68': 'FFEF4444',  # critical
    'FF39B982': 'FF22C55E',  # success
    'FFE6A23C': 'FFF59E0B',  # warning
    'FF40596D': 'FF334155',  # light secondary
    'FFB4233C': 'FFDC2626',  # light error
}

START_SIG = bytes.fromhex('33 27 1b ff 00 00 00 00 71 20 e3 53 10 00 0b 00 68 00 be 2b 18 00')


def patch(path: Path):
    b = bytearray(path.read_bytes())
    start = b.find(START_SIG)
    if start < 0:
        raise SystemExit('Verified CarDiag theme signature was not found; refusing to patch unknown DEX.')

    end = min(len(b), start + 520)
    region = b[start:end]
    counts = {}
    for old_hex, new_hex in PALETTE.items():
        old = bytes.fromhex(old_hex)[::-1]
        new = bytes.fromhex(new_hex)[::-1]
        c = region.count(old)
        counts[old_hex] = c
        if c:
            region = region.replace(old, new)

    required = [
        'FF1B2733', 'FF263746', 'FF8FA9BF', 'FFB8CBD9', 'FF070B0F',
        'FF131D26', 'FF22303C', 'FFE85B68', 'FF39B982', 'FFE6A23C'
    ]
    missing = [x for x in required if counts[x] != 1]
    if missing:
        raise SystemExit(
            f'Unexpected theme constant counts; refusing patch: {missing}; counts={counts}'
        )

    b[start:end] = region
    path.write_bytes(b)
    print(f'Patched verified theme block at DEX offset {start}; replacements={counts}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: patch_release_dex.py classes.dex')
    patch(Path(sys.argv[1]))
