"""Playwell activity runner API
"""
from playwell import (
    API,
    Methods,
)


## Get activity runner status ##
STATUS = API(
    Methods.GET,
    "/v1/activity_runner/status"
)

## Pause activity runner ##
PAUSE = API(
    Methods.POST,
    "/v1/activity_runner/pause"
)

## Rerun activity runner ##
RERUN = API(
    Methods.POST,
    "/v1/activity_runner/rerun"
)
