"""DomainID strategy 相关API测试
"""
import time
from api_test import (
    BASE_URL,
    Methods,
    API,
    ResultPattern
)

ADD_API = API(
    "add_domain_id_strategy",
    Methods.POST,
    BASE_URL + "/v1/domain_id/add"
)

DELETE_API = API(
    "delete_domain_id_strategy",
    Methods.DELETE,
    BASE_URL + "/v1/domain_id"
)

QUERY_ALL_API = API(
    "query_all_domain_id_strategies",
    Methods.GET,
    BASE_URL + "/v1/domain_id/all"
)

print("=== 添加Domain ID Strategy ===")

print("> 正常添加")
(ADD_API.body({
    "name": "user_id",
    "cond_expression": "true",
    "domain_id_expression": "eventAttr('user_id')"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_ALL_API
.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "strategies": [
            {
                "name": "user_id"
            }
        ]
    }
))
.test())

print("> 重复添加")
(ADD_API.body({
    "name": "user_id",
    "cond_expression": "true",
    "domain_id_expression": "eventAttr('user_id')"
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="already_existed"
))
.test())

print("=== 删除Domain ID Strategy ===")
(ADD_API.body({
    "name": "user_id_2",
    "cond_expression": "true",
    "domain_id_expression": "eventAttr('user_id')"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(DELETE_API.query_params({
    "name": "user_id_2"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_ALL_API
.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "strategies": [
            {
                "name": "user_id"
            }
        ]
    }
))
.test())
