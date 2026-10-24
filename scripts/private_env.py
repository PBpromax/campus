"""Read literal KEY=VALUE configuration; never evaluate shell syntax."""
from pathlib import Path
import re

DEEPSEEK_KEYS = {'DEEPSEEK_API_KEY', 'DEEPSEEK_MODEL'}


def read_env(path: Path, allowed=None):
    values = {}
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        key, value = key.strip(), value.strip()
        if not separator or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
            raise ValueError(f'Invalid configuration at line {number}; use KEY=VALUE.')
        if allowed is not None and key not in allowed:
            raise ValueError(f'Unexpected configuration key at line {number}.')
        if key in values:
            raise ValueError(f'Duplicate configuration key at line {number}.')
        if value.startswith(('"', "'")):
            if len(value) < 2 or value[-1] != value[0]:
                raise ValueError(f'Unclosed quote at line {number}.')
            value = value[1:-1]
        values[key] = value
    return values
