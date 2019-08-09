"""Playwell activity definition API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
    Result,
    output_response,
)


## Get all latest definitions ##
GET_ALL_LATEST = API(
    Methods.GET,
    "/v1/definition/all/latest"
)

## Get activity definitions by name ##
GET_BY_NAME = API(
    Methods.GET,
    "/v1/definition/{name}",
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


## Get unique activity definition by name and version ##
def _format_definition(arguments, response):
    output_response(response)
    if arguments.get("format"):
        result = Result.from_response(response)
        if result.is_ok():
            print(result.data["definition_string"])


GET = API(
    Methods.GET,
    "/v1/definition/{name}/{version}",
    (
        Arg(
            "name",
            ArgPos.PATH,
            {
                "required": True,
                "help": "The activity definition name"
            }
        ),
        Arg(
            "version",
            ArgPos.PATH,
            {
                "required": True,
                "help": "The activity definition version"
            }
        ),
        Arg(
            "format",
            None,
            {
                "action": "store_true"
            }
        ),
    ),
    response_handler=_format_definition
)

def _read_definition(arguments):
    with open(arguments["definition"], "r", encoding="utf-8") as f:
        return f.read()

## Create activity definition ##
CREATE = API(
    Methods.POST,
    "/v1/definition/create",
    (
        Arg(
            "codec",
            ArgPos.BODY,
            {
                "required": False,
                "default": "yaml",
                "help": "The codec of activity definition"
            }
        ),
        Arg(
            "definition",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The definition file path of activity definition"
            },
            handler=_read_definition
        ),
        Arg(
            "version",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The version of activity definition"
            }
        ),
    )
)

## Validate activity definition ##
VALIDATE = API(
    Methods.POST,
    "/v1/definition/validate",
    (
        Arg(
            "codec",
            ArgPos.BODY,
            {
                "required": False,
                "default": "yaml",
                "help": "The codec of activity definition"
            }
        ),
        Arg(
            "definition",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The definition file path of activity definition"
            },
            handler=_read_definition
        ),
    )
)

## Modify activity definition ##
MODIFY = API(
    Methods.POST,
    "/v1/definition/modify",
    (
        Arg(
            "codec",
            ArgPos.BODY,
            {
                "required": False,
                "default": "yaml",
                "help": "The codec of activity definition"
            }
        ),
        Arg(
            "definition",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The definition file path of activity definition"
            },
            handler=_read_definition
        ),
        Arg(
            "version",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The version of activity definition"
            }
        ),
    )
)

ENABLE = API(
    Methods.POST,
    "/v1/definition/enable",
    (
        Arg(
            "name",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The name of Activity Definition"
            }
        ),
        Arg(
            "version",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The version of activity definition"
            }
        ),
    )
)

DISABLE = API(
    Methods.POST,
    "/v1/definition/disable",
    (
        Arg(
            "name",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The name of Activity Definition"
            }
        ),
        Arg(
            "version",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The version of activity definition"
            }
        ),
    )
)

## Delete activity definition ##
DELETE = API(
    Methods.DELETE,
    "/v1/definition/delete",
    (
        Arg(
            "name",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target activity definition name"
            }
        ),
        Arg(
            "version",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target activity definition version"
            }
        )
    )
)
