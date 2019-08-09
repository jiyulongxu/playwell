"""上行短信接收
"""
import time
import logging
from bottle import request as web_request


def launch():
    from playwell.service.resource.web import web_server
    from playwell.service.message import Message
    from playwell.service.message.bus import message_bus_manager
    from playwell_rpa.config import redirect_message_bus

    @web_server.app.post("/sms")
    def handle_sms():
        try:
            forms = web_request.forms
            sender = forms.getunicode("from")
            body = forms.getunicode("body")
            logging.info("sender: %s, body: %s" % (sender, body))
            message_bus = message_bus_manager.get_message_bus(redirect_message_bus)
            message_bus.write([Message(
                type="sms",
                sender="",
                receiver="",
                attr={
                    "sender": sender,
                    "content": body
                },
                time=int(time.time() * 1000)
            )])
        except Exception as e:
            logging.exception(e)

        return {
            "returnCode": 0,
            "returnCodeDesc": "Success"
        }
