#!/usr/bin/env python3
"""Exercise only com.bitchat.droid.qa. This script erases that disposable QA app's data."""
import argparse
import os
from pathlib import Path
import shutil
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = 'com.bitchat.droid.qa'
PERMISSION_PACKAGES = {'com.google.android.permissioncontroller', 'com.android.permissioncontroller', 'com.google.android.packageinstaller', 'com.android.packageinstaller'}
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
args = parser.parse_args()

def adb(*words):
    result = subprocess.run([args.adb, '-s', args.serial, *words], capture_output=True, text=True, timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout)
    return result.stdout

def prepare_dump_helper():
    candidates = [Path(shutil.which(args.adb) or args.adb).resolve().parent.parent]
    candidates += [Path(os.environ[key]) for key in ['ANDROID_HOME', 'ANDROID_SDK_ROOT'] if os.environ.get(key)]
    sdk = next((p for p in candidates if (p / 'platforms').is_dir()), candidates[0])
    platforms = sorted((sdk / 'platforms').glob('android-*/android.jar'),
                       key=lambda p: int(re.search(r'android-(\d+)', str(p)).group(1)))
    compilers = sorted((sdk / 'build-tools').glob('*/d8'), key=lambda p: p.parent.name)
    assert platforms and compilers, 'Android SDK platform and build-tools are required'
    out = Path(__file__).resolve().parent.parent / 'app/build/qa-ui-driver'
    out.mkdir(parents=True, exist_ok=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(platforms[-1]),
                    '-d', str(out), str(Path(__file__).with_name('QaUiDump.java'))], check=True)
    subprocess.run([str(compilers[-1]), '--min-api', '28', '--lib', str(platforms[-1]),
                    '--output', str(out), str(out / 'QaUiDump.class')], check=True)
    adb('push', str(out / 'classes.dex'), '/data/local/tmp/outpost-qa-dump.dex')

def ui():
    for attempt in range(3):
        try:
            output = adb('shell',
                         'CLASSPATH=/data/local/tmp/outpost-qa-dump.dex:/system/framework/uiautomator.jar',
                         'app_process', '/system/bin', 'QaUiDump')
            start = output.find('<?xml')
            if start >= 0:
                return ET.fromstring(output[start:].strip())
        except RuntimeError as error:
            if not any(reason in str(error) for reason in ['Killed', 'No active Android window']):
                raise
        time.sleep(.5)
    raise AssertionError('Android did not return a fresh QA hierarchy')

def nodes(root, value, permissions=False):
    packages = PERMISSION_PACKAGES if permissions else {PACKAGE}
    return [n for n in root.iter('node') if n.get('package') in packages and
            (value in [n.get('text'), n.get('content-desc'), n.get('resource-id')] or
             value == 'EDIT' and 'EditText' in n.get('class', ''))]

def wait(value, timeout=35):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = ui()
        if nodes(root, value):
            return root
        time.sleep(.4)
    raise AssertionError('QA screen did not show ' + value)

def message_visible(root):
    return any(n.get('package') == PACKAGE and 'SyntheticSmokeMessage' in n.get('text', '')
               and 'EditText' not in n.get('class', '') for n in root.iter('node'))

def wait_message():
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        if message_visible(ui()):
            return
        time.sleep(.4)
    raise AssertionError('Synthetic QA message did not appear outside the composer')

def tap(value, permissions=False):
    root = ui() if permissions else wait(value)
    matches = nodes(root, value, permissions)
    assert len(matches) == 1, f'Expected one QA control {value!r}; no tap performed'
    n = matches[0]
    assert n.get('enabled') != 'false', 'QA control disabled'
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds', '')))
    assert x2 > x1 and y2 > y1
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def enter(value):
    tap('EDIT')
    # Wait for the IME transition before typing into the QA app.
    time.sleep(.6)
    adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    deadline = time.monotonic() + 8
    while time.monotonic() < deadline:
        root = ui()
        if any(n.get('text') == value for n in nodes(root, 'EDIT')):
            return
    raise AssertionError('QA keyboard input did not settle')

def launch():
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/com.bitchat.android.MainActivity')

def restart():
    adb('shell', 'am', 'force-stop', PACKAGE)
    launch()

def erase_qa():
    # PACKAGE is a constant, and selectors refuse nodes belonging to the real installation.
    tap('Options')
    tap('Erase account and all data')
    wait('Erase account and all app data?')
    tap('Erase everything')
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'pidof', PACKAGE], capture_output=True, text=True)
        if not result.stdout.strip():
            break
        time.sleep(.3)
    else:
        raise AssertionError('QA application reset did not stop its process')
    launch()
    wait('Create profile')
    print('PASS: full Android account reset returns to onboarding', flush=True)

prepare_dump_helper()
launch()
deadline = time.monotonic() + 60
while time.monotonic() < deadline:
    root = ui()
    if nodes(root, 'Create profile') or nodes(root, 'Options'):
        break
    time.sleep(.4)
else:
    raise AssertionError('QA app did not reach onboarding or inbox after launch')
if not nodes(root, 'Create profile'):
    # Repeated runs may reuse a previous disposable QA profile.
    erase_qa()
enter('OutpostQA')
tap('Create profile')
# Refuse optional/nearby prompts first. The app must stay usable for local account/group actions.
deadline = time.monotonic() + 45
settled_since = None
while time.monotonic() < deadline:
    root = ui()
    deny = [n for n in root.iter('node') if n.get('package') in PERMISSION_PACKAGES and n.get('resource-id', '').endswith('/permission_deny_button')]
    if not deny:
        deny = [n for n in root.iter('node') if n.get('package') in PERMISSION_PACKAGES and n.get('text') in ["Don't allow", 'Don’t allow', 'Deny']]
    if deny:
        assert any('Outpost QA' in n.get('text', '') for n in root.iter('node')), 'Unrelated permission prompt; no tap performed'
        n = deny[0]
        x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        settled_since = None
    elif nodes(root, 'New chat or group'):
        settled_since = settled_since or time.monotonic()
        if time.monotonic() - settled_since >= 3:
            break
    else:
        settled_since = None
wait('New chat or group')
services = adb('shell', 'dumpsys', 'activity', 'services', PACKAGE)
assert 'isForeground=true' not in services, 'Mesh service should not run without required permissions'
print('PASS: onboarding and required-permission refusal', flush=True)
name = 'ReleaseQA' + uuid.uuid4().hex[:6]
tap('New chat or group')
tap('New group')
enter(name)
tap('Create')
wait('Group invitation QR')
tap('Done')
tap(name)
enter('SyntheticSmokeMessage')
tap('Send')
wait_message()
restart()
wait_message()
tap(name)
wait_message()
print('PASS: optimized encrypted group history survives process restart', flush=True)
tap('Options')
tap('Clear chat')
tap('Clear chat')
assert not message_visible(ui())
restart()
tap(name)
assert not message_visible(ui())
print('PASS: cleared history stays deleted after restart', flush=True)
tap('Options')
tap('Leave group')
tap('Leave and clear')
assert not nodes(ui(), name)
erase_qa()
adb('shell', 'rm', '-f', '/data/local/tmp/outpost-qa-dump.dex')
print('PASS: QA smoke suite complete; original Outpost installation untouched', flush=True)
