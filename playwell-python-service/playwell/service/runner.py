"""Playwell service runner
"""
import time
import logging
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed

from playwell.service import PlaywellServiceException
from playwell.service.executor import ThreadPoolServiceExecutor


class ServiceRunner:

    """Service runner
    """

    @classmethod
    def from_config(cls):
        from playwell.service.config import runner_config
        executor_type = runner_config.get("executor", "thread_pool")
        executor_workers = runner_config.get("executor_workers", 10)
        if executor_type == "thread_pool":
            executor = ThreadPoolServiceExecutor(max_workers=executor_workers)
        else:
            raise PlaywellServiceException(
                "Unknown service executor type: %s" % executor_type)

        sleep_time = runner_config.get("sleep_time", 1)
        fetch_requests_num = runner_config.get("fetch_requests_num", 1000)
        response_workers = runner_config.get("response_workers", 4)
        send_response_timeout = runner_config.get("send_response_timeout", 10)
        only_refresh = runner_config.get("only_refresh", False)

        return ServiceRunner(
            service_executor=executor,
            sleep_time=sleep_time,
            fetch_requests_num=fetch_requests_num,
            response_workers=response_workers,
            send_response_timeout=send_response_timeout,
            only_refresh=only_refresh
        )

    def __init__(
            self,
            service_executor=None,
            sleep_time=1,
            fetch_requests_num=1000,
            response_workers=4,
            send_response_timeout=10,
            only_refresh=False
    ):
        self._service_executor = ThreadPoolServiceExecutor() \
            if service_executor is None else service_executor
        self._sleep_time = sleep_time
        self._fetch_requests_num = fetch_requests_num
        self._response_executor = ThreadPoolExecutor(max_workers=response_workers)
        self._send_response_timeout = send_response_timeout
        self._only_refresh = only_refresh
        self._closed = False

    def run(self):
        from playwell.service.meta import service_meta_manager
        from playwell.service.message.bus import message_bus_manager

        while True:
            if self._closed:
                break
            try:
                message_bus_manager.refresh_all()
                service_meta_manager.refresh_all()

                if not self._only_refresh:
                    self._handle_response(
                        message_bus_manager,
                        service_meta_manager
                    )

                    self._handle_requests(
                        message_bus_manager,
                        service_meta_manager
                    )
            except Exception as e:
                logging.exception(e)
            finally:
                time.sleep(self._sleep_time)

    def _handle_requests(
            self, message_bus_manager, service_meta_manager):
        input_message_bus = message_bus_manager.input_message_bus
        all_requests = input_message_bus.read(self._fetch_requests_num)
        if not all_requests:
            return

        requests_by_service_name = defaultdict(list)
        for request in all_requests:
            requests_by_service_name[request.receiver].append(request)

        for service_name, requests in requests_by_service_name.items():
            service = service_meta_manager.get_local_service_meta(service_name)
            self._service_executor.execute(service, requests)

    def _handle_response(self, message_bus_manager, service_meta_manager):
        all_response = self._service_executor.get_response()
        if not all_response:
            return
        response_by_service = defaultdict(list)
        for response in all_response:
            response_by_service[response.receiver].append(response)

        futures = []
        for service_name, response_seq in response_by_service.items():
            service_meta = service_meta_manager.get_service_meta(service_name)
            message_bus_name = service_meta.message_bus
            message_bus = message_bus_manager.get_message_bus(message_bus_name)
            future = self._response_executor.submit(message_bus.write, response_seq)
            futures.append(future)
        as_completed(futures, timeout=self._send_response_timeout)

    def close(self):
        self._closed = True
        self._response_executor.shutdown()
        self._service_executor.close()
        logging.info("Playwell service container already closed!")
