"""proxy允许以一种自行定义的方式来路由请求消息。这在一些业务场景中非常重要，
比如RPA，对外都是统一暴露同一种类型的工作单元，并拥有相同的MessageBus，
但对于服务请求，隶属一个机器人的所有请求通常只能在同一个处理进程中完成。
"""
from abc import (
    ABCMeta,
    abstractmethod
)
from collections import defaultdict

from playwell.service import PlaywellServiceException


class ServiceRequestProxy(metaclass=ABCMeta):

    def __init__(self, rule):
        if not callable(rule):
            raise PlaywellServiceException(
                "The ServiceRequestProxy rule must be callable!")
        self._rule = rule

    @abstractmethod
    def start(self):
        pass

    @abstractmethod
    def close(self):
        pass
