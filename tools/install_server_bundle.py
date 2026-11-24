"""Install a hash-verified bundle while the Minecraft server is stopped. Never starts it."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import zipfile


def digest(payload):
    return hashlib.sha256(payload).hexdigest()


def ensure_stopped(root):
    for process in Path("/proc").iterdir():
        if not process.name.isdigit():
            continue
        try:
            executable = (process / "cmdline").read_bytes().split(b"\0")[0]
            if b"java" in executable and (process / "cwd").resolve() == root:
                raise RuntimeError("Minecraft is running; stop it before replacing jars")
        except (PermissionError, FileNotFoundError, ProcessLookupError):
            pass


def install(root, bundle):
    root = root.resolve()
    ensure_stopped(root)
    with zipfile.ZipFile(bundle) as archive:
        assert archive.testzip() is None, "Corrupt deployment archive"
        manifest = json.loads(archive.read("manifest.json"))
        pending = []
        for entry in manifest:
            relative = Path(entry["path"])
            target = (root / relative).resolve()
            if relative.is_absolute() or root / "plugins" not in target.parents:
                raise ValueError("Deployment target must be inside plugins")
            payload = archive.read(entry["path"])
            if digest(payload) != entry["sha256"]:
                raise ValueError("Bundle checksum mismatch: " + entry["path"])
            if "before_sha256" in entry and digest(target.read_bytes()) != entry["before_sha256"]:
                raise ValueError("Server file changed since inspection: " + entry["path"])
            pending.append((entry, target, payload))
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    backup = root / "deployment-backups" / stamp
    backup.mkdir(parents=True)
    for entry, target, _ in pending:
        if not target.is_file():
            raise ValueError("Expected existing installation file: " + str(target))
        saved = backup / entry["path"]
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, saved)
    (backup / "manifest.json").write_text(json.dumps(manifest, indent=2))
    changed = []
    try:
        ensure_stopped(root)
        for entry, target, payload in pending:
            temporary = target.with_name(target.name + ".codex-staged")
            temporary.write_bytes(payload)
            temporary.chmod(target.stat().st_mode)
            os.replace(temporary, target)
            changed.append((entry, target))
        for entry, target, _ in pending:
            if digest(target.read_bytes()) != entry["sha256"]:
                raise RuntimeError("Installed checksum mismatch: " + entry["path"])
            print("VERIFIED", entry["sha256"], entry["path"])
    except Exception:
        for entry, target in reversed(changed):
            shutil.copy2(backup / entry["path"], target)
        raise
    print("BACKUP", backup)
    ensure_stopped(root)
    print("Server remains stopped. No start command was issued.")


if __name__ == "__main__":
    install(Path(sys.argv[1]), Path(sys.argv[2]))
