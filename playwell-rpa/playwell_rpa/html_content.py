from bs4 import BeautifulSoup
from playwell.service import (
    Result,
    single_request_service
)
from playwell.service.message import ServiceRequestMessage
from playwell_rpa.data.text import locate_text
from playwell_rpa.data.table import locate_from_request
from playwell_rpa.util import convert


@single_request_service
def to_table(request: ServiceRequestMessage):
    """将HTML中的指定元素和属性提取成为Table对象
    - name: html_to_table
      type: html.to_table
      args:
        request:
          html: ref("memory_text", "html_text")
          elements: str(".gl-i-wrap")
          table: ref("excel_type", "the_table")
          columns:
            - element: str(".p-name a")
              attr: str("title")
            - element: str(".p-price .i")
              type: str("decimal")
              default: str("xxx")
    """
    args = request.args
    html_content = _get_html_content("html", request)

    bs = BeautifulSoup(html_content, 'html.parser')
    elements = bs.select(args["elements"])

    rows = []
    for element in elements:
        row = []
        for declare in args["columns"]:
            row.append(_get_value_from_element(element, declare))
        rows.append(row)

    table = locate_from_request("table", request)
    table.append_rows(rows)

    return Result.ok()


def _get_html_content(ref_name, request):
    html_ref = request.args[ref_name]
    if isinstance(html_ref, str):
        # web driver session
        from playwell_rpa.browser import web_driver_manager
        driver = web_driver_manager.get_driver(html_ref)
        return driver.page_source
    else:
        # text类型
        type_, name, meta, _tmp = html_ref
        text = locate_text(
            type=type_,
            activity_id=request.activity_id,
            domain_id=request.domain_id,
            name=name,
            meta=meta
        )
        return text.read()


def _get_value_from_element(p_element, selector_declare):
    """从html element中获取指定的值
    """
    element_expr = selector_declare["element"]
    element = p_element.select_one(element_expr)
    if element is None:
        return selector_declare.get("default")

    if "attr" in selector_declare:
        attr = selector_declare["attr"]
        try:
            value = element[attr]
        except KeyError:
            value = selector_declare.get("default")
    else:
        value = element.text

    if "type" in selector_declare:
        value = convert.convert(selector_declare["type"], value)

    if isinstance(value, str):
        value = value.strip()

    return value
