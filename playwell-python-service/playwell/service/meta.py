"""该模块包含了Playwell service meta相关的业务逻辑
"""
import json
import pydoc
from urllib.parse import urljoin

import requests

from playwell.service import (
    Result,
    PlaywellServiceException
)


class ServiceMeta:
    
    """Service Meta Info
    """

    def __init__(
        self,
        name: str, 
        message_bus: str,
        config: dict = None
    ):
        self._name = name
        self._message_bus = message_bus
        self._config = config if config is not None else {}

    @property
    def name(self):
        return self._name

    @property
    def message_bus(self):
        return self._message_bus

    @property
    def config(self):
        return self._config

    def to_dict(self):
        return {
            "name": self.name,
            "message_bus": self.message_bus,
            "config": self.config
        }

    def __repr__(self):
        return json.dumps(self.to_dict())

    def __str__(self):
        return repr(self)


class LocalServiceMeta(ServiceMeta):
    
    """Local service meta
    """
    
    def __init__(
        self,
        service,
        name: str,
        message_bus: str,
        config: dict = None
    ):
        self._service = service
        super().__init__(name, message_bus, config)

    @property
    def service(self):
        return self._service

    def init(self):
        if hasattr(self._service, "init"):
            self._service.init(**self.config)

    def call(self, messages):
        return self._service(messages)


class ServiceMetaManager:
    
    """Service meta manager
    """
    
    def __init__(self):
        self._all_service_meta = {}
        self._all_local_service = {}

    def register_all_local_service(self):
        from playwell.service.config import all_local_service_config
        from playwell.service.message.bus import message_bus_manager

        if not all_local_service_config:
            return

        for local_service_config in all_local_service_config:
            service_path = local_service_config["service"]
            service = pydoc.locate(service_path)
            if not callable(service):
                raise PlaywellServiceException(
                    "The service %s is not a callable component" % service_path)

            if isinstance(service, type):
                service = service()

            local_service_meta = LocalServiceMeta(
                service,
                local_service_config["name"],
                message_bus_manager.input_message_bus.name,
                local_service_config.get("config", {})
            )
            self.register_local_service(local_service_meta)

    def register_local_service(self, service_meta: LocalServiceMeta):
        """register local service
        """
        if not isinstance(service_meta, LocalServiceMeta):
            raise ValueError("Only accept LocalServiceMeta object!")

        service_meta.init()
        self._all_local_service[service_meta.name] = service_meta
        result = self.register_service_meta(service_meta)
        if not result.is_ok():
            raise PlaywellServiceException(
                "Register local service error, the API return: %s" % result)

    def register_service_meta(self, service_meta: ServiceMeta):
        """Register service meta
        """
        from playwell.service.config import playwell_api
        response = requests.post(
            urljoin(playwell_api, "/v1/service_meta/register"),
            json={
                "name": service_meta.name,
                "message_bus": service_meta.message_bus,
                "config": service_meta.config
            }
        )
        return Result.from_response(response)

    def refresh_all(self):
        """Refresh all service meta from API
        """
        from playwell.service.config import playwell_api
        result = Result.from_response(requests.get(
            urljoin(playwell_api, "/v1/service_meta/all")))
        if not result.is_ok():
            raise PlaywellServiceException(
                "Refresh service meta failure, the API return: %s" % result)
        self._all_service_meta = {
            service_meta["name"]: ServiceMeta(**service_meta) 
            for service_meta in result.data["services"]
        }

    def get_service_meta(self, name: str):
        """Get service meta info by name
        """
        if name in self._all_local_service:
            return self._all_local_service[name]
        return self._all_service_meta.get(name)

    def get_local_service_meta(self, name: str):
        return self._all_local_service[name]


service_meta_manager = ServiceMetaManager()
