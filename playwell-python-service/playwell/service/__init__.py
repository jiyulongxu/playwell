"""Playwell service base module
"""
import json
import functools
import logging
from abc import (
    ABCMeta,
    abstractmethod
)


class Result:
    
    """API response result
    """

    STATUS_OK = "ok"

    STATUS_FAIL = "fail"

    STATUS_IGNORE = "ignore"

    STATUS_TIMEOUT = "timeout"

    @classmethod
    def ok(cls, data=None):
        return cls(Result.STATUS_OK, data=data)

    @classmethod
    def fail(cls, error_code, message="", data=None):
        return cls(
            status=Result.STATUS_FAIL,
            error_code=error_code,
            message=message,
            data=data
        )

    @classmethod
    def timeout(cls, error_code="", message="", data=None):
        return cls(
            status=Result.STATUS_TIMEOUT,
            error_code=error_code,
            message=message,
            data=data
        )

    @classmethod
    def from_response(cls, response):
        return cls(**response.json())

    def __init__(self, status, error_code="", message="", data=None):
        self._status = status
        self._error_code = error_code
        self._message = message
        self._data = {} if data is None else data

    @property
    def status(self):
        return self._status

    @property
    def error_code(self):
        return self._error_code

    @property
    def message(self):
        return self._message

    @property
    def data(self):
        return self._data

    def is_ok(self):
        return Result.STATUS_OK == self.status

    def is_failure(self):
        return Result.STATUS_FAIL == self.status

    def is_ignore(self):
        return Result.STATUS_IGNORE == self.status

    def is_timeout(self):
        return Result.STATUS_TIMEOUT == self.status

    def to_dict(self):
        data = {"status": self._status}
        if self._error_code:
            data["error_code"] = self._error_code
        if self._message:
            data["message"] = self._message
        if self._data:
            data["data"] = self._data
        return data

    def __repr__(self):
        return json.dumps(self.to_dict())

    def __str__(self):
        return self.__repr__()


class PlaywellServiceException(Exception):
    
    """自定义异常，用于抛出与playwell-service相关的错误
    """

    def __init__(self, msg):
        self._msg = msg

    def __repr__(self):
        return self._msg

    def __str__(self):
        return self._msg


def single_request_service(service):

    @functools.wraps(service)
    def _service(requests):
        results = []
        for req in requests:
            try:
                result = service(req)
            except Exception as e:
                logging.exception(e)
                result = Result.fail(error_code="sys_error", message=str(e))
            results.append(result)
        return results

    return _service


class SingleRequestService(metaclass=ABCMeta):

    def __init__(self):
        pass

    def __callable__(self, request_messages):
        results = []
        for req in request_messages:
            try:
                result = self._handle_request(req)
            except Exception as e:
                logging.exception(e)
                result = Result.fail(error_code="sys_error", message=str(e))
            results.append(result)
        return results

    @abstractmethod
    def _handle_request(self, request_message) -> Result:
        pass
