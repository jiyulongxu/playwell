"""Browser common actions
"""
import pydoc
import logging

from selenium.common.exceptions import (
    NoSuchElementException,
    TimeoutException
)

from playwell.service import (
    Result,
    single_request_service
)
from playwell.service.message import ServiceRequestMessage


@single_request_service
def open_web_page(request: ServiceRequestMessage):
    """打开一个指定的web页面
    如果没有指定session_id，则会打开一个新的浏览器会话
    如果指定了session_id，则会直接使用已经存在的浏览器会话
    - name: open_browser
      type: browser.open
      args:
        request:
          browser: str("Chrome")
          url: str("https://www.apple.com")
          auto_close: yes
          wait:
            timeout: minutes(1)
            elements:
              - css_selector: str(".class_name")
          save_source: ref("source_code", text", "file:///tmp/index.html")
          gather:
            - name: str("id")
              css_selector: str("#id")
      ctrl:
        - when: resultOk()
          then: call("next")
          context_vars:
            browser_session_id: resultVar("session_id")
    """
    args = request.args

    url = args["url"]
    wait = args.get("wait", {})
    gather_elements = args.get("gather", [])

    from playwell_rpa.browser import (
        web_driver_manager,
        select_element
    )

    if "browser" in args:
        session_id = web_driver_manager.create_driver(
            activity_id=request.activity_id,
            domain_id=request.domain_id,
            browser=args["browser"],
            tmp=args.get("auto_close", True)
        )
    else:
        session_id = args["session_id"]
    driver = web_driver_manager.get_driver(session_id)

    if "page_load_timeout" in args:
        driver.set_page_load_timeout(args["page_load_timeout"])
    if "script_timeout" in args:
        driver.set_script_timeout(args["script_timeout"])

    if wait:
        timeout = wait["timeout"]
        driver.implicitly_wait(timeout)

    try:
        driver.get(url)
    except TimeoutException:
        logging.warning("Page load timeout: %s" % url)

    if wait:
        elements = wait["elements"]
        for element in elements:
            selector, selector_expr = element.popitem()
            try:
                select_element(driver, selector, selector_expr)
            except NoSuchElementException as e:
                logging.exception(e)
                return Result.fail(
                    error_code="wait_element_error", message=str(e))

    gathered = {}
    if gather_elements:
        for ge in gather_elements:
            try:
                item_name, text = None, None
                for k, v in ge.items():
                    if k == "name":
                        item_name = v
                    if k.endswith("_selector"):
                        text = select_element(driver, k, v).text
                gathered[item_name] = text
            except NoSuchElementException as e:
                logging.exception(e)

    if "save_source" in args:
        save_source = args["save_source"]
        from playwell_rpa.data.text import create_text
        text = create_text(
            type=save_source[0],
            activity_id=request.activity_id,
            domain_id=request.domain_id,
            name=save_source[1],
            meta=save_source[2],
            tmp=save_source[3]
        )
        text.write(driver.page_source)

    return Result.ok(data={
        "session_id": session_id,
        "gathered": gathered
    })


@single_request_service
def close_browser(request: ServiceRequestMessage):
    """关闭一个浏览器会话
    - name: close_browser
      type: browser.close
      args:
        request:
          session_id: var("session_id")
    """
    session_id = request.args["session_id"]
    from playwell_rpa.browser import web_driver_manager
    web_driver_manager.close_driver(session_id)
    return Result.ok()


@single_request_service
def click(request: ServiceRequestMessage):
    """click单元可以用于连续点击指定的一系列页面元素
    - name: click
      type: browser.click
      args:
        request:
          session_id: var("session_id")
          elements:
            - css_selector: str(".button_a")
            - name_selector: str("btn")
            - class_selector: str("btn")
    """
    args = request.args
    session_id, elements = args["session_id"], args["elements"]

    from playwell_rpa.browser import (
        web_driver_manager,
        select_element
    )
    driver = web_driver_manager.get_driver(session_id)
    for element_info in elements:
        selector, selector_expr = element_info.popitem()
        element = select_element(driver, selector, selector_expr)
        element.click()

    return Result.ok()


@single_request_service
def execute_selenium_script(request: ServiceRequestMessage):
    """执行Selenium脚本
    支持脚本文件或者具体的Python组件，会自动将工作单元参数和web driver对象注入到脚本当中

    - name: selenium_script
      type: browser.selenium
      args:
        request:
          session_id: var("session_id")
          script: str("file:///Users/chihongze/test.py")
          arg1: xxxx
          arg2: xxxx

    OR

    - name: selenium_script
      type: browser.selenium
      args:
        request:
          session_id: var("session_id")
          component: str("my.rpa.test")
          arg1: xxxx
          arg2: xxx
    """
    args = request.args
    session_id = args["session_id"]

    from playwell_rpa.browser import web_driver_manager
    web_driver = web_driver_manager.get_driver(session_id)

    if "script" in args:
        with open(args["script"], "r") as f:
            code = f.read()
            exec(code, {
                "web_driver": web_driver,
                "activity_id": request.activity_id,
                "domain_id": request.domain_id,
                "args": args
            })
        return Result.ok()
    elif "component" in args:
        component = pydoc.locate(args["component"])
        if not callable(component):
            return Result.fail(
                error_code="invalid_component",
                message="The component is not callable"
            )
        result = component(
            web_driver,
            request.activity_id,
            request.domain_id,
            args
        )
        if result is None:
            return Result.ok()
        else:
            return Result.ok({"result": result})
    else:
        return Result.fail(
            error_code="invalid_arg",
            message="没有指定要执行的Selenium脚本或组件"
        )
