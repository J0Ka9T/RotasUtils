"""Merge translation keys into the shipped language files.

Usage: python scripts/addlang.py <additions.json>

The additions file is {"en_us": {key: value, ...}, "th_th": {...}}. Existing keys are overwritten,
new ones are appended, and both files keep their 2-space JSON formatting so the parity test and a
diff both stay readable.
"""
import collections
import io
import json
import sys

LANG = "common/src/main/resources/assets/rotasutils/lang/%s.json"


def main(path):
    with io.open(path, encoding="utf-8") as handle:
        additions = json.load(handle)
    for language, extra in additions.items():
        target = LANG % language
        with io.open(target, encoding="utf-8") as handle:
            data = json.load(handle, object_pairs_hook=collections.OrderedDict)
        added = sum(1 for key in extra if key not in data)
        data.update(extra)
        with io.open(target, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print("%s: +%d new, %d total" % (language, added, len(data)))


if __name__ == "__main__":
    main(sys.argv[1])
