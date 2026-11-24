"""Check repaired metadata and byte-for-byte preservation of all map assets."""
import io
import sys
import zipfile
import yaml


def entries(payload):
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        assert archive.testzip() is None
        return {name: archive.read(name) for name in archive.namelist()}


def verify(original, repaired):
    before, after = entries(original), entries(repaired)
    assert before.keys() == after.keys()
    assert before["world.slime"] == after["world.slime"]
    assert yaml.safe_load(after["map.yml"])["points"]["arena_origin"]
    ids = set()
    for name, payload in after.items():
        if not name.endswith(".dmap"):
            continue
        old, new = entries(before[name]), entries(payload)
        assert old.keys() == new.keys()
        assert old["map.schem"] == new["map.schem"]
        meta = yaml.safe_load(new["map.yml"])
        assert meta["id"] not in ids
        ids.add(meta["id"])
        assert meta["points"]["red_spawn"] and meta["points"]["blue_spawn"]
        assert meta["regions"]["arena"]
        if meta["id"] in ("arena", "court"):
            assert meta["regions"]["objective_a"] and meta["regions"]["objective_b"]
    assert ids == {"arena", "bastion", "castle", "court", "island", "oasis"}
    print("Passed six submap metadata checks; world and every schematic are unchanged.")


if __name__ == "__main__":
    from pathlib import Path
    verify(*(Path(path).read_bytes() for path in sys.argv[1:]))
