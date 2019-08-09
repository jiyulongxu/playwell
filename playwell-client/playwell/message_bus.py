"""Playwell message bus API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
    load_config,
    load_messages
)


## Get all message bus ##
GET_ALL = API(
    Methods.GET,
    "/v1/message_bus/all"
)

## Get message bus by name ##
GET = API(
    Methods.GET,
    "/v1/message_bus/{name}",
    (
        Arg(
            "name",
            ArgPos.PATH,
            {
                "required": True,
                "help": "The name of message bus"
            }
        ),
    )
)

## Register message bus ##
REGISTER = API(
    Methods.POST,
    "/v1/message_bus/register",
    (
        Arg(
            "config",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The configuration of new message bus"
            },
            handler=lambda arguments: load_config("config", arguments)
        ),
    )
)

## Open message bus ##
OPEN = API(
    Methods.POST,
    "/v1/message_bus/open",
    (
        Arg(
            "name",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target message bus name"
            }
        ),
    )
)

## Close message bus ##
CLOSE = API(
    Methods.POST,
    "/v1/message_bus/close",
    (
        Arg(
            "name",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target message bus name"
            }
        ),
    )
)

## Delete message bus ##
DELETE = API(
    Methods.DELETE,
    "/v1/message_bus",
    (
        Arg(
            "name",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target message bus name"
            }
        ),
    )
)

## Write message ##
WRITE = API(
    Methods.POST,
    "/v1/message_bus/write",
    (
        Arg(
            "message_bus",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target message bus name"
            }
        ),
        Arg(
            "messages",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The message sequence"
            },
            handler=lambda arguments: load_messages("messages", arguments)
        ),
    )
)
