import yaml


class ConfigItems:

    LOG = "log"

    WEB_SERVER = "web_server"

    PROXY = "proxy"


log_config = {}

web_server_config = {}

proxy_config = {}


def _load_config(config_data: dict):
    global log_config
    log_config = config_data.get(ConfigItems.LOG, {})

    global web_server_config
    web_server_config = config_data[ConfigItems.WEB_SERVER]

    global proxy_config
    proxy_config = config_data[ConfigItems.PROXY]


def load_config_from_yaml(config_file_path: str):
    """Load config with yaml config file
    """
    with open(config_file_path, "r") as f:
        config_data = yaml.load(f, Loader=yaml.Loader)
        _load_config(config_data)
