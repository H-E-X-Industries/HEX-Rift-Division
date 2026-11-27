#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Offline removal of trd:mud_cross (+ its trd:multiblock_part satellites) from a
Minecraft 1.21.1 (Anvil region) world. Pure-python; uses nbtlib + lz4.

Usage:
  python remove_mud_cross.py scan  <world_dir>
  python remove_mud_cross.py apply <world_dir>            (edits in place; make a backup first!)
"""
import io, os, sys, gzip, zlib, math, shutil, tempfile

import nbtlib

SECTOR = 4096
CONTROLLER_BLOCK = "trd:mud_cross"
PART_BLOCK = "trd:multiblock_part"
REMOVE_BLOCKS = {CONTROLLER_BLOCK, PART_BLOCK}
REMOVE_BE_IDS = {"trd:mud_cross_be", "trd:mud_cross", "trd:multiblock_part"}
AIR = "minecraft:air"

MASK64 = (1 << 64) - 1

# ---------------------------------------------------------------- blockpos long
def unpack_pos(l):
    l &= MASK64
    x = (l >> 38) & 0x3FFFFFF
    if x >= (1 << 25):
        x -= (1 << 26)
    y = l & 0xFFF
    if y >= (1 << 11):
        y -= (1 << 12)
    z = (l >> 12) & 0x3FFFFFF
    if z >= (1 << 25):
        z -= (1 << 26)
    return (x, y, z)

# ---------------------------------------------------------------- region io
def region_coords(path):
    name = os.path.basename(path)
    parts = name.split(".")
    # r.X.Z.mca
    return int(parts[1]), int(parts[2])

def find_region_dirs(world_dir):
    out = []
    for root, dirs, files in os.walk(world_dir):
        if os.path.basename(root) == "region":
            if any(f.startswith("r.") and f.endswith(".mca") for f in files):
                out.append(root)
    return out

def region_files(region_dir):
    return sorted(
        os.path.join(region_dir, f)
        for f in os.listdir(region_dir)
        if f.startswith("r.") and f.endswith(".mca")
    )

def _mcc_path(region_dir, region_x, region_z, index):
    cx = region_x * 32 + (index % 32)
    cz = region_z * 32 + (index // 32)
    return os.path.join(region_dir, "c.%d.%d.mcc" % (cx, cz))

def read_region(path):
    """Return list of 1024 entries. Each entry None or dict."""
    region_dir = os.path.dirname(path)
    region_x, region_z = region_coords(path)
    data = open(path, "rb").read()
    entries = [None] * 1024
    if len(data) < SECTOR * 2:
        return entries
    loc = data[:SECTOR]
    ts = data[SECTOR:SECTOR * 2]
    for i in range(1024):
        offset = int.from_bytes(loc[i * 4:i * 4 + 3], "big")
        sector_count = loc[i * 4 + 3]
        timestamp = int.from_bytes(ts[i * 4:i * 4 + 4], "big")
        if offset == 0 or sector_count == 0:
            continue
        base = offset * SECTOR
        if base + 4 > len(data):
            continue
        length = int.from_bytes(data[base:base + 4], "big")
        if length <= 0:
            continue
        ctype_byte = data[base + 4]
        external = bool(ctype_byte & 0x80)
        ctype = ctype_byte & 0x7F
        record_len = 4 + length
        raw_record = data[base:base + record_len]
        if external:
            mcc = _mcc_path(region_dir, region_x, region_z, i)
            body = open(mcc, "rb").read() if os.path.exists(mcc) else b""
        else:
            body = data[base + 5:base + 4 + length]
        entries[i] = {
            "index": i,
            "timestamp": timestamp,
            "ctype": ctype,
            "external": external,
            "body": body,
            "raw_record": raw_record,
            "mcc_path": _mcc_path(region_dir, region_x, region_z, i),
            "sector_count": sector_count,
        }
    return entries

def decompress(ctype, body):
    if ctype == 1:
        return gzip.decompress(body)
    if ctype == 2:
        return zlib.decompress(body)
    if ctype == 3:
        return body
    if ctype == 4:
        import lz4.block
        import lz4.frame
        # Minecraft LZ4 chunk format: sequence of blocks; try frame then raw
        try:
            return lz4.frame.decompress(body)
        except Exception:
            # MC uses raw LZ4 blocks with 4-byte little-endian uncompressed length header per block
            return _mc_lz4_decompress(body)
    raise ValueError("unknown compression type %d" % ctype)

def _mc_lz4_decompress(body):
    import lz4.block
    out = bytearray()
    i = 0
    n = len(body)
    while i + 4 <= n:
        block_len = int.from_bytes(body[i:i + 4], "little")
        i += 4
        if block_len < 0:
            raise ValueError("bad lz4 block")
        # token layout used by MC: 4-byte length of compressed block follows; we don't know uncomp size,
        # use lz4.block with store_size=False and a large buffer fallback
        comp_len = block_len
        comp = body[i:i + comp_len]
        i += comp_len
        try:
            out += lz4.block.decompress(comp, uncompressed_size=SECTOR * 1024 * 16)
        except Exception:
            out += lz4.block.decompress(comp)
    return bytes(out)

def parse_chunk(entry):
    raw = decompress(entry["ctype"], entry["body"])
    f = nbtlib.File.parse(io.BytesIO(raw))
    return f

def serialize_chunk(tag):
    out = io.BytesIO()
    tag.write(out)
    return out.getvalue()

def write_region(path, entries):
    """Rebuild region file. Unmodified entries keep raw_record verbatim."""
    tmp_fd, tmp_path = tempfile.mkstemp(suffix=".mca", dir=os.path.dirname(path))
    os.close(tmp_fd)
    try:
        loc = bytearray(SECTOR)
        ts = bytearray(SECTOR)
        body_out = bytearray()
        pos = SECTOR * 2
        for e in entries:
            if e is None:
                continue
            i = e["index"]
            ts[i * 4:i * 4 + 4] = int(e.get("timestamp", 0)).to_bytes(4, "big")
            record = e["raw_record"]
            # pad record to sector multiple
            pad = (-len(record)) % SECTOR
            record_padded = record + b"\x00" * pad
            if pos % SECTOR != 0:
                # shouldn't happen since we always pad; safety
                pad2 = (-pos) % SECTOR
                body_out += b"\x00" * pad2
                pos += pad2
            offset = pos // SECTOR
            sector_count = len(record_padded) // SECTOR
            if offset > 0xFFFFFF or sector_count > 0xFF:
                raise ValueError("region too large")
            loc[i * 4:i * 4 + 3] = offset.to_bytes(3, "big")
            loc[i * 4 + 3] = sector_count
            body_out += record_padded
            pos += len(record_padded)
        with open(tmp_path, "wb") as f:
            f.write(bytes(loc))
            f.write(bytes(ts))
            f.write(bytes(body_out))
        os.replace(tmp_path, path)
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

def make_chunk_record(tag):
    raw = serialize_chunk(tag)
    comp = zlib.compress(raw, 6)
    length = len(comp) + 1
    record = length.to_bytes(4, "big") + bytes([2]) + comp
    return record

# ---------------------------------------------------------------- blockstates packing
def bits_for(palette_len):
    if palette_len <= 1:
        return 0
    return max(4, (palette_len - 1).bit_length())

def unpack_data(longs, bits, count):
    if bits == 0:
        return [0] * count
    mask = (1 << bits) - 1
    per_long = 64 // bits
    out = []
    for idx in range(count):
        long_index = idx // per_long
        slot = idx % per_long
        word = longs[long_index] if long_index < len(longs) else 0
        word &= MASK64
        out.append((word >> (slot * bits)) & mask)
    return out

def pack_data(indices, bits, count):
    if bits == 0:
        return []
    per_long = 64 // bits
    n_longs = (count + per_long - 1) // per_long
    longs = [0] * n_longs
    mask = (1 << bits) - 1
    for idx, val in enumerate(indices[:count]):
        long_index = idx // per_long
        slot = idx % per_long
        longs[long_index] |= (val & mask) << (slot * bits)
    # convert to signed 64-bit two's complement
    signed = []
    for w in longs:
        w &= MASK64
        if w >= (1 << 63):
            w -= (1 << 64)
        signed.append(w)
    return signed

# ---------------------------------------------------------------- nbt helpers
def nbt_name(tag):
    return str(tag.get("Name", "")) if isinstance(tag, dict) else ""

def comp_get(tag, key, default=None):
    try:
        return tag[key]
    except Exception:
        return default

def be_pos(be):
    return (int(be.get("x", 0)), int(be.get("y", 0)), int(be.get("z", 0)))

# ---------------------------------------------------------------- world scan
def chunk_global(chunk, chunkx, chunkz):
    return chunkx * 16, chunkz * 16

def iter_section_cells(section):
    """yield (local_index, name, global_x_offset... ) handled by caller"""
    bs = comp_get(section, "block_states")
    if not bs:
        return None
    palette = comp_get(bs, "palette")
    if palette is None:
        return None
    palette = list(palette)
    data = comp_get(bs, "data")
    longs = [int(v) for v in data] if data is not None else []
    bits = bits_for(len(palette))
    indices = unpack_data(longs, bits, 4096)
    return palette, indices

def scan_world(world_dir, collect):
    """collect: dict with sets. Reads every chunk, no writes."""
    for region_dir in find_region_dirs(world_dir):
        for rpath in region_files(region_dir):
            rx, rz = region_coords(rpath)
            entries = read_region(rpath)
            for e in entries:
                if e is None:
                    continue
                try:
                    chunk = parse_chunk(e)
                except Exception as ex:
                    collect["errors"].append("%s idx=%d: %s" % (rpath, e["index"], ex))
                    continue
                cx = rx * 32 + (e["index"] % 32)
                cz = rz * 32 + (e["index"] // 32)
                base_x = cx * 16
                base_z = cz * 16
                sections = comp_get(chunk, "sections") or []
                for section in sections:
                    sec_y = int(section.get("Y", 0))
                    parsed = iter_section_cells(section)
                    if not parsed:
                        continue
                    palette, indices = parsed
                    names = [nbt_name(p) for p in palette]
                    for li, pname in enumerate(indices):
                        name = names[pname] if pname < len(names) else ""
                        if name not in REMOVE_BLOCKS:
                            continue
                        lx = li & 15
                        lz = (li >> 4) & 15
                        ly = (li >> 8) & 15
                        gx = base_x + lx
                        gz = base_z + lz
                        gy = sec_y * 16 + ly
                        pos = (gx, gy, gz)
                        if name == CONTROLLER_BLOCK:
                            collect["controllers"].add(pos)
                            collect["counts"][name] = collect["counts"].get(name, 0) + 1
                        elif name == PART_BLOCK:
                            collect["parts"].add(pos)
                            collect["counts"][name] = collect["counts"].get(name, 0) + 1
                # block entities
                for be in comp_get(chunk, "block_entities") or []:
                    bid = str(be.get("id", ""))
                    pos = be_pos(be)
                    if bid in REMOVE_BE_IDS or bid in REMOVE_BLOCKS:
                        collect["be"].setdefault(bid, []).append(pos)
                    if bid == "trd:multiblock_part":
                        ctrl = be.get("ControllerPos")
                        if ctrl is not None:
                            collect["part_ctrl"][pos] = unpack_pos(int(ctrl))
                        else:
                            collect["part_ctrl"][pos] = None
    return collect

def compute_removal(collect):
    controllers = collect["controllers"]
    parts = collect["parts"]
    part_ctrl = collect["part_ctrl"]
    removal = set(controllers)
    # satellites: explicit controller link
    for p in parts:
        c = part_ctrl.get(p)
        if c in controllers:
            removal.add(p)
    # satellites: 3x3 ring around each controller (same Y), only real multiblock_part cells
    for (cx, cy, cz) in controllers:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                n = (cx + dx, cy, cz + dz)
                if n in parts:
                    # don't steal a part owned by a *different* controller
                    owner = part_ctrl.get(n)
                    if owner is not None and owner != (cx, cy, cz) and owner not in controllers:
                        continue
                    removal.add(n)
    return removal

# ---------------------------------------------------------------- apply
def apply_removal_to_chunk(chunk, removal, base_x, base_z):
    changed = False
    sections = comp_get(chunk, "sections") or []
    for section in sections:
        bs = comp_get(section, "block_states")
        if not bs:
            continue
        palette = comp_get(bs, "palette")
        if palette is None:
            continue
        palette = list(palette)
        data = comp_get(bs, "data")
        longs = [int(v) for v in data] if data is not None else []
        old_bits = bits_for(len(palette))
        indices = unpack_data(longs, old_bits, 4096)
        sec_y = int(section.get("Y", 0))
        names = [nbt_name(p) for p in palette]
        # air index
        air_idx = None
        for i, nm in enumerate(names):
            if nm == AIR:
                air_idx = i
                break
        sec_changed = False
        for li in range(4096):
            cur = indices[li]
            name = names[cur] if cur < len(names) else ""
            if name not in REMOVE_BLOCKS:
                continue
            lx = li & 15
            lz = (li >> 4) & 15
            ly = (li >> 8) & 15
            pos = (base_x + lx, sec_y * 16 + ly, base_z + lz)
            if pos not in removal:
                continue
            if air_idx is None:
                air = nbtlib.Compound({"Name": nbtlib.String(AIR)})
                palette.append(air)
                names.append(AIR)
                air_idx = len(palette) - 1
            indices[li] = air_idx
            sec_changed = True
        if not sec_changed:
            continue
        # prune unused palette entries + remap
        used = set(indices)
        if len(used) < len(palette):
            remap = {}
            new_palette = []
            for i, p in enumerate(palette):
                if i in used:
                    remap[i] = len(new_palette)
                    new_palette.append(p)
            new_indices = [remap[i] for i in indices]
            palette = new_palette
            indices = new_indices
        bits = bits_for(len(palette))
        new_longs = pack_data(indices, bits, 4096)
        # set back
        bs["palette"] = nbtlib.List[nbtlib.Compound]([nbtlib.Compound(p) if not isinstance(p, nbtlib.Compound) else p for p in palette])
        if bits == 0:
            bs.pop("data", None)
        else:
            bs["data"] = nbtlib.LongArray(new_longs)
        changed = True
    # block entities
    block_entities = comp_get(chunk, "block_entities")
    if block_entities:
        kept = []
        removed_any = False
        for be in block_entities:
            pos = be_pos(be)
            bid = str(be.get("id", ""))
            # global pos for BE is already absolute in chunk NBT
            if pos in removal and (bid in REMOVE_BE_IDS or bid in REMOVE_BLOCKS):
                removed_any = True
                continue
            kept.append(be)
        if removed_any:
            chunk["block_entities"] = nbtlib.List[nbtlib.Compound]([k for k in kept])
            changed = True
    # scheduled ticks
    for key in ("block_ticks", "fluid_ticks", "pendingBlockTicks", "pendingFluidTicks"):
        lst = comp_get(chunk, key)
        if lst:
            kept = [t for t in lst if str(t.get("i", "")) not in REMOVE_BLOCKS]
            if len(kept) != len(lst):
                chunk[key] = nbtlib.List[nbtlib.Compound](kept)
                changed = True
    return changed

def apply_world(world_dir, removal):
    total_chunks_changed = 0
    cells_removed = 0
    for region_dir in find_region_dirs(world_dir):
        for rpath in region_files(region_dir):
            rx, rz = region_coords(rpath)
            entries = read_region(rpath)
            out_entries = []
            dirty = False
            for e in entries:
                if e is None:
                    out_entries.append(None)
                    continue
                try:
                    chunk = parse_chunk(e)
                except Exception:
                    out_entries.append(e)  # keep verbatim
                    continue
                cx = rx * 32 + (e["index"] % 32)
                cz = rz * 32 + (e["index"] // 32)
                before = count_targets(chunk, removal, cx * 16, cz * 16)
                if apply_removal_to_chunk(chunk, removal, cx * 16, cz * 16):
                    rec = make_chunk_record(chunk)
                    ne = dict(e)
                    ne["raw_record"] = rec
                    ne["external"] = False
                    out_entries.append(ne)
                    dirty = True
                    total_chunks_changed += 1
                    cells_removed += before
                else:
                    out_entries.append(e)
            if dirty:
                write_region(rpath, out_entries)
    return total_chunks_changed, cells_removed

def count_targets(chunk, removal, base_x, base_z):
    n = 0
    for section in comp_get(chunk, "sections") or []:
        parsed = iter_section_cells(section)
        if not parsed:
            continue
        palette, indices = parsed
        names = [nbt_name(p) for p in palette]
        sec_y = int(section.get("Y", 0))
        for li, pname in enumerate(indices):
            name = names[pname] if pname < len(names) else ""
            if name not in REMOVE_BLOCKS:
                continue
            lx = li & 15
            lz = (li >> 4) & 15
            ly = (li >> 8) & 15
            if (base_x + lx, sec_y * 16 + ly, base_z + lz) in removal:
                n += 1
    return n

# ---------------------------------------------------------------- main
def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    mode = sys.argv[1]
    world_dir = sys.argv[2]
    if not os.path.isdir(world_dir):
        print("world dir not found:", world_dir)
        sys.exit(1)

    collect = {
        "controllers": set(),
        "parts": set(),
        "part_ctrl": {},
        "be": {},
        "counts": {},
        "errors": [],
    }
    scan_world(world_dir, collect)
    removal = compute_removal(collect)

    print("world:", world_dir)
    print("region dirs:", find_region_dirs(world_dir))
    print("controllers trd:mud_cross:", len(collect["controllers"]))
    for p in sorted(collect["controllers"]):
        print("   controller @", p)
    print("multiblock_part cells:", len(collect["parts"]))
    print("block-entity ids seen:", {k: len(v) for k, v in collect["be"].items()})
    print("scan errors:", len(collect["errors"]))
    for e in collect["errors"][:10]:
        print("   ", e)
    print("=> total cells to clear:", len(removal))

    if mode == "scan":
        return
    if mode == "apply":
        if not removal:
            print("nothing to remove.")
            return
        changed, cells = apply_world(world_dir, removal)
        print("chunks modified:", changed, "cells cleared (pre-count):", cells)
    else:
        print("unknown mode", mode)
        sys.exit(1)

if __name__ == "__main__":
    main()
