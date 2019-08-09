"""Playwell clock API
"""
import json

from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
)


## Scan clock messages ##
SCAN = API(
    Methods.POST,
    "/v1/clock/scan",
    (
        Arg(
            "conditions",
            ArgPos.BODY,
            {
                "required": False,
                "default": "[]",
                "help": "The scan filter conditions"
            },
            handler=lambda arguments: json.loads(arguments.get("conditions", "[]"))
        ),
        Arg(
            "log_per_records",
            ArgPos.BODY,
            {
                "required": False,
                "type": int,
                "default": 1,
                "help": "Output log per records"
            }
        ),
        Arg(
            "limit",
            ArgPos.BODY,
            {
                "required": False,
                "type": int,
                "default": -1,
                "help": "The scan records limit number"
            }
        ),
        Arg(
            "mark",
            ArgPos.BODY,
            {
                "required": False,
                "default": "",
                "help": "The scan operation mark"
            }
        ),
    )
)

## Stop scan clock messages ##
STOP_SCAN = API(
    Methods.POST,
    "/v1/clock/stop_scan"
)
