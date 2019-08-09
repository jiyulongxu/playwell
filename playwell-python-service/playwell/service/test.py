"""test service
"""
from typing import Sequence
from playwell.service import Result
from playwell.service.message import ServiceRequestMessage


def add(request_messages: Sequence[ServiceRequestMessage]):
    return [Result.ok({
        "result": req.args["a"] + req.args["b"]
    }) for req in request_messages]
