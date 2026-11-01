#!/usr/bin/env python3
"""Standalone verifier for a Sentinel Ledger export. Standard library only, no app needed.

Usage: python3 verify_ledger.py ledger.jsonl [saved_head_hash]

Checks every record's link to the one before it, recomputes each payload hash and record hash,
and, if given, compares the final hash with the head hash the owner saved off the phone.
Payload values must be strings, booleans, integers or null (floats are not canonicalised here).
"""
import hashlib
import json
import sys

GENESIS_PREV = "0" * 64


def esc(s):
    out = ['"']
    for c in s:
        if c == '"':
            out.append('\\"')
        elif c == "\\":
            out.append("\\\\")
        elif c == "\n":
            out.append("\\n")
        elif c == "\r":
            out.append("\\r")
        elif c == "\t":
            out.append("\\t")
        elif c == "\b":
            out.append("\\b")
        elif c == "\f":
            out.append("\\f")
        elif ord(c) < 0x20:
            out.append("\\u%04x" % ord(c))
        else:
            out.append(c)
    out.append('"')
    return "".join(out)


def canon(v):
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, str):
        return esc(v)
    if isinstance(v, dict):
        return "{" + ",".join(esc(k) + ":" + canon(v[k]) for k in sorted(v)) + "}"
    if isinstance(v, list):
        return "[" + ",".join(canon(x) for x in v) + "]"
    raise ValueError("unsupported value: %r" % (v,))


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    path = sys.argv[1]
    saved = sys.argv[2].strip().lower() if len(sys.argv) > 2 else None
    records = [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]
    prev_hash, prev_seq, last_hash = GENESIS_PREV, 0, None
    for r in records:
        n = r["seq"]
        if n != prev_seq + 1:
            return fail(n, "sequence jumps from %d to %d (a record is missing or reordered)" % (prev_seq, n))
        if r["prevHash"] != prev_hash:
            return fail(n, "record does not link to the record before it")
        if sha(canon(r["payload"])) != r["payloadHash"]:
            return fail(n, "payload was changed after it was written")
        body = {"seq": n, "ts": r["ts"], "type": r["type"], "summary": r["summary"],
                "payloadHash": r["payloadHash"], "prevHash": r["prevHash"]}
        if sha(canon(body)) != r["hash"]:
            return fail(n, "record contents were changed after it was written")
        prev_hash, prev_seq, last_hash = r["hash"], n, r["hash"]
    if saved is not None and last_hash != saved:
        print("HEAD MISMATCH: the log's head hash is %s, not the one you saved." % last_hash)
        return 1
    print("CHAIN VERIFIES: %d records, head hash %s" % (len(records), last_hash))
    if saved is None:
        print("Note: no saved head hash was given, so the log could still have been rewritten as a whole.")
    return 0


def fail(n, why):
    print("BROKEN at record %d: %s" % (n, why))
    return 1


if __name__ == "__main__":
    sys.exit(main())
