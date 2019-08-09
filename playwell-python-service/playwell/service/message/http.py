"""Http message bus
"""
import logging
import queue
from urllib.parse import urlparse

import requests
from bottle import request as web_request

from playwell.service import Result
from playwell.service.message.bus import MessageBus


class HttpMessageBus(MessageBus):

    """HttpMessageBus
    """

    CLASS_NAME = "playwell.message.bus.HttpMessageBus"

    CONFIG_URL = "url"

    def __init__(self, name, clazz, alive, opened, available, config):
        super().__init__(name, clazz, alive, opened, available, config)
        self._url = config[HttpMessageBus.CONFIG_URL]
        self._buffer = queue.Queue()

    def init_web_server(self):
        from playwell.service.resource.web import web_server
        parse_result = urlparse(self._url)
        @web_server.app.post(parse_result.path)
        def _post_handler():
            try:
                message_data_seq = web_request.json
                for message_data in message_data_seq:
                    self._buffer.put_nowait(self._decode_message(message_data))
                return Result.ok().to_dict()
            except Exception as e:
                logging.exception(e)
                return Result.fail(
                    error_code="service_error",
                    message=str(e)
                ).to_dict()

    def write(self, messages):
        message_data_seq = self._encode_messages(messages)
        if not message_data_seq:
            return
        requests.post(self._url, json=message_data_seq)

    def read(self, max_fetch_num: int):
        messages = []
        while True:
            try:
                messages.append(self._buffer.get_nowait())
            except queue.Empty:
                return messages
            else:
                if len(messages) >= max_fetch_num:
                    return messages
