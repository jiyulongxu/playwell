"""该模块会从配置文件中加载配置，并维护这些配置项
"""
import yaml
import pydoc

from playwell.service import PlaywellServiceException


class ConfigItems:

    LOG = "log"

    PLAYWELL_API = "playwell_api"

    INPUT_MESSAGE_BUS = "input_message_bus"

    WEB_SERVER = "web_server"

    ALL_LOCAL_SERVICE = "local_service"

    RUNNER = "runner"

    CONFIG_HOOK = "config_hook"

    LAUNCH_HOOK = "launch_hook"


log_config = {}

playwell_api = None  # playwell api base url

input_message_bus_config = None

all_local_service_config = {}

web_server_config = {}

runner_config = {}

launch_hook_path = None


def _load_config(config_data: dict):
    global log_config
    log_config = config_data.get(ConfigItems.LOG, {})

    global playwell_api
    playwell_api = config_data[ConfigItems.PLAYWELL_API]

    global input_message_bus_config
    input_message_bus_config = config_data.get(ConfigItems.INPUT_MESSAGE_BUS, {})

    global all_local_service_config
    all_local_service_config = config_data.get(ConfigItems.ALL_LOCAL_SERVICE, {})

    global web_server_config
    web_server_config = config_data.get(ConfigItems.WEB_SERVER, {})

    global runner_config
    runner_config = config_data.get(ConfigItems.RUNNER, {})

    if ConfigItems.CONFIG_HOOK in config_data:
        print(config_data[ConfigItems.CONFIG_HOOK])
        config_hook = pydoc.locate(config_data[ConfigItems.CONFIG_HOOK])
        if not callable(config_hook):
            raise PlaywellServiceException("The config hook must be callable!")
        config_hook(config_data)

    if ConfigItems.LAUNCH_HOOK in config_data:
        global launch_hook_path
        launch_hook_path = config_data[ConfigItems.LAUNCH_HOOK]


def load_config_from_yaml(config_file_path: str):
    """Load config with yaml config file
    """
    with open(config_file_path, "r", encoding="utf-8") as f:
        config_data = yaml.load(f, Loader=yaml.Loader)
        _load_config(config_data)
