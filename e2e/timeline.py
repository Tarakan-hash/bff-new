#!/usr/bin/env python3
"""Временная шкала событий workflow из `temporal workflow show -o json` (stdin)."""
import json, sys
from datetime import datetime

events = json.load(sys.stdin)["events"]
start = None
for event in events:
    at = datetime.fromisoformat(event["eventTime"].replace("Z", "+00:00"))
    start = start or at
    attributes = next((v for k, v in event.items() if k.endswith("Attributes")), {})
    detail = attributes.get("activityType", {}).get("name", "")
    print(f"{(at - start).total_seconds() * 1000:8.1f} мс  {event['eventType'].replace('EVENT_TYPE_', ''):35} {detail}")
