"""Playwell service API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
    load_config
)


## Get all service meta ##
GET_ALL = API(
    Methods.GET,
    "/v1/service_meta/all"
)

## Get service meta by name ##
GET = API(
    Methods.GET,
    "/v1/service_meta/{name}",
    (
        Arg(
            "name",
            ArgPos.PATH,
            {
                "required": True,
                "help": "The service meta name"
            }
        ),
    )
)

## Register new service meta ##
REGISTER = API(
    Methods.POST,
    "/v1/service_meta/register",
    (
        Arg(
            "name",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The new service meta name"
            }
        ),
        Arg(
            "message_bus",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The new service message bus"
            }
        ),
        Arg(
            "config",
            ArgPos.BODY,
            {
                "required": False,
                "default": "{}",
                "help": "The configuration of new service"
            },
            handler=lambda arguments: load_config("config", arguments)
        ),
    )
)

## Delete service meta ##
DELETE = API(
    Methods.DELETE,
    "/v1/service_meta",
    (
        Arg(
            "name",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target service name"
            }
        ),
    )
)
