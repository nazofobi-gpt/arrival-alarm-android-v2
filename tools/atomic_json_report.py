"""Write verified JSON reports without exposing partially written output."""

import json
import os
import tempfile
from pathlib import Path


def write_atomic_json_report(destination: str | Path, payload: dict) -> None:
    """Replace a report only after its complete JSON has reached disk.

    The staging file lives beside the destination so os.replace is atomic on
    the same filesystem. An unsuccessful serialization/flush/replace retains
    the previous completed report and leaves no temporary file behind.
    """
    path = Path(destination)
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=path.parent,
            prefix=f".{path.name}.", suffix=".tmp", delete=False,
        ) as output:
            temporary_path = Path(output.name)
            json.dump(payload, output, ensure_ascii=False, indent=2)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
