"""MessageBus API的测试用例
"""
import time
from api_test import (
    BASE_URL,
    Methods,
    API,
    ResultPattern
)

REGISTER_API = API(
    "register_message_bus",
    Methods.POST,
    BASE_URL + "/v1/message_bus/register"
)

OPEN_API = API(
    "open_message_bus",
    Methods.POST,
    BASE_URL + "/v1/message_bus/open"
)

CLOSE_API = API(
    "close_message_bus",
    Methods.POST,
    BASE_URL + "/v1/message_bus/close"
)

QUERY_ALL_API = API(
    "query_all_message_bus",
    Methods.GET,
    BASE_URL + "/v1/message_bus/all"
)

QUERY_API = API(
    "query_message_bus",
    Methods.GET,
    BASE_URL + "/v1/message_bus/{name}"
)

DELETE_API = API(
    "delete_message_bus",
    Methods.DELETE,
    BASE_URL + "/v1/message_bus"
)

print("=== 注册新的MessageBus ===")

print("> 正常注册")
(REGISTER_API.body({
    "class": "playwell.message.bus.ConcurrentLinkedQueueMessageBus",
    "config": {
        "name": "test_bus"
    }
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_bus"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_bus",
        "opened": True
    }
))
.test())

print("=== 关闭MessageBus ===")

(CLOSE_API.body({
    "name": "test_bus"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_bus"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_bus",
        "opened": False
    }
))
.test())

print("=== 开启MessageBus ===")

(OPEN_API.body({
    "name": "test_bus"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_bus"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_bus",
        "opened": True
    }
))
.test())

print("=== 删除MessageBus ===")

(DELETE_API.query_params({
    "name": "test_bus"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_bus"
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="not_found"
))
.test())
