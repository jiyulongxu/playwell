"""RPA launcher
"""


def launch():
    # 加载web driver
    from playwell_rpa.browser import init_web_driver_manager
    init_web_driver_manager()
