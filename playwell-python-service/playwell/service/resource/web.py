"""Web server resource, base on bottle framework
"""
import threading

from bottle import (
    Bottle,
    run
)


web_server = None


class ConfigItems:

    """Web server配置项
    """

    SERVER_TYPE = "server_type"  # web服务基于的IO模型，比如gevent、tornado、gunicorn等等

    HOST = "host"  # 监听的host地址

    PORT = "port"  # 监听的端口号

    SERVER_OPTIONS = "server_options"  # web服务配置选项


class WebServer:

    """Bottle web server container
    """

    def __init__(self, config: dict, app: Bottle = None):
        self._server_type = config.get(ConfigItems.SERVER_TYPE, "wsgiref")
        self._host = config[ConfigItems.HOST]
        self._port = config[ConfigItems.PORT]
        self._server_options = config.get(ConfigItems.SERVER_OPTIONS, {})
        self._app = Bottle() if app is None else app

    @property
    def server_type(self):
        return self._server_type

    @property
    def host(self):
        return self._host

    @property
    def port(self):
        return self._port

    @property
    def server_options(self):
        return self._server_options

    @property
    def app(self):
        return self._app

    def run(self):
        """Run web server
        """
        run(
            app=self.app,
            server=self.server_type,
            host=self.host,
            port=self.port,
            quiet=True,
            **self.server_options
        )


def start_web_server():
    global web_server
    from playwell.service.config import web_server_config
    if not web_server_config:
        return
    web_server = WebServer(web_server_config)
    threading.Thread(target=web_server.run, daemon=True).start()
