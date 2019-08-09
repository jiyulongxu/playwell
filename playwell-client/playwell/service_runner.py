"""Playwell service runner API
"""
from playwell import (
    API,
    Methods,
)

## Get service runner status ##
STATUS = API(
    Methods.GET,
    "/v1/service_runner/status"
)

## Pause the service runner ##
PAUSE = API(
    Methods.POST,
    "/v1/service_runner/pause"
)

## Rerun the service runner ##
RERUN = API(
    Methods.POST,
    "/v1/service_runner/rerun"
)
