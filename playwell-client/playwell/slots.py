"""Playwell slots API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
    load_config
)


## Alloc slots ##
ALLOC = API(
    Methods.POST,
    "/v1/slot/alloc",
    (
        Arg(
            "slots_num",
            ArgPos.BODY,
            {
                "required": True,
                "help": "All slots num"
            }
        ),
        Arg(
            "slots_per_node",
            ArgPos.BODY,
            {
                "required": True,
                "help": "Slots num per node"
            },
            handler=lambda arguments: load_config("slots_per_node", arguments)
        ),
    )
)

## Get service by slot ##
GET_SERVICE_BY_SLOT = API(
    Methods.GET,
    "/v1/slot/get_service_by_slot",
    (
        Arg(
            "slot",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target slot"
            }
        ),
    )
)

## Get slots by service ##
GET_SLOTS_BY_SERVICE = API(
    Methods.GET,
    "/v1/slot/get_slots_by_service",
    (
        Arg(
            "service",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target service"
            }
        ),
    )
)

## Get slots distribution ##
DISTRIBUTION = API(
    Methods.GET,
    "/v1/slot/distribution"
)

## Get service by hash ##
GET_SERVICE_BY_HASH = API(
    Methods.GET,
    "/v1/slot/get_service_by_hash",
    (
        Arg(
            "hash",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The hash code"
            }
        ),
    )
)

## Get service by key ##
GET_SERVICE_BY_KEY = API(
    Methods.GET,
    "/v1/slot/get_service_by_key",
    (
        Arg(
            "key",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target key"
            }
        ),
    )
)

## Get slot by key ##
GET_SLOT_BY_KEY = API(
    Methods.GET,
    "/v1/slot/get_slot_by_key",
    (
        Arg(
            "key",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target key"
            }
        ),
    )
)

## Start slots migration ##
START_MIGRATION = API(
    Methods.POST,
    "/v1/slot/migration/start",
    (
        Arg(
            "message_bus",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The migration message bus"
            }
        ),
        Arg(
            "input_message_bus_config",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The configuration for input message bus"
            },
            handler=lambda arguments: 
                load_config("input_message_bus_config", arguments)
        ),
        Arg(
            "output_message_bus_config",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The configuration for output message bus"
            },
            handler=lambda arguments:
                load_config("output_message_bus_config", arguments)
        ),             
        Arg(
            "slots_distribution",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The new slots distribution"
            },
            handler=lambda arguments: load_config("slots_distribution", arguments)
        ),
        Arg(
            "comment",
            ArgPos.BODY,
            {
                "required": False,
                "default": "",
                "help": "The comment of slots migration"
            }
        )
    )
)

## Stop slots migration ##
STOP_MIGRATION = API(
    Methods.POST,
    "/v1/slot/migration/stop"
)

## Continue the slots migration ##
CONTINUE_MIGRATION = API(
    Methods.POST,
    "/v1/slot/migration/continue"
)

## Get slots migration status ##
GET_MIGRATION_STATUS = API(
    Methods.GET,
    "/v1/slot/migration/status"
)
