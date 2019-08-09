"""Playwell Activity thread API
"""
import json

from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
)


## Get activity thread ##
GET = API(
    Methods.GET,
    "/v1/activity_thread/{activity_id}/{domain_id}",
    (
        Arg(
            "activity_id",
            ArgPos.PATH,
            {
                "required": True,
                "type": int,
                "help": "The activity id"
            }
        ),
        Arg(
            "domain_id",
            ArgPos.PATH,
            {
                "required": True,
                "type": str,
                "help": "The domain id"
            }
        ),
    )
)

## Scan activity threads ##
SCAN = API(
    Methods.POST,
    "/v1/activity_thread/scan",
    (
        Arg(
            "conditions",
            ArgPos.BODY,
            {
                "required": False,
                "default": "[]",
                "help": "The scan filter conditions"
            },
            handler=lambda arguments: json.loads(arguments.get("conditions", []))
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
            "mark",
            ArgPos.BODY,
            {
                "required": False,
                "default": None,
                "help": "The scan operation mark"
            }
        ),
        Arg(
            "remove_thread",
            ArgPos.BODY,
            {
                "action": "store_true",
                "help": "Remove the match condition threads"
            }
        ),
        Arg(
            "remove_slot_no_match",
            ArgPos.BODY,
            {
                "action": "store_true",
                "help": "Remove the activity thread which slot not belone to this node"
            }
        ),
        Arg(
            "sync_message_bus",
            ArgPos.BODY,
            {
                "required": False,
                "default": "",
                "help": "Sync activity thread to these message buses, split with ','"
            },
            handler=lambda arguments: [bus.strip() for bus in arguments.get(
                "sync_message_bus", "").split(",") if bus.strip()]
        ),
        Arg(
            "sync_batch_num",
            ArgPos.BODY,
            {
                "required": False,
                "default": 1,
                "help": "Sync batch number"
            }
        )
    )
)

## Stop scan operation ##
STOP_SCAN = API(
    Methods.POST,
    "/v1/activity_thread/stop_scan"
)

## Pause the activity thread ##
PAUSE = API(
    Methods.POST,
    "/v1/activity_thread/pause",
    (
        Arg(
            "activity_id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
        Arg(
            "domain_id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target domain id"
            }
        ),
    )
)

## Continue the activity thread ##
CONTINUE = API(
    Methods.POST,
    "/v1/activity_thread/continue",
    (
        Arg(
            "activity_id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
        Arg(
            "domain_id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target domain id"
            }
        ),
    )
)

## Kill the activity thread ##
KILL = API(
    Methods.POST,
    "/v1/activity_thread/kill",
    (
        Arg(
            "activity_id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
        Arg(
            "domain_id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target domain id"
            }
        ),
    )
)

## Add replication message bus
ADD_REPLICATION_MESSAGE_BUS = API(
    Methods.POST,
    "/v1/activity_thread/replication_message_bus",
    (
        Arg(
            "message_bus",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The replication message bus name"
            }
        ),
    )
)

## Remove replication message bus
REMOVE_REPLICATION_MESSAGE_BUS = API(
    Methods.DELETE,
    "/v1/activity_thread/replication_message_bus",
    (
        Arg(
            "message_bus",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The replication message bus name"
            }
        ),
    )
)

## Get all replication message buses
GET_ALL_REPLICATION_MESSAGE_BUSES = API(
    Methods.GET,
    "/v1/activity_thread/replication_message_bus/all"
)
