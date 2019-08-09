"""Service Meta相关API测试用例
"""
import time
from api_test import (
    BASE_URL,
    Methods,
    API,
    ResultPattern
)

REGISTER_API = API(
    "register_service_meta",
    Methods.POST,
    BASE_URL + "/v1/service_meta/register"
)

DELETE_API = API(
    "delete_service_meta",
    Methods.DELETE,
    BASE_URL + "/v1/service_meta"
)

QUERY_ALL_API = API(
    "query_all_service_meta",
    Methods.GET,
    BASE_URL + "/v1/service_meta/all"
)

QUERY_API = API(
    "query_service_meta",
    Methods.GET,
    BASE_URL + "/v1/service_meta/{name}"
)

print("=== 注册ServiceMeta ===")

print("> 正常注册")
(REGISTER_API.body({
    "name": "test_service",
    "message_bus": "test_bus",
    "config": {
        "a": 1,
        "b": 2
    }
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_service"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_service",
        "message_bus": "test_bus",
        "config": {
            "a": 1,
            "b": 2
        }
    }
))
.test())

print("> 重新注册旧服务")
(REGISTER_API.body({
    "name": "test_service",
    "message_bus": "test_bus2",
    "config": {
        "a": "x",
    }
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_service"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_service",
        "message_bus": "test_bus2",
        "config": {
            "a": "x"
        }
    }
))
.test())

print("=== 查询所有ServiceMeta ===")

(QUERY_ALL_API.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "services": lambda services: any(
            s["name"] == "test_service" for s in services)
    }
))
.test())

print("=== 移除ServiceMeta ===")

(DELETE_API.query_params({
    "name": "test_service"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_service"
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="not_found"
))
.test())
