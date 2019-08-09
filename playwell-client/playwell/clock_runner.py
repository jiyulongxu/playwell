"""Playwell Clock Runner API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
)


## Get status of clock runner
STATUS = API(
    Methods.GET,
    "/v1/clock_runner/status"
)

## Pause clock runner
PAUSE = API(
    Methods.POST,
    "/v1/clock_runner/pause"
)

## Rerun clock runner
RERUN = API(
    Methods.POST,
    "/v1/clock_runner/rerun"
)
