#!/usr/bin/env python3
"""Start the development backend with Java 21 and a private env file."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def main():
    root = Path(__file__).resolve().parents[1]
    config = Path(sys.argv[1]).expanduser() if len(sys.argv) > 1 else root / '.env.m2.local'
    if not config.is_absolute():
        config = root / config
    if not config.is_file():
        raise SystemExit(f'Private configuration missing: {config}')

    env = os.environ.copy()
    for number, line in enumerate(config.read_text().splitlines(), 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        key = key.strip()
        if not separator or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
            raise SystemExit(f'Invalid configuration at line {number}; use KEY=VALUE.')
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        # Values are literal data: no shell evaluation or variable expansion.
        env[key] = value
    for key in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET'):
        if not env.get(key):
            raise SystemExit(f'Missing configuration: {key}')

    cached = Path.home() / '.cache/campus-toolchains/jdk-21.0.12.1+1/Contents/Home'
    candidates = []
    if env.get('JAVA_HOME'):
        candidates.append(Path(env['JAVA_HOME']) / 'bin/java')
    candidates.append(cached / 'bin/java')
    system_java = shutil.which('java')
    if system_java:
        candidates.append(Path(system_java))
    for java in candidates:
        if not java.is_file():
            continue
        result = subprocess.run([str(java), '-version'], capture_output=True, text=True)
        if result.returncode == 0 and re.search(r'version "21(?:\.|\")', result.stderr + result.stdout):
            env['JAVA_HOME'] = str(java.parent.parent)
            env['PATH'] = str(java.parent) + os.pathsep + env.get('PATH', '')
            break
    else:
        raise SystemExit('Java 21 required. Set JAVA_HOME to your JDK 21 installation.')
    mvn = shutil.which('mvn', path=env['PATH'])
    if not mvn:
        raise SystemExit('Maven is missing from PATH.')
    print(f"Starting Java 21 backend on port {env.get('SERVER_PORT', '8080')}; credentials are not printed.", flush=True)
    os.chdir(root / 'backend')
    os.execve(mvn, [mvn, '-B', 'spring-boot:run'], env)


if __name__ == '__main__':
    main()
