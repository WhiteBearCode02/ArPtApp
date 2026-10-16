"""Transport-test receiver only. Reads synthetic stdin; never opens model/media files."""
import hashlib
import sys

payload = sys.stdin.buffer.read()
print(f"{len(payload)} {hashlib.sha256(payload).hexdigest()}")
