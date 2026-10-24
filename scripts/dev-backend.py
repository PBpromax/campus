#!/usr/bin/env python3
"""Start the development backend with Java 21 and a private env file."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from private_env import read_env, DEEPSEEK_KEYS


def main():
    root = Path(__file__).resolve().parents[1]
    config = Path(sys.argv[1]).expanduser() if len(sys.argv) > 1 else root / '.env.m2.local'
    if not config.is_absolute():
        config = root / config
    if not config.is_file():
        raise SystemExit(f'Private configuration missing: {config}')

    env = os.environ.copy()
    try:
        env.update(read_env(config))
        deepseek = root / '.env.deepseek.local'
        if deepseek.is_file():
            # Only model credentials/settings may be overridden by this separate file.
            env.update(read_env(deepseek, DEEPSEEK_KEYS))
    except (OSError, ValueError) as error:
        raise SystemExit(str(error)) from None
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
    # Optional, read-only Cangjie component built from this checkout. No secret is passed to it.
    component = root / 'cangjie/task-preview/build/task-preview'
    component_runtime = Path.home() / '.cache/campus-toolchains/cangjie-1.2.0/cangjie/runtime/lib/darwin_aarch64_cjnative'
    if component.is_file() and component_runtime.is_dir():
        env.setdefault('TASK_PREVIEW_BINARY', str(component))
        env.setdefault('TASK_PREVIEW_RUNTIME', str(component_runtime))
    mvn = shutil.which('mvn', path=env['PATH'])
    if not mvn:
        raise SystemExit('Maven is missing from PATH.')
    print(f"Starting Java 21 backend on port {env.get('SERVER_PORT', '8080')}; credentials are not printed.", flush=True)
    os.chdir(root / 'backend')
    os.execve(mvn, [mvn, '-B', 'spring-boot:run'], env)


if __name__ == '__main__':
    main()
