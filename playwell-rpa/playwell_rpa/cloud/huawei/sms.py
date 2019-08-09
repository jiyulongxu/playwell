"""Huawei SMS Service
"""
import time
import uuid
import base64
import hashlib
import logging
import requests
from typing import Sequence
from urllib.parse import urljoin
from playwell.service import Result
from playwell.service.message import ServiceRequestMessage


class BatchSendService:

    """批量短信发送服务
    - name: huawei.sms.batch
      args:
        request:
          to: var("phone")
          template_id: str("id")
          template_params:
            - str("chihongze")
    """

    def __init__(self):
        self._app_key = ""
        self._app_secret = ""
        self._channel_id = ""
        self._api_base = ""
        self._signature = ""
        self._batch = 500

    def init(self, app_key, app_secret, channel_id, api_base, signature, batch=500):
        self._app_key = app_key
        self._app_secret = app_secret
        self._channel_id = channel_id
        self._api_base = api_base
        self._signature = signature
        self._batch = batch

    def __call__(self, requests: Sequence[ServiceRequestMessage]):
        sms_contents = []
        results = []

        for i, req in enumerate(requests):
            sms_contents.append({
                "to": [req.args["to"]],
                "templateId": req.args["template_id"],
                "templateParas": ["Chihz"],
                "signature": self._signature
            })

            if i % self._batch == 0:
                results += self._batch_send(sms_contents)
                sms_contents = []

        if sms_contents:
            results += self._batch_send(sms_contents)

        return results

    def _batch_send(self, sms_contents: Sequence[dict]):
        try:
            headers = {
                'Authorization': 'WSSE realm="SDP",profile="UsernameToken",type="Appkey"',
                'X-WSSE': _build_wsse_header(self._app_key, self._app_secret)
            }

            print(sms_contents)

            body = {
                "from": self._channel_id,
                "smsContent": sms_contents
            }

            response = requests.post(
                url=urljoin(self._api_base, "/sms/batchSendDiffSms/v1"),
                headers=headers,
                json=body,
                verify=False
            )

            send_result = response.json()

            if send_result["code"] == "000000":
                all_sms_status = {
                    sms_status["originTo"]: sms_status["status"] for sms_status in send_result["result"]
                }
                results = []
                for sms_content in sms_contents:
                    status = all_sms_status[sms_content["to"][0]]
                    if status == "000000":
                        results.append(Result.ok())
                    else:
                        results.append(Result.fail(error_code=status))
                return results
            else:
                return [Result.fail(error_code=send_result["code"])] * len(sms_contents)

        except Exception as e:
            logging.exception(e)
            return [Result.fail(error_code="sys_error", message=str(e))] * len(sms_contents)


def _build_wsse_header(app_key, app_secret):
    now = time.strftime('%Y-%m-%dT%H:%M:%SZ')
    nonce = str(uuid.uuid4()).replace('-', '')
    digest = hashlib.sha256((nonce + now + app_secret).encode()).hexdigest()

    digest_base64 = base64.b64encode(digest.encode()).decode()
    return 'UsernameToken Username="{}",PasswordDigest="{}",Nonce="{}",Created="{}"'.format(
        app_key, digest_base64, nonce, now)


def _batch_test():
    headers = {
        'Authorization': 'WSSE realm="SDP",profile="UsernameToken",type="Appkey"',
        'X-WSSE': _build_wsse_header("m97WWjK5FtIV7h29jsnWCWDb4I8e", "F13400QeCtVjUQ5Vm74Sj8zIw805")
    }

    sms_contents = [
        {
            "to": ["17600817832"],
            "templateId": "8856354b9b7e495fb6bd933882b9f9db",
            "templateParas": ["Chihz"],
            "signature": "企智未来"
        }
    ]

    api_base = "https://api.rtc.huaweicloud.com:10443"
    channel_id = "8819080633619"
    body = {
        "from": channel_id,
        "smsContent": sms_contents
    }

    try:
        response = requests.post(
            url=urljoin(api_base, "/sms/batchSendDiffSms/v1"),
            headers=headers,
            json=body,
            verify=False
        )
        print(headers)
        print(response.text)

        send_result = response.json()

        if send_result["code"] == "000000":
            all_sms_status = {
                sms_status["originTo"]: sms_status["status"] for sms_status in send_result["result"]
            }
            results = []
            for sms_content in sms_contents:
                status = all_sms_status[sms_content["to"][0]]
                if status == "000000":
                    results.append(Result.ok())
                else:
                    results.append(Result.fail(error_code=status))
            return results
        else:
            return [Result.fail(error_code=send_result["code"])] * len(sms_contents)

    except Exception as e:
        logging.exception(e)
        return [Result.fail(error_code="sys_error", message=str(e))] * len(sms_contents)


if __name__ == "__main__":
    print(_batch_test())
