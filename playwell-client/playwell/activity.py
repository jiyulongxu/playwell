"""Playwell activity API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
    load_config
)


## Get all activities ##
GET_ALL = API(
    Methods.GET,
    "/v1/activity/all"
)

## Get activity by ID ##
GET = API(
    Methods.GET,
    "/v1/activity/{id}",
    (
        Arg(
            "id",
            ArgPos.PATH,
            {
                "required": True,
                "help": "The activity id"
            }
        ),
    )
)

## Get by definition name ##
GET_BY_DEFINITION = API(
    Methods.GET,
    "/v1/activity/definition/{name}",
    (
        Arg(
            "name",
            ArgPos.PATH,
            {
                "required": True,
                "help": "The activity definition name"
            }
        ),
    )
)

## Get by status ##
GET_BY_STATUS = API(
    Methods.GET,
    "/v1/activity/status/{status}",
    (
        Arg(
            "status",
            ArgPos.PATH,
            {
                "required": True,
                "help": "The activity status"
            }
        ),
    )
)

## Create activity ##
CREATE = API(
    Methods.POST,
    "/v1/activity/create",
    (
        Arg(
            "definition",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The activity definition name"
            }
        ),
        Arg(
            "display_name",
            ArgPos.BODY,
            {
                "required": False,
                "default": "",
                "help": "The activity display name"
            }
        ),
        Arg(
            "config",
            ArgPos.BODY,
            {
                "required": False,
                "default": "{}",
                "help": "The configuration data for new activity"
            },
            handler=lambda arguments: load_config("config", arguments)
        ),
    )
)

## Pause Activity ##
PAUSE = API(
    Methods.POST,
    "/v1/activity/pause",
    (
        Arg(
            "id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
    )
)

## Continue Activity ##
CONTINUE = API(
    Methods.POST,
    "/v1/activity/continue",
    (
        Arg(
            "id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
    )
)

## Kill Activity ##
KILL = API(
    Methods.POST,
    "/v1/activity/kill",
    (
        Arg(
            "id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
    )
)

## Modify activity config ##
MODIFY_CONFIG = API(
    Methods.POST,
    "/v1/activity/modify/config",
    (
        Arg(
            "id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
        Arg(
            "config",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The configuration data for new activity"
            },
            handler=lambda arguments: load_config("config", arguments)
        ),
    )
)

## Put Activity config item ##
PUT_CONFIG_ITEM = API(
    Methods.POST,
    "/v1/activity/modify/config/item",
    (
        Arg(
            "id",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
        Arg(
            "key",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The config item key"
            },
        ),
        Arg(
            "type",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The config item type"
            }
        ),
        Arg(
            "value",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The config item value"
            }
        )
    )
)

## Remove Activity config item ##
REMOVE_CONFIG_ITEM = API(
    Methods.DELETE,
    "/v1/activity/modify/config/item",
    (
        Arg(
            "id",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target activity id"
            }
        ),
        Arg(
            "key",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The config item key"
            },
        ),
    )
)
