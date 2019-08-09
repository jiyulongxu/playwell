"""launcher
"""
import pydoc
import atexit

from playwell.service import PlaywellServiceException
from playwell.service.config import load_config_from_yaml


def launch(config_file_path: str):
    """初始化配置 & 开始运行
    """

    load_config_from_yaml(config_file_path)

    # 初始化日志
    from playwell.service.log import init_logging
    init_logging()

    # 初始化web server
    from playwell.service.resource.web import start_web_server
    start_web_server()

    # 初始化launch hook
    from playwell.service.config import launch_hook_path
    if launch_hook_path is not None:
        launch_hook = pydoc.locate(launch_hook_path)
        if not callable(launch_hook):
            raise PlaywellServiceException(
                "The launch hook %s must be callable" % launch_hook_path)
        launch_hook()

    # 注册input message bus
    from playwell.service.message.bus import message_bus_manager
    message_bus_manager.register_input_message_bus()

    # 注册local service
    from playwell.service.meta import service_meta_manager
    service_meta_manager.register_all_local_service()

    # 构建ServiceRunner
    from playwell.service.runner import ServiceRunner
    service_runner = ServiceRunner.from_config()
    atexit.register(service_runner.close)

    service_runner.run()


def main():
    import sys
    if len(sys.argv) < 2:
        print("Need config file path: playwell_service ./config.yaml")
        exit(1)
    launch(sys.argv[1])


if __name__ == "__main__":
    main()
