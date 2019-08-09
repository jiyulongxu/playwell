"""
Base API test framework
"""
import re
import abc
import json
import typing
import requests

BASE_URL = "http://127.0.0.1:1922"
EVENT_BUS_URL = "http://127.0.0.1:1923/input"

class Methods:
    
    """Http method constants
    """
    
    GET = "GET"

    PUT = "PUT"

    POST = "POST"

    DELETE = "DELETE"

class API:
    
    """API Meta
    """

    def __init__(self, name, method, url):
        self._name = name
        self._method = method
        self._url = url

    @property
    def name(self):
        return self._name

    @property
    def method(self):
        return self._method

    @property
    def url(self):
        return self._url

    def headers(self, headers):
        return APITestCase(self).headers(headers)

    def body(self, body):
        return APITestCase(self).body(body)

    def path_params(self, path_params):
        return APITestCase(self).path_params(path_params)

    def query_params(self, query_params):
        return APITestCase(self).query_params(query_params)


class APITestCase:
    
    """API Test Case
    """

    def __init__(self, api):
        # API meta info
        self._api = api

        # Request info
        self._headers = {}
        self._body = {}
        self._path_params = {}
        self._query_params = {}

        # Expected response
        self._expected_status = None
        self._expected_headers = None
        self._expected_result = None

    def headers(self, headers):
        self._headers = headers
        return self

    def body(self, body):
        self._body = body
        return self

    def path_params(self, path_params):
        self._path_params = path_params
        return self

    def query_params(self, query_params):
        self._query_params = query_params
        return self

    def expected_status(self, status):
        self._expected_status = status
        return self
    
    def expected_headers(self, headers):
        self._expected_headers = headers
        return self

    def expected_result(self, result):
        self._expected_result = result
        return self

    def test(self, result_extractor=None):
        method = getattr(requests, self._api.method.lower())

        url = self._api.url
        if self._path_params:
            url = url.format(**self._path_params)
            print(url)

        args = {}
        if self._headers:
            args["headers"] = self._headers
        if self._body:
            args["json"] = self._body
        if self._query_params:
            args["params"] = self._query_params

        response = method(url, **args)
        print(json.loads(response.content))

        if self._expected_status is not None:
            match(
                "http_status_code", 
                self._expected_status, 
                response.status_code
            )

        if self._expected_headers is not None:
            match(
                "http_headers",
                self._expected_headers,
                response.headers
            )
        
        result = json.loads(response.content)
        if self._expected_result is not None:
            self._expected_result.test(result)
        
        if result_extractor is not None:
            return result_extractor(result)

class ResultPattern:

    def __init__(self, status=None, error_code=None, message=None, data=None):
        self._status = status
        self._error_code = error_code
        self._message = message
        self._data = data

    def test(self, result):
        if self._status is not None:
            match("status", self._status, result.get("status"))
        
        if self._error_code is not None:
            match("error_code", self._error_code, result.get("error_code"))

        if self._message is not None:
            match("message", self._message, result.get("message"))

        if self._data is not None:
            match("data", self._data, result.get("data"))

class NoRequired:
    
    """如果一个字段不是必须的，那么可以用该类修饰判断条件
    """
    
    def __init__(self, expected):
        self._expected = expected

    @property
    def expected(self):
        return self._expected

class Length:
    
    """Expected length declare
    """
    
    def __init__(self, *, min=None, max=None):
        self._min = min
        self._max = max

    def is_match(self, field, value):
        length = len(value)
        if self._min is not None and (length > self._min):
            raise NoMatchError(
                "The length of the field %s is less than expected min length: %d" % (
                    field,
                    self._min
                )
            )
        if self._max is not None and (length < self._max):
            raise NoMatchError(
                "The length of the field %s is more than expected max lenght: %d" % (
                    field,
                    self._max
                )
            )

def match(field, expected, actual):
    
    if isinstance(expected, NoRequired):
        if actual is None:
            return
        else:
            expected = expected.expected
    else:
        if actual is None:
            raise NoMatchError(
                "The field %s is required, but the actual is None" % field)
    
    if isinstance(expected, typing.Dict):
        if not isinstance(actual, typing.Dict):
            raise NoMatchError("The field %s expected the Dict type" % field)
        for k, v in expected.items():
            match("%s[%s]" % (field, k), v, actual.get(k))
    elif isinstance(expected, typing.List):
        if not isinstance(actual, typing.List):
            raise NoMatchError("The field %s expected the List type" % field)
        for index, element in enumerate(zip(expected, actual)):
            expected_element, actual_element = element
            match("%s[%d]" % (field, index), expected_element, actual_element)
    elif isinstance(expected, Length):
        expected.is_match(field, actual)
    elif isinstance(expected, typing.Pattern):
        if expected.search(actual) is None:
            raise NoMatchError("The field %s not match the expected pattern" % field)
    elif isinstance(expected, typing.Callable):
        if not expected(actual):
            raise NoMatchError("The field %s not match the expected condition" % field)
    else:
        if expected != actual:
            raise NoMatchError("The field %s expected value is %s, but the actual is %s" % (
                field,
                expected,
                actual
            ))


class NoMatchError(BaseException):
    
    """当真实值同预期值不匹配的时候抛出该异常
    """

    def __init__(self, msg):
        super().__init__(msg)
