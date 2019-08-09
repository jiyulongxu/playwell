"""Message proxy launcher
"""
import atexit
from playwell.service.message.proxy.config import load_config_from_yaml


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

    # 初始化proxy
    from playwell.service.message.proxy.http import HttpServiceRequestProxy
    proxy = HttpServiceRequestProxy.build_with_config()
    atexit.register(proxy.close)
    proxy.start()


def main():
    import sys
    if len(sys.argv) < 2:
        print("Need config file path: playwell_service_proxy ./config.yaml")
        exit(1)
    launch(sys.argv[1])
