"""Activity相关API测试
"""
import time
from api_test import (
    BASE_URL,
    Methods,
    API,
    ResultPattern,
    Length
)

CREATE_API = API(
    "create_activity",
    Methods.POST,
    BASE_URL + "/v1/activity/create"
)

QUERY_API = API(
    "query_activity",
    Methods.GET,
    BASE_URL + "/v1/activity/{id}"
)

QUERY_BY_DEF_API = API(
    "query_by_definition",
    Methods.GET,
    BASE_URL + "/v1/activity/definition/{name}"
)

QUERY_BY_STATUS_API = API(
    "query_by_status",
    Methods.GET,
    BASE_URL + "/v1/activity/status/{status}"
)

QUERY_ALL_API = API(
    "query_all",
    Methods.GET,
    BASE_URL + "/v1/activity/all"
)

PAUSE_API = API(
    "pause_activity",
    Methods.POST,
    BASE_URL + "/v1/activity/pause"
)

CONTINUE_API = API(
    "continue_activity",
    Methods.POST,
    BASE_URL + "/v1/activity/continue"
)

KILL_API = API(
    "kill_activity",
    Methods.POST,
    BASE_URL + "/v1/activity/kill"
)

MODIFY_ACTIVITY_CONFIG_API = API(
    "modify_activity_config",
    Methods.POST,
    BASE_URL + "/v1/activity/modify/config"
)

print("=== 测试创建活动 ===")

print("> 正常创建活动")
activity_id = (CREATE_API.body({
    "definition_name": "test_activity",
    "display_name": "Test activity",
    "config": {"a": 1}
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "activity": {
            "definition": "test_activity",
            "config": {"a": 1}
        }
    }
))
.test(lambda rs: rs["data"]["activity"]["id"]))
print("New activity id: %d" % activity_id)
time.sleep(1)
(QUERY_API.path_params({
    "id": activity_id
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "id": activity_id,
        "definition": "test_activity",
        "config": {"a": 1},
        "status": "common"
    }
))
.test())

print("> 使用错误的定义创建活动")
(CREATE_API.body({
    "definition_name": "error_definition",
    "display_name": "Test activity",
    "config": {"a": 1}
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="def_not_found"
))
.test())

print("=== 测试修改活动状态 ===")

print("> 暂停活动")
(PAUSE_API.body({
    "id": activity_id
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "id": activity_id
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "id": activity_id,
        "status": "paused"
    }
))
.test())

print("> 根据状态获取活动")
(QUERY_BY_STATUS_API.path_params({
    "status": "paused"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "activities": [
            {
                "id": activity_id,
                "status": "paused"
            }
        ]
    }
))
.test())
(QUERY_BY_STATUS_API.path_params({
    "status": "common"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "activities": Length(min=0, max=0)
    }
))
.test())

print("> 恢复活动")
(CONTINUE_API.body({
    "id": activity_id
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "id": activity_id
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "id": activity_id,
        "status": "common"
    }
))
.test())

print("> 修改活动配置")
(MODIFY_ACTIVITY_CONFIG_API.body({
    "id": activity_id,
    "config": {
        "a": 2,
        "b": 3
    }
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "id": activity_id
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "id": activity_id,
        "config": {
            "a": 2,
            "b": 3
        }
    }
))
.test())

print("> 根据定义名称查询活动")
(QUERY_BY_DEF_API.path_params({
    "name": "test_activity"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "activities": [
            {
                "id": activity_id
            }
        ]
    }
))
.test())

print("> 杀掉活动")
(KILL_API.body({
    "id": activity_id
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_API.path_params({
    "id": activity_id
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="not_found"
))
.test())
