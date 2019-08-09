"""该模块包含对文本数据类型的各种处理
"""
from abc import (
    ABCMeta,
    abstractmethod
)
from playwell.service import PlaywellServiceException
from playwell_rpa.resource import resource_tracer


class Text(metaclass=ABCMeta):

    def __init__(self, name):
        self._name = name

    @property
    def name(self):
        return self._name

    @abstractmethod
    def read(self):
        pass

    @abstractmethod
    def write(self, text):
        pass

    @abstractmethod
    def append(self, text):
        pass


class MemoryText(Text):

    ALL_MEMORY_TEXT = {}

    @classmethod
    def register_text(cls, activity_id: int, domain_id: str, name: str, _meta):
        full_name = MemoryText._full_text_name(activity_id, domain_id, name)
        text = cls(name)
        MemoryText.ALL_MEMORY_TEXT[full_name] = text
        return text

    @staticmethod
    def locate_text(activity_id: int, domain_id: str, name: str, _meta):
        full_name = MemoryText._full_text_name(activity_id, domain_id, name)
        if full_name not in MemoryText.ALL_MEMORY_TEXT:
            raise PlaywellServiceException("找不到名为%s的memory_text" % name)
        return MemoryText.ALL_MEMORY_TEXT[name]

    @staticmethod
    def drop_text(activity_id: int, domain_id: str, name: str):
        full_name = MemoryText._full_text_name(activity_id, domain_id, name)
        if full_name not in MemoryText.ALL_MEMORY_TEXT:
            raise PlaywellServiceException("找不到名为%s的memory_text" % name)
        del MemoryText.ALL_MEMORY_TEXT[full_name]

    @staticmethod
    def _full_text_name(activity_id: int, domain_id: str, name: str):
        return "%d:%s:%s" % (activity_id, domain_id, name)

    def __init__(self, name):
        super().__init__(name)
        self._text = ""

    def read(self):
        return self._text

    def write(self, text):
        if text is None:
            text = ""
        self._text = text

    def append(self, text):
        if text is None:
            return
        self._text += text


class FileText(Text):

    """基于本地文件存储的Text
    """

    ALL_FILE_TEXT_META = {}

    @classmethod
    def register_text(cls, activity_id: int, domain_id: str, name: str, meta: dict):
        full_name = FileText._full_text_name(activity_id, domain_id, name)
        FileText.ALL_FILE_TEXT_META[full_name] = meta
        return cls(name, meta["path"])

    @staticmethod
    def locate_text(activity_id: int, domain_id: str, name: str, _meta):
        full_name = FileText._full_text_name(activity_id, domain_id, name)
        if full_name not in FileText.ALL_FILE_TEXT_META:
            raise PlaywellServiceException("找不到名为%s的file_text" % name)
        file_path = FileText.ALL_FILE_TEXT_META[full_name]["path"]
        return FileText(name, file_path)

    @staticmethod
    def drop_text(activity_id: int, domain_id: str, name: str):
        full_name = FileText._full_text_name(activity_id, domain_id, name)
        if full_name not in FileText.ALL_FILE_TEXT_META:
            raise PlaywellServiceException("找不到名为%s的file_text" % name)
        del FileText.ALL_FILE_TEXT_META[full_name]

    @staticmethod
    def _full_text_name(activity_id: int, domain_id: str, name: str):
        return "%d:%s:%s" % (activity_id, domain_id, name)

    def __init__(self, name, file_path):
        super().__init__(name)
        self._file_path = file_path

    def read(self):
        with open(self._file_path, "r") as f:
            return f.read()

    def write(self, text):
        with open(self._file_path, "w") as f:
            f.write(text)

    def append(self, text):
        with open(self._file_path, "a") as f:
            f.write(text)


_text_types = {
    "memory_text": MemoryText,
    "file_text": FileText
}

for type, type_class in _text_types.items():
    resource_tracer.register_handler(type, getattr(type_class, "drop_text"))


def create_text(type: str, activity_id: int, domain_id: str, name: str, meta: dict, tmp: bool):
    global _text_types
    _check_type(type)
    text = _text_types[type].register_text(activity_id, domain_id, name, meta)
    if tmp:
        resource_tracer.trace(
            activity_id=activity_id,
            domain_id=domain_id,
            type=type,
            name=name
        )
    return text


def locate_text(type: str, activity_id: int, domain_id: str, name: str, meta: dict):
    global _text_types
    _check_type(type)
    return _text_types[type].locate_text(activity_id, domain_id, name, meta)


def drop_text(type: str, activity_id: int, domain_id: str, name: str):
    global _text_types
    _check_type(type)
    return _text_types[type].drop_text(activity_id, domain_id, name)


def _check_type(type: str):
    if type not in _text_types:
        raise PlaywellServiceException("未知的Text类型: %s" % type)
