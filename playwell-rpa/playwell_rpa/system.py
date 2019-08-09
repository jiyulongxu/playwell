"""System actions
"""
import os
from playwell.service import (
    Result,
    single_request_service
)
from playwell.service.message import ServiceRequestMessage


@single_request_service
def exec_cmd(request: ServiceRequestMessage):
    cmd = request.args["cmd"]
    os.system(cmd)
    return Result.ok()
