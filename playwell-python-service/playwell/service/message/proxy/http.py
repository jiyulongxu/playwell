"""http request message proxy
"""
import pydoc
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests
from bottle import request as web_request

from playwell.service import PlaywellServiceException
from playwell.service.message import ServiceRequestMessage
from playwell.service.message.proxy import ServiceRequestProxy


class HttpServiceRequestProxy(ServiceRequestProxy):

    @classmethod
    def build_with_config(cls):
        from playwell.service.message.proxy.config import proxy_config
        rule = pydoc.locate(proxy_config["rule"])
        path = proxy_config["path"]
        max_handlers = proxy_config.get("max_handlers", 10)
        handle_timeout = proxy_config.get("handle_timeout", 10)
        return cls(
            rule=rule,
            path=path,
            max_handlers=max_handlers,
            handle_timeout=handle_timeout
        )

    def __init__(
            self, rule, path: str, max_handlers: int = 10, handle_timeout: int = 10):
        super().__init__(rule)
        self._path = path
        self._executor = ThreadPoolExecutor(max_workers=max_handlers)
        self._handle_timeout = handle_timeout

    def start(self):
        from playwell.service.resource.web import web_server
        handle_timeout = self._handle_timeout
        @web_server.app.post(self._path)
        def _proxy_handle(self):
            nonlocal handle_timeout
            try:
                all_request_messages = [ServiceRequestMessage.from_dict(data) for data in web_request.json]
                routes_request_messages = self._rule(all_request_messages)

                futures = []
                for url, request_messages in routes_request_messages.items():
                    future = self._executor.submit(self._post_message, url, request_messages)
                    futures.append(future)

                for future in futures:
                    try:
                        future.result(timeout=handle_timeout)
                    except Exception as e:
                        logging.exception(e)
            except Exception as e:
                logging.exception(e)

    def _post_messages(self, url, request_messages):
        response = requests.post(url, json=[msg.to_dict() for msg in request_messages])
        if response.status_code != 200:
            raise PlaywellServiceException(
                "Deliver service request error, url: %s, status code: %d" % (url, response.status_code))

    def close(self):
        self._executor.shutdown()


