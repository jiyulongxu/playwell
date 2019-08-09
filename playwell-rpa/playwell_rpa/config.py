"""Playwell rpa config
"""


class ConfigItems:

    """RPA Config items
    """

    WEB_DRIVER = "web_driver"

    CLOUD_SERVICE = "cloud_service"

    REDIRECT_MESSAGE_BUS = "redirect_message_bus"


all_web_driver_config = {}

all_cloud_service_config = {}

redirect_message_bus = ""


def load_config(config_data: dict):
    """Load RPA config
    """
    global all_web_driver_config  # web driver
    all_web_driver_config = config_data.get(ConfigItems.WEB_DRIVER, {})

    global all_cloud_service_config  # cloud service
    all_cloud_service_config = config_data.get(ConfigItems.CLOUD_SERVICE, {})

    global redirect_message_bus
    redirect_message_bus = config_data.get(ConfigItems.REDIRECT_MESSAGE_BUS, "")
