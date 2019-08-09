"""Playwell Domain API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
)


## Get all domain id strategies ##
GET_ALL = API(
    Methods.GET,
    "/v1/domain_id/all"
)

## Add new domain id strategy ##
ADD = API(
    Methods.POST,
    "/v1/domain_id/add",
    (
        Arg(
            "name",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The domain id strategy unique name"
            }
        ),
        Arg(
            "cond_expression",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The condition expression of the domain id strategy"
            }
        ),
        Arg(
            "domain_id_expression",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The domain id extract expression"
            }
        ),
    )
)

## Delete domain id strategy ##
DELETE = API(
    Methods.DELETE,
    "/v1/domain_id",
    (
        Arg(
            "name",
            ArgPos.PARAM,
            {
                "required": True,
                "help": "The target domain id strategy name"
            }
        ),
    )
)
