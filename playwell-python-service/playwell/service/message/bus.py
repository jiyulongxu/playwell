"""This module include base message bus component
"""
from abc import (
    ABCMeta,
    abstractmethod
)
from urllib.parse import urljoin

import requests

from playwell.service import (
    Result,
    PlaywellServiceException
)
from playwell.service.message import (
    MessageTypes,
    Message,
    ServiceRequestMessage,
    ServiceResponseMessage
)


class MessageBus(metaclass=ABCMeta):

    """Base message bus
    """

    def __init__(self, name, clazz, alive, opened, available, config):
        self._name = name
        self._clazz = clazz
        self._alive = alive
        self._opened = opened
        self._available = available
        self._config = config

    @property
    def name(self):
        return self._name

    @property
    def clazz(self):
        return self._clazz

    @property
    def alive(self):
        return self._alive

    @alive.setter
    def alive(self, alive):
        self._alive = alive

    @property
    def opened(self):
        return self._opened

    @opened.setter
    def opened(self, opened):
        self._opened = opened

    @property
    def available(self):
        return self._available

    @available.setter
    def available(self, available):
        self._available = available

    @property
    def config(self):
        return self._config

    @abstractmethod
    def write(self, messages):
        ...

    @abstractmethod
    def read(self, max_fetch_num: int):
        ...

    def _encode_messages(self, messages):
        if not messages:
            return []
        return [msg.to_dict() for msg in messages]

    def _decode_messages(self, message_data_seq):
        if not message_data_seq:
            return []
        return [self._decode_message(message_data) for message_data in message_data_seq]

    def _decode_message(self, message_data):
        message_type = message_data[Message.FIELD_TYPE]
        if message_type == MessageTypes.REQUEST:
            return ServiceRequestMessage.from_dict(message_data)
        elif message_type == MessageTypes.RESPONSE:
            return ServiceResponseMessage.from_dict(message_data)
        else:
            return Message(**message_data)


class MessageBusManager:

    """message bus manager
    """

    def __init__(self):
        self._input_message_bus = None
        self._all_playwell_message_bus = {}

    def register_input_message_bus(self):
        """注册input message bus
        """
        from playwell.service.config import input_message_bus_config
        if not input_message_bus_config:
            return
        if self._input_message_bus is not None:
            raise PlaywellServiceException("The input message bus has already been registered!")

        from playwell.service.config import playwell_api
        response = requests.post(
            urljoin(playwell_api, "/v1/message_bus/register"),
            json={
                "config": input_message_bus_config
            }
        )
        result = Result.from_response(response)
        if not result.is_ok():
            raise PlaywellServiceException(
                "Register message bus error, the API return %s" % result)

        response = requests.post(
            urljoin(playwell_api, "/v1/message_bus/open"),
            json={"name": input_message_bus_config["name"]}
        )
        result = Result.from_response(response)
        if not result.is_ok() and result.error_code != "already_opened":
            raise PlaywellServiceException(
                "Open message bus error, the API return: %s" % result)

        from playwell.service.message.http import HttpMessageBus
        self._input_message_bus = HttpMessageBus(
            name=input_message_bus_config["name"],
            clazz=input_message_bus_config["class"],
            alive=True,
            opened=True,
            available=True,
            config=input_message_bus_config
        )
        self._input_message_bus.init_web_server()
        self._all_playwell_message_bus[
            self._input_message_bus.name] = self._input_message_bus

    @property
    def input_message_bus(self):
        return self._input_message_bus

    def get_message_bus(self, name: str):
        return self._all_playwell_message_bus[name]

    def refresh_all(self):
        from playwell.service.config import playwell_api
        response = requests.get(
            urljoin(playwell_api, "/v1/message_bus/all")
        )
        result = Result.from_response(response)
        if not result.is_ok():
            raise PlaywellServiceException(
                "Refresh message bus failure, the API return %s" % result)

        message_bus_data_seq = result.data.get("buses", [])
        all_names = set()
        for message_bus_data in message_bus_data_seq:
            name = message_bus_data["name"]
            clazz = message_bus_data["class"]
            all_names.add(name)
            if name in self._all_playwell_message_bus:
                message_bus = self._all_playwell_message_bus[name]
                if message_bus.opened != message_bus_data["opened"]:
                    message_bus.opened = message_bus_data["opened"]
                if message_bus.alive != message_bus_data["alive"]:
                    message_bus.alive = message_bus_data["alive"]
                if message_bus.available != message_bus_data["available"]:
                    message_bus.available = message_bus_data["available"]
            else:
                from playwell.service.message.http import HttpMessageBus
                if clazz == HttpMessageBus.CLASS_NAME:
                    message_bus = HttpMessageBus(
                        name=name,
                        clazz=clazz,
                        alive=message_bus_data["alive"],
                        opened=message_bus_data["opened"],
                        available=message_bus_data["available"],
                        config=message_bus_data.get("config", {})
                    )
                    self._all_playwell_message_bus[message_bus.name] = message_bus

        for message_bus in list(self._all_playwell_message_bus.values()):
            if message_bus.name not in all_names:
                del self._all_playwell_message_bus[message_bus.name]


message_bus_manager = MessageBusManager()
