#!/usr/bin/env python3
"""Compute a Twilio X-Twilio-Signature (HMAC-SHA1) for webhook verification.

Matches the algorithm used in TwilioWhatsAppWebhookControllerTest:
append each form param (sorted by key) as key+value to the signed URL,
HMAC-SHA1 with the auth token, Base64-encode.

Usage:
  sign-twilio.py --auth-token TOKEN --url URL [--param Key=Value ...]
  echo 'From=whatsapp:+5511...' | sign-twilio.py --auth-token TOKEN --url URL --params-from-stdin

Prints the signature to stdout (no trailing commentary).
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import sys


def sign(auth_token: str, url: str, params: dict[str, str]) -> str:
    data = url.encode("utf-8")
    for key in sorted(params):
        data += key.encode("utf-8") + params[key].encode("utf-8")
    digest = hmac.new(auth_token.encode("utf-8"), data, hashlib.sha1).digest()
    return base64.b64encode(digest).decode("ascii")


def parse_kv(items: list[str]) -> dict[str, str]:
    out: dict[str, str] = {}
    for item in items:
        if "=" not in item:
            raise SystemExit(f"expected Key=Value, got: {item!r}")
        key, value = item.split("=", 1)
        out[key] = value
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--auth-token", required=True)
    parser.add_argument("--url", required=True, help="Full signed URL (publicBaseUrl + path + query)")
    parser.add_argument("--param", action="append", default=[], help="Form field Key=Value (repeatable)")
    parser.add_argument(
        "--params-from-stdin",
        action="store_true",
        help="Read additional Key=Value lines from stdin",
    )
    args = parser.parse_args()

    params = parse_kv(args.param)
    if args.params_from_stdin:
        for line in sys.stdin:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            params.update(parse_kv([line]))

    print(sign(args.auth_token, args.url, params), end="")


if __name__ == "__main__":
    main()
