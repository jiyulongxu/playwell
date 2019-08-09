"""Web form actions
"""
import logging
from playwell.service import (
    Result,
    single_request_service
)
from playwell.service.message import ServiceRequestMessage


@single_request_service
def submit_form(request: ServiceRequestMessage):
    """该Action可以用于自动填充表单并提交
    - name: submit_form
      type: browser.form
      args:
        request:
          session_id: var("session_id")
          input:
            - css_selector: str(".username")
              input: str("Sam")
            - css_selector: str(".password")
              input: str("12345")
          submit:
            css_selector: str(".submit")
    """
    from playwell_rpa.browser import (
        web_driver_manager,
        select_element
    )
    args = request.args
    driver = web_driver_manager.get_driver(args["session_id"])

    # handle input elements
    input_elements = args["input"]
    for input_element_arg in input_elements:
        selector, selector_expr, input_value = None, None, None
        for k, v in input_element_arg.items():
            if k.endswith("_selector"):
                selector = k
                selector_expr = v
            elif k == "input":
                input_value = v
        input_element = select_element(driver, selector, selector_expr)
        input_element.click()
        input_element.send_keys(input_value)

    # click submit button
    submit_element_args = args["submit"]
    selector, selector_expr = submit_element_args.popitem()

    try:
        select_element(driver, selector, selector_expr).submit()
    except Exception as e:
        logging.warning("Submit error: %s, try click" % str(e))
        select_element(driver, selector, selector_expr).click()

    return Result.ok()
