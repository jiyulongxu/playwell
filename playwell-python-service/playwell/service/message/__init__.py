"""Base message module
"""
import json
from collections import UserDict

from playwell.service import Result


class MessageTypes:
    
    """Message type constants
    """

    REQUEST = "req"  # Service request message

    RESPONSE = "res"  # Service response message


class Attributes(UserDict):
    
    """Attributes of message
    """

    def __init__(self, attributes: dict):
        super().__init__(attributes)

    def __getattr__(self, name):
        return self[name]


class Message:
    
    FIELD_TYPE = "type"

    FIELD_SENDER = "sender"

    FIELD_RECEIVER = "receiver"

    FIELD_ATTR = "attr"

    FIELD_TIME = "time"

    def __init__(
        self,
        type: str,
        sender: str,
        receiver: str,
        attr: dict,
        time: int
    ):
        self._type = type
        self._sender = sender
        self._receiver = receiver
        self._attr = attr
        self._time = time

    @property
    def type(self):
        return self._type

    @property
    def sender(self):
        return self._sender

    @property
    def receiver(self):
        return self._receiver

    @property
    def attr(self):
        return Attributes(self._attr)

    @property
    def time(self):
        return self._time

    def to_dict(self):
        return {
            Message.FIELD_TYPE: self._type,
            Message.FIELD_SENDER: self._sender,
            Message.FIELD_RECEIVER: self._receiver,
            Message.FIELD_ATTR: self._attr,
            Message.FIELD_TIME: self._time
        }

    def __repr__(self):
        return json.dumps(self.to_dict())

    def __str__(self):
        return self.__repr__()


class ServiceRequestMessage(Message):
    
    """Service request message
    """

    ATTR_ACTIVITY = "activity"

    ATTR_DOMAIN = "domain"

    ATTR_ACTION = "action"

    ATTR_ARGS = "args"

    ATTR_IGNORE_RESULT = "ignore_result"

    @classmethod
    def from_dict(cls, data: dict):
        return cls(
            sender=data[Message.FIELD_SENDER],
            receiver=data[Message.FIELD_RECEIVER],
            time=data[Message.FIELD_TIME],
            activity_id=data[Message.FIELD_ATTR][ServiceRequestMessage.ATTR_ACTIVITY],
            domain_id=data[Message.FIELD_ATTR][ServiceRequestMessage.ATTR_DOMAIN],
            action=data[Message.FIELD_ATTR][ServiceRequestMessage.ATTR_ACTION],
            args=data[Message.FIELD_ATTR][ServiceRequestMessage.ATTR_ARGS],
            ignore_result=data[Message.FIELD_ATTR].get(ServiceRequestMessage.ATTR_IGNORE_RESULT, False)
        )
    
    def __init__(
        self,
        sender: str,
        receiver: str,
        time: int,
        activity_id: int,
        domain_id: str,
        action: str,
        args,
        ignore_result: bool = False
    ):
        super().__init__(
            type=MessageTypes.REQUEST,
            sender=sender,
            receiver=receiver,
            attr={
                ServiceRequestMessage.ATTR_ACTIVITY: activity_id,
                ServiceRequestMessage.ATTR_DOMAIN: domain_id,
                ServiceRequestMessage.ATTR_ACTION: action,
                ServiceRequestMessage.ATTR_ARGS: args,
                ServiceRequestMessage.ATTR_IGNORE_RESULT: ignore_result
            },
            time=time
        )
        self._activity_id = activity_id
        self._domain_id = domain_id
        self._action = action
        self._args = args
        self._ignore_result = ignore_result

    @property
    def activity_id(self):
        return self._activity_id

    @property
    def domain_id(self):
        return self._domain_id

    @property
    def action(self):
        return self._action

    @property
    def args(self):
        return self._args

    @property
    def ignore_result(self):
        return self._ignore_result


class ServiceResponseMessage(Message):

    """Service Response message
    """

    ATTR_ACTIVITY = "activity"

    ATTR_DOMAIN = "domain"

    ATTR_ACTION = "action"

    ATTR_STATUS = "status"

    ATTR_ERROR_CODE = "error_code"

    ATTR_MESSAGE = "message"

    ATTR_DATA = "data"

    @classmethod
    def from_result(cls, time: int, request_message: ServiceRequestMessage, result: Result):
        return cls(
            sender=request_message.receiver,
            receiver=request_message.sender,
            time=time,
            activity_id=request_message.activity_id,
            domain_id=request_message.domain_id,
            action=request_message.action,
            status=result.status,
            error_code=result.error_code,
            message=result.message,
            data=result.data
        )

    @classmethod
    def from_dict(cls, data: dict):
        return cls(
            sender=data[Message.FIELD_SENDER],
            receiver=data[Message.FIELD_RECEIVER],
            time=data[Message.FIELD_TIME],
            activity_id=data[Message.FIELD_ATTR][ServiceResponseMessage.ATTR_ACTIVITY],
            domain_id=data[Message.FIELD_ATTR][ServiceResponseMessage.ATTR_DOMAIN],
            action=data[Message.FIELD_ATTR][ServiceResponseMessage.ATTR_ACTION],
            status=data[Message.FIELD_ATTR][ServiceResponseMessage.ATTR_STATUS],
            error_code=data[Message.FIELD_ATTR].get(ServiceResponseMessage.ATTR_ERROR_CODE, ""),
            message=data[Message.FIELD_ATTR].get(ServiceResponseMessage.ATTR_MESSAGE, ""),
            data=data[Message.FIELD_ATTR].get(ServiceResponseMessage.ATTR_DATA, {})
        )

    def __init__(
        self,
        sender: str,
        receiver: str,
        time: int,
        activity_id: int,
        domain_id: str,
        action: str,
        status: str,
        error_code: str = "",
        message: str = "",
        data: dict = None
    ):
        super().__init__(
            type=MessageTypes.RESPONSE,
            sender=sender,
            receiver=receiver,
            attr={
                ServiceResponseMessage.ATTR_ACTIVITY: activity_id,
                ServiceResponseMessage.ATTR_DOMAIN: domain_id,
                ServiceResponseMessage.ATTR_ACTION: action,
                ServiceResponseMessage.ATTR_STATUS: status,
                ServiceResponseMessage.ATTR_ERROR_CODE: error_code,
                ServiceResponseMessage.ATTR_MESSAGE: message,
                ServiceResponseMessage.ATTR_DATA: data
            },
            time=time
        )
        self._activity_id = activity_id
        self._domain_id = domain_id
        self._action = action
        self._result = Result(
            status=status,
            error_code=error_code if error_code is not None else "",
            message=message if message is not None else "",
            data=data if data is not None else {}
        )

    @property
    def activity_id(self):
        return self._activity_id

    @property
    def domain_id(self):
        return self._domain_id

    @property
    def action(self):
        return self._action

    @property
    def result(self):
        return self._result
