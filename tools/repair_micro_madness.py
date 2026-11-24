"""Repair the supplied Micro Madness metadata without changing world/schematic bytes.

Usage: python tools/repair_micro_madness.py input.dmap output.dmap
Requires PyYAML. Bounds are read from Sponge schematic NBT, never guessed.
"""
import gzip
import io
import json
import struct
import sys
import zipfile

import yaml


def schematic_size(payload):
    stream = io.BytesIO(gzip.decompress(payload))

    def number(fmt):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(">" + fmt)))[0]

    def string():
        return stream.read(number("H")).decode("utf-8")

    def tag(kind):
        if kind in range(1, 7):
            return number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind in (7, 11, 12):
            size = number("i") * {7: 1, 11: 4, 12: 8}[kind]
            return stream.read(size)
        if kind == 8:
            return string()
        if kind == 9:
            child, count = number("b"), number("i")
            return [tag(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                child = number("b")
                if child == 0:
                    return result
                name = string()
                result[name] = tag(child)
        raise ValueError(f"Unsupported NBT tag {kind}")

    kind = number("b")
    string()
    root = tag(kind)
    root = root.get("Schematic", root)
    return tuple(root[key] & 0xFFFF for key in ("Width", "Height", "Length"))


def packed(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, payload in entries.items():
            archive.writestr(name, payload)
    return output.getvalue()


def repair(source):
    with zipfile.ZipFile(io.BytesIO(source)) as archive:
        entries = {name: archive.read(name) for name in archive.namelist()}
    config = yaml.safe_load(entries["map.yml"])
    # Separate the arenas from the small lobby and allow ample height for towers.
    config.setdefault("points", {}).setdefault("arena_origin", [{"x": 128, "y": 64, "z": 0}])
    entries["map.yml"] = yaml.safe_dump(config, sort_keys=False).encode()
    ids = set()
    for name in list(entries):
        if not name.startswith("submaps/") or not name.endswith(".dmap"):
            continue
        with zipfile.ZipFile(io.BytesIO(entries[name])) as archive:
            sub = {entry: archive.read(entry) for entry in archive.namelist()}
        meta = yaml.safe_load(sub["map.yml"])
        expected = name.rsplit("/", 1)[-1].removesuffix(".dmap")
        meta["id"] = expected
        assert expected not in ids
        ids.add(expected)
        width, height, depth = schematic_size(sub["map.schem"])
        regions = meta.setdefault("regions", {})
        regions.setdefault("arena", [{"min": {"x": 0, "y": 0, "z": 0},
                                      "max": {"x": width - 1, "y": max(height - 1, 48), "z": depth - 1}}])
        # These exported goal corners were stored as points rather than regions.
        if expected in ("arena", "court"):
            for key in ("objective_a", "objective_b"):
                corners = meta["points"][key]
                regions.setdefault(key, [{
                    "min": {axis: min(point[axis] for point in corners) for axis in ("x", "y", "z")},
                    "max": {axis: max(point[axis] for point in corners) + (4 if axis == "y" else 0) for axis in ("x", "y", "z")},
                }])
        sub["map.yml"] = yaml.safe_dump(meta, sort_keys=False).encode()
        manifest = json.loads(sub["manifest.json"])
        manifest["id"] = expected
        sub["manifest.json"] = json.dumps(manifest, indent=2).encode()
        entries[name] = packed(sub)
        print(f"{expected}: schematic {width} x {height} x {depth}; regions {list(regions)}")
    return packed(entries)


if __name__ == "__main__":
    from pathlib import Path
    source, destination = map(Path, sys.argv[1:])
    if source.resolve() == destination.resolve():
        raise ValueError("Use a separate output path to preserve the original")
    destination.write_bytes(repair(source.read_bytes()))
