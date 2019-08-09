"""ActivityRunner API测试用例
"""
import time
from api_test import (
    API,
    BASE_URL,
    Methods,
    ResultPattern
)

STATUS_API = API(
    "activity_runner_status",
    Methods.GET,
    BASE_URL + "/v1/activity_runner/status"
)

PAUSE_API = API(
    "pause_activity_runner",
    Methods.POST,
    BASE_URL + "/v1/activity_runner/pause"
)

RERUN_API = API(
    "rerun_activity_runner",
    Methods.POST,
    BASE_URL + "/v1/activity_runner/rerun"
)

print("=== 测试暂停Activity Runner ===")

(PAUSE_API.body({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(4)
last_active = (STATUS_API.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "status": "paused"
    }
))
.test(lambda rs: rs["data"]["last_active"]))
time.sleep(2)
(STATUS_API.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "status": "paused",
        "last_active": last_active
    }
))
.test())

print("=== 测试恢复Activity Runner ===")

(RERUN_API.body({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(4)
last_active = (STATUS_API.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "status": lambda v: v != "paused"
    }
))
.test(lambda rs: rs["data"]["last_active"]))
time.sleep(2)
(STATUS_API.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "status": lambda v: v != "paused",
        "last_active": lambda l: l > last_active
    }
))
.test())
