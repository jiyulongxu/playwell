"""Activity definition相关API测试
"""
import time
from api_test import (
    BASE_URL,
    Methods,
    API,
    ResultPattern,
    Length
)

DEFINITION = r"""
activity:
    name: test_activity
    display_name: Test activity
    domain_id_strategy: user_id

    trigger:
        type: event
        args:
            condition: eventType("user_behavior")
    
    actions:
        - name: greeting
          type: stdout
          args: str("Hello, world")
          ctrl: finish
""".strip()

CREATE_API = API(
    "create_definition",
    Methods.POST,
    BASE_URL + "/v1/definition/create"
)

MODIFY_API = API(
    "modify_definition",
    Methods.POST,
    BASE_URL + "/v1/definition/modify"
)

DELETE_API = API(
    "delete_definition",
    Methods.DELETE,
    BASE_URL + "/v1/definition/delete"
)

QUERY_API = API(
    "query_definition",
    Methods.GET,
    BASE_URL + "/v1/definition/{name}/{version}"
)

QUERY_BY_NAME_API = API(
    "query_by_name",
    Methods.GET,
    BASE_URL + "/v1/definition/{name}"
)

QUERY_LATEST_API = API(
    "query_all_latest",
    Methods.GET,
    BASE_URL + "/v1/definition/all/latest"
)

print("=== 测试活动创建 ===")

print("> 测试正常创建活动")
(CREATE_API.body({
    "codec": "yaml",
    "version": "1",
    "definition": DEFINITION
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
)).test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_activity",
    "version": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_activity",
        "version": "1"
    }
)).test())

print("> 重复创建相同版本")
(CREATE_API.body({
    "codec": "yaml",
    "version": "1",
    "definition": DEFINITION
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="already_exist"
)).test())

print("> 不合法的活动定义")
(CREATE_API.body({
    "codec": "yaml",
    "version": "1",
    "definition": "xxx"
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="parse_error"
)).test())

print("> 不合法的codec")
(CREATE_API.body({
    "codec": "xml",
    "version": "1",
    "definition": DEFINITION
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail"
)).test())

print("> 创建新版本")
(CREATE_API.body({
    "codec": "yaml",
    "version": "2",
    "definition": DEFINITION
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
)).test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_activity",
    "version": "2"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_activity",
        "version": "2"
    }
)).test())

print("=== 测试活动修改 ===")

(MODIFY_API.body({
    "codec": "yaml",
    "version": "1",
    "definition": DEFINITION,
    "enable": False
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
)).test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_activity",
    "version": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "name": "test_activity",
        "enable": False
    }
)).test())

print("=== 测试活动获取 ===")

print("> 获取名称下的所有版本")
(QUERY_BY_NAME_API.path_params({
    "name": "test_activity"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "definitions": Length(min=2, max=2)
    }
)).test())

print("> 获取所有的最新版本")
(QUERY_LATEST_API.path_params({})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "definitions": [
            {
                "name": "test_activity",
                "version": "2"
            }
        ]
    }
)).test())

print("=== 测试活动删除 ===")

print("> 测试删除版本1")
(DELETE_API.query_params({
    "name": "test_activity",
    "version": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
)).test())
time.sleep(1)
(QUERY_API.path_params({
    "name": "test_activity",
    "version": "1"
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="not_found"
)).test())
