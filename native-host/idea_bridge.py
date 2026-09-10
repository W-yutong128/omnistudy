#!/usr/bin/env python3
"""Minimal Chrome Native Messaging host for an explicit OmniStudy IDEA handoff."""

import json
import os
from pathlib import Path
import struct
import subprocess
import sys


HOST_DIR = Path(__file__).resolve().parent
CONFIG_PATH = HOST_DIR / "idea_bridge.config.json"


def read_message():
    size_bytes = sys.stdin.buffer.read(4)
    if len(size_bytes) != 4:
        return None
    size = struct.unpack("<I", size_bytes)[0]
    if size > 1024 * 1024:
        raise ValueError("message too large")
    payload = sys.stdin.buffer.read(size)
    if len(payload) != size:
        raise ValueError("incomplete message")
    return json.loads(payload.decode("utf-8"))


def write_message(payload):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    sys.stdout.buffer.write(struct.pack("<I", len(data)))
    sys.stdout.buffer.write(data)
    sys.stdout.buffer.flush()


def inside(path, roots):
    return any(path == root or root in path.parents for root in roots)


def handle(message):
    if message.get("action") != "open":
        raise ValueError("unsupported action")
    if not CONFIG_PATH.exists():
        raise ValueError("missing idea_bridge.config.json")
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    roots = [Path(item).expanduser().resolve(strict=True) for item in config.get("allowedRoots", [])]
    if not roots:
        raise ValueError("allowedRoots cannot be empty")

    project_raw = message.get("projectPath")
    if not isinstance(project_raw, str) or not project_raw.strip():
        raise ValueError("projectPath is required")
    project = Path(project_raw).expanduser().resolve(strict=True)
    if not project.is_dir() or not inside(project, roots):
        raise ValueError("projectPath is outside allowedRoots")

    target = project
    file_raw = message.get("filePath")
    if isinstance(file_raw, str) and file_raw.strip():
        candidate = Path(file_raw).expanduser()
        target = (candidate if candidate.is_absolute() else project / candidate).resolve(strict=True)
        if not inside(target, [project]):
            raise ValueError("filePath is outside projectPath")

    idea = config.get("ideaCommand", "/Applications/IntelliJ IDEA.app/Contents/MacOS/idea")
    if not Path(idea).expanduser().exists():
        raise ValueError("configured IDEA command does not exist")
    subprocess.Popen([str(Path(idea).expanduser()), str(target)], start_new_session=True,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return {"success": True, "opened": str(target)}


def main():
    try:
        message = read_message()
        if message is not None:
            write_message(handle(message))
    except Exception as error:
        write_message({"success": False, "error": str(error)})


if __name__ == "__main__":
    main()
