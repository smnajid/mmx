#!/usr/bin/env python3
"""External Identity stub for local cross-org testing (CGEG deployment side).

The CGEG deployment resolves hub-side global accounts through its ExternalIdentityGateway
(HTTP GET {base}/api/identity/resolve) before sending a Leg-A routing request to LODH.
In production this is an external identity system; in local testing this stub stands in for it.

Resolution rules:
  1. exact triple match in MAPPINGS (spec example: (CGD, CGD-PM-77, LOC) → CGD-LOC-001);
  2. deterministic fallback: {client}-{hub}-001 (e.g. (CGD, PM-42, LOC) → CGD-LOC-001);
  3. unmapped/missing params → 404, which the gateway turns into an empty Optional and the
     intake use case turns into a client-side Rejected (routing failure) — per
     openspec/specs/order-routing/spec.md "remote account resolution".

Usage:   ./scripts/identity-stub.py          # listens on 127.0.0.1:8090
Env:     IDENTITY_STUB_PORT (default 8090)
"""

import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(os.environ.get("IDENTITY_STUB_PORT", "8090"))

# (clientLegalEntityCode, clientPortfolioNumber, hubLegalEntityCode) → hub-side portfolioNumber
MAPPINGS = {
    ("CGD", "CGD-PM-77", "LOC"): "CGD-LOC-001",
}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        if url.path != "/api/identity/resolve":
            self._json(404, {"error": "NOT_FOUND", "message": f"unknown path {url.path}"})
            return
        q = parse_qs(url.query)

        def one(key):
            return (q.get(key) or [""])[0]

        key = (
            one("clientLegalEntityCode"),
            one("clientPortfolioNumber"),
            one("hubLegalEntityCode"),
        )
        hub_portfolio = MAPPINGS.get(key)
        if hub_portfolio is None and key[0] and key[2]:
            hub_portfolio = f"{key[0]}-{key[2]}-001"
        if hub_portfolio is None:
            self._json(
                404,
                {"error": "UNRESOLVED", "message": f"no hub-side account mapped for {key}"},
            )
            return
        self._json(200, {"hubPortfolioNumber": hub_portfolio})

    def _json(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_args):
        pass  # keep the starter console quiet


if __name__ == "__main__":
    print(f"[identity-stub] listening on http://127.0.0.1:{PORT}/api/identity/resolve", flush=True)
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
