"""简单SQL执行
"""
import logging
import pymysql
import pymysql.cursors
from typing import Sequence
from playwell.service import Result
from playwell.service.message import ServiceRequestMessage


class ExecuteSQL:

    """执行SQL语句
    """

    def __init__(self):
        self._config = {}

    def init(self, **config):
        self._config = config

    def __call__(self, requests: Sequence[ServiceRequestMessage]):
        connection = pymysql.connect(
            cursorclass=pymysql.cursors.DictCursor,
            **self._config
        )
        try:
            with connection.cursor() as cursor:
                for req in requests:
                    sql = req.args["sql"]
                    params = req.args["params"]
                    cursor.execute(sql, params)
            connection.commit()
            return [Result.ok()] * len(requests)
        except Exception as e:
            logging.exception(e)
            return [Result.fail(
                error_code="sys_error",
                message=str(e)
            )] * len(requests)
        finally:
            connection.close()
