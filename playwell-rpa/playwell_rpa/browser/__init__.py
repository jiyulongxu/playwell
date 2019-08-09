"""Browser base module
"""
import logging
from selenium import webdriver
from playwell.service import PlaywellServiceException
from playwell_rpa.resource import resource_tracer


class WebDriverManager:

    """Manage web driver with session id
    """

    def __init__(self, all_driver_config):
        self._drivers = dict()
        self._all_driver_config = all_driver_config
        resource_tracer.register_handler("browser", self._gc_handler)

    def create_driver(self, activity_id: int, domain_id: str, browser, tmp: bool = True):
        """Create new web driver by browser and config
        """
        if not hasattr(webdriver, browser):
            raise PlaywellServiceException(
                "Could not found the web driver of browser: %s" % browser)
        config = self._all_driver_config.get(browser, {})
        driver = getattr(webdriver, browser)(**config)
        self._drivers[driver.session_id] = driver

        if tmp:
            resource_tracer.trace(
                activity_id=activity_id,
                domain_id=domain_id,
                type="browser",
                name=driver.session_id
            )

        logging.info("Created web driver session: %s" % driver.session_id)
        return driver.session_id

    def get_driver(self, session_id):
        """Get web driver by session id
        """
        if session_id not in self._drivers:
            raise PlaywellServiceException(
                "Could not found web driver session id: %s" % session_id)
        return self._drivers[session_id]

    def close_driver(self, session_id):
        """Close web driver by session id
        """
        driver = self.get_driver(session_id)
        driver.close()
        logging.info("Closed web driver session: %s" % session_id)
        del self._drivers[session_id]

    def _gc_handler(self, _activity_id, _domain_id, session_id):
        self.close_driver(session_id)


web_driver_manager = None


def init_web_driver_manager():
    from playwell_rpa.config import all_web_driver_config
    global web_driver_manager
    web_driver_manager = WebDriverManager(all_web_driver_config)


# Selenium element selectors
_all_element_selectors = {
  "class": lambda driver, expr: driver.find_element_by_class_name(expr),
  "css": lambda driver, expr: driver.find_element_by_css_selector(expr),
  "id": lambda driver, expr: driver.find_element_by_id(expr),
  "name": lambda driver, expr: driver.find_element_by_name(expr),
  "tag_name": lambda driver, expr: driver.find_element_by_tag_name(expr),
  "xpath": lambda driver, expr: driver.find_element_by_xpath(expr)
}

# Selenium elements selector
_all_elements_selectors = {
    "class": lambda driver, expr: driver.find_elements_by_class_name(expr),
    "css": lambda driver, expr: driver.find_elements_by_css_selector(expr),
    "id": lambda driver, expr: driver.find_elements_by_id(expr),
    "name": lambda driver, expr: driver.find_elements_by_name(expr),
    "tag_name": lambda driver, expr: driver.find_elements_by_tag_name(expr),
    "xpath": lambda driver, expr: driver.find_elements_by_xpath(expr)
}


def select_element(driver, selector, selector_expr):
    """Select web page element with selector
    """
    global _all_element_selectors
    return _all_element_selectors[selector.rsplit("_", 1)[0]](
      driver, selector_expr)


def select_elements(driver, selector, selector_expr):
    """Select web page elements with selector
    """
    global _all_elements_selectors
    return _all_elements_selectors[selector.rsplit("_", 1)[0]](
        driver, selector_expr)
