"""Playwell client base
"""
import os
import time
import json
from urllib.parse import urljoin
from typing import (
    Sequence,
    Dict,
    Callable
)

import requests
from termcolor import colored


BASE_API_URL = ""

class Methods:
    
    """Http methods
    """

    GET, POST, PUT, DELETE = "GET", "POST", "PUT", "DELETE"


def init_client():
    """Init playwell client
    """
    global BASE_API_URL
    if "PLAYWELL_API" not in os.environ:
        print("Could not found the PLAYWELL_API environment variable.")
        exit(1)
    BASE_API_URL = os.environ["PLAYWELL_API"]


def url(path):
    return urljoin(BASE_API_URL, path)


def output_response(response):
    result = json.loads(response.text)
    text = json.dumps(
        result,
        indent=4,
        ensure_ascii=False
    )
    color = "green" if result["status"] == Result.STATUS_OK else "red"
    print(colored(text, color))


class Result:

    """API response result
    """

    STATUS_OK = "ok"

    STATUS_FAIL = "fail"

    STATUS_IGNORE = "ignore"

    STATUS_TIMEOUT = "timeout"

    @classmethod
    def from_response(cls, response):
        return cls(**json.loads(response.text))

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


class API:

    """API meta info
    """

    def __init__(self, method: str, url: str, args_declare=tuple(), response_handler=None):
        self._method = method
        self._url = url
        self._args_declare = args_declare
        self._response_handler = response_handler

    @property
    def method(self):
        return self._method

    @property
    def url(self):
        return url(self._url)

    @property
    def args_declare(self):
        return self._args_declare

    def get_response(self, arguments):
        path_variables = {arg.name: arg.handler(arguments) for arg in self.args_declare
                          if arg.position == ArgPos.PATH}
        url = self.url.format(**path_variables) if path_variables else self.url
        parameters = {arg.name: arg.handler(arguments) for arg in self.args_declare
                      if arg.position == ArgPos.PARAM}
        body = {arg.name: arg.handler(arguments) for arg in self.args_declare
                if arg.position == ArgPos.BODY}
        request_args = {}
        if parameters:
            request_args["params"] = parameters
        if body:
            request_args["json"] = body
        return getattr(requests, self.method.lower())(
            url,
            **request_args
        )

    def execute(self, arguments):
        response = self.get_response(arguments)
        if self._response_handler is None:
            output_response(response)
        else:
            self._response_handler(arguments, response)

    def __repr__(self):
        return {
            "method": self.method,
            "url": self.url,
            "args_declare": self.args_declare
        }

    def __str__(self):
        return self.__repr__()


class ArgPos:

    """Argument positions
    """

    BODY = "body"

    PATH = "path"

    PARAM = "param"


class Arg:

    """Argument declaration
    """

    def __init__(self, name: str, position: str, meta: Dict, handler=None):
        self._name = name
        self._position = position
        self._meta = meta
        self._handler = (lambda arguments: arguments[name]) if handler is None else handler

    @property
    def name(self):
        return self._name

    @property
    def position(self):
        return self._position

    @property
    def meta(self):
        return self._meta

    @property
    def handler(self):
        return self._handler


def load_config(field_name, arguments, default="{}"):
    """加载配置信息，如果是json，那么直接解码，其它情况按文件处理
    """
    field_value = arguments.get(field_name, default).strip()
    if field_value.startswith("{") and field_value.endswith("}"):
        return json.loads(field_value)
    else:
        with open(field_value, "r", encoding="utf-8") as f:
            return json.loads(f.read().strip())


def load_messages(field_name, arguments, default="[]"):
    """从命令行或者文件中加载序列
    """
    messages = []
    field_value = arguments.get(field_name, default).strip()
    if field_value.startswith("[") and field_value.endswith("]"):
        messages = json.loads(field_value)
    else:
        with open(field_value, "r", encoding="utf-8") as f:
            messages = json.loads(f.read().strip())
    
    def _add_timestamp(msg):
        msg["time"] = int(time.time() * 1000)
        return msg

    return [_add_timestamp(msg) for msg in messages]
