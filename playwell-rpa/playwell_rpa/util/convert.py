"""convert模块提供了类型转换的相关工具
"""
from decimal import Decimal
from playwell.service import PlaywellServiceException


def to_decimal(data, quantize="0.00"):
    return Decimal(data).quantize(Decimal(quantize))


_converts = {
    "int": int,
    "str": str,
    "string": str,
    "float": float,
    "bool": bool,
    "decimal": to_decimal
}


def convert(target_type: str, data):
    """将数据转换为目标类型
    """
    global _converts
    if target_type not in _converts:
        raise PlaywellServiceException(
            "Unknown convert type: %s" % target_type)
    return _converts[target_type](data)
