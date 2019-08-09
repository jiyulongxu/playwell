"""Service Executor
"""
import logging
import time
import queue
from abc import (
    ABCMeta,
    abstractmethod
)
from concurrent.futures import ThreadPoolExecutor

from playwell.service import PlaywellServiceException
from playwell.service.message import ServiceResponseMessage


service_executor = None


class ServiceExecutor(metaclass=ABCMeta):

    """Base service executor
    """

    def __init__(self):
        pass

    @abstractmethod
    def execute(self, service, request_messages):
        pass

    @abstractmethod
    def get_response(self):
        pass

    @abstractmethod
    def close(self):
        pass

    def _process(self, service, request_messages):
        results = service.call(request_messages)
        if len(results) != len(request_messages):
            raise PlaywellServiceException((
                "The service %s return results length "
                "is not match with requests length"
            ) % service.name)

        now = int(time.time() * 1000)
        return [
            ServiceResponseMessage.from_result(now, request_msg, result)
            for request_msg, result in zip(request_messages, results)
            if not request_msg.ignore_result
        ]


class ThreadPoolServiceExecutor(ServiceExecutor):

    """基于Python线程池的ThreadPoolServiceExecutor
    """

    def __init__(self, max_workers=10):
        super().__init__()
        self._executor = ThreadPoolExecutor(max_workers=max_workers)
        self._response_buffer = queue.Queue()

    def execute(self, service, request_messages):
        self._executor.submit(self._process, service, request_messages)

    def get_response(self):
        all_response = []
        while True:
            try:
                all_response.append(self._response_buffer.get_nowait())
            except queue.Empty:
                return all_response

    def _process(self, service, request_messages):
        try:
            all_response = super()._process(service, request_messages)
            for response in all_response:
                self._response_buffer.put_nowait(response)
        except Exception as e:
            logging.exception(e)

    def close(self):
        self._executor.shutdown()
