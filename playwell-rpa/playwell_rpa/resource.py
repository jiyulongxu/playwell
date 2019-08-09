"""resource模块用于管理RPA中使用的资源
"""
import logging
from abc import (
    ABCMeta,
    abstractmethod
)
from collections import defaultdict
from playwell.service import single_request_service
from playwell.service.message import ServiceRequestMessage


class TmpResourceTracer(metaclass=ABCMeta):

    """用于跟踪并释放临时资源
    """

    def __init__(self):
        pass

    @abstractmethod
    def trace(self, activity_id: int, domain_id: str, type: str, name: str):
        """注册要追踪的资源信息
        :param activity_id Activity ID
        :param domain_id Domain ID
        :param type 资源类型
        :param name 资源名称
        """
        pass

    @abstractmethod
    def register_handler(self, type, handler):
        """为不同类型注册回收处理器
        :param type 资源类型
        :param handler 资源类型回收处理器
        """
        pass

    @abstractmethod
    def free_all(self, activity_id: int, domain_id: str):
        """释放ActivityThread的所有资源
        :param activity_id Activity ID
        :param domain_id Domain ID
        """
        pass


class MemTmpResourceTracer(TmpResourceTracer):

    def __init__(self):
        super().__init__()
        self._resource = defaultdict(list)
        self._handlers = {}

    def trace(self, activity_id: int, domain_id: str, type: str, name):
        self._resource[(activity_id, domain_id)].append((type, name))

    def register_handler(self, type, handler):
        self._handlers[type] = handler

    def free_all(self, activity_id: int, domain_id: str):
        all_ref = self._resource[(activity_id, domain_id)]
        if not all_ref:
            return
        for (type, name) in all_ref:
            try:
                handler = self._handlers[type]
                handler(activity_id, domain_id, name)
            except Exception as e:
                logging.exception(e)
        del self._resource[(activity_id, domain_id)]


resource_tracer = MemTmpResourceTracer()


@single_request_service
def gc(request: ServiceRequestMessage):
    global resource_tracer
    args = request.args
    event = args["event"]
    if event == "finished" or event == "failure":
        resource_tracer.free_all(
            request.activity_id,
            request.domain_id
        )
