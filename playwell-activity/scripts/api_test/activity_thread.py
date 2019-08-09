"""Activity Thread相关API测试
"""
import time
from api_test import (
    EVENT_BUS_URL,
    BASE_URL,
    Methods,
    API,
    ResultPattern
)

CREATE_DEF_API = API(
    "create_definition",
    Methods.POST,
    BASE_URL + "/v1/definition/create"
)

CREATE_ACTIVITY_API = API(
    "create_activity",
    Methods.POST,
    BASE_URL + "/v1/activity/create"
)

QUERY_ACTIVITY_THREAD_API = API(
    "query_activity_thread",
    Methods.GET,
    BASE_URL + "/v1/activity_thread/{activity_id}/{domain_id}"
)

PAUSE_ACTIVITY_THREAD_API = API(
    "pause_activity_thread",
    Methods.POST,
    BASE_URL + "/v1/activity_thread/pause"
)

CONTINUE_ACTIVITY_THREAD_API = API(
    "continue_activity_thread",
    Methods.POST,
    BASE_URL + "/v1/activity_thread/continue"
)

KILL_ACTIVITY_THREAD_API = API(
    "kill_activity_thread",
    Methods.POST,
    BASE_URL + "/v1/activity_thread/kill"
)

EVENT_BUS_API = API(
    "event_bus",
    Methods.POST,
    EVENT_BUS_URL
)

DEFINITION = r"""
activity:
    name: test_activity_thread
    display_name: Test activity thread
    domain_id_strategy: user_id

    trigger:
        type: event
        args:
            condition: eventTypeIs("user_behavior")
    
    actions:
        - name: greeting
          type: stdout
          args: str("Hello, World")
          ctrl: call("receive")

        - name: receive
          type: receive
          args:
            - when: eventAttr("behavior") == "提交订单"
              then: call("echo")

        - name: echo
          type: stdout
          args: str("Activity thread finish")
          ctrl: finish
""".strip()

print("=== 准备：创建活动定义 & 活动 ===")

(CREATE_DEF_API.body({
    "codec": "yaml",
    "version": "1",
    "definition": DEFINITION
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
activity_id = (CREATE_ACTIVITY_API.body({
    "definition_name": "test_activity_thread",
    "display_name": "Test activity thread",
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test(lambda rs: rs["data"]["activity"]["id"]))
print("new activity id: ", activity_id)
time.sleep(1)

print("=== 触发ActivityThread ===")

(EVENT_BUS_API.body({
    "type": "user_behavior",
    "attr": {
        "user_id": "1",
        "behavior": "用户注册"
    },
    "time": int(time.time() * 1000)
})
.expected_status(200)
.test())
time.sleep(1)

print("=== 查询ActivityThread ===")

print("> 正常查询")
(QUERY_ACTIVITY_THREAD_API.path_params({
    "activity_id": activity_id,
    "domain_id": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "activity_id": activity_id,
        "domain_id": "1",
        "status": "waiting",
        "current_action": "receive"
    }
))
.test())

print("> 查询不存在")
(QUERY_ACTIVITY_THREAD_API.path_params({
    "activity_id": activity_id,
    "domain_id": 2
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="not_found"
))
.test())

print("=== 暂停ActivityThread ===")
(PAUSE_ACTIVITY_THREAD_API.body({
    "activity_id": activity_id,
    "domain_id": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_ACTIVITY_THREAD_API.path_params({
    "activity_id": activity_id,
    "domain_id": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "activity_id": activity_id,
        "domain_id": "1",
        "status": "paused",
        "current_action": "receive"
    }
))
.test())

print("=== 恢复ActivityThread ===")
(CONTINUE_ACTIVITY_THREAD_API.body({
    "activity_id": activity_id,
    "domain_id": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_ACTIVITY_THREAD_API.path_params({
    "activity_id": activity_id,
    "domain_id": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok",
    data={
        "activity_id": activity_id,
        "domain_id": "1",
        "status": "waiting",
        "current_action": "receive"
    }
))
.test())

print("=== kill掉ActivityThread ===")
(KILL_ACTIVITY_THREAD_API.body({
    "activity_id": activity_id,
    "domain_id": "1"
})
.expected_status(200)
.expected_result(ResultPattern(
    status="ok"
))
.test())
time.sleep(1)
(QUERY_ACTIVITY_THREAD_API.path_params({
    "activity_id": activity_id,
    "domain_id": 1
})
.expected_status(400)
.expected_result(ResultPattern(
    status="fail",
    error_code="not_found"
))
.test())
