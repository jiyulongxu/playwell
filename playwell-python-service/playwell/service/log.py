"""log
"""


class LogConfigItems:

    """LogConfigItems
    """

    FILENAME = "filename"
    DEFAULT_FILENAME = "playwell.log"

    LEVEL = "level"
    DEFAULT_LEVEL = "INFO"

    MAX_BYTES = "max_bytes"
    DEFAULT_MAX_BYTES = 1024 * 1024

    BACKUP_COUNT = "backup_count"
    DEFAULT_MAX_BACKUP_COUNT = 3


def init_logging():
    """init log config
    """
    from playwell.service.config import log_config

    import logging
    from logging.handlers import RotatingFileHandler
    logging.root.setLevel(getattr(logging, log_config.get(
        LogConfigItems.LEVEL,
        LogConfigItems.DEFAULT_LEVEL
    )))
    rotating_handler = RotatingFileHandler(
        log_config.get(
            LogConfigItems.FILENAME,
            LogConfigItems.DEFAULT_FILENAME
        ),
        maxBytes=log_config.get(
            LogConfigItems.MAX_BYTES,
            LogConfigItems.DEFAULT_MAX_BYTES
        ),
        backupCount=log_config.get(
            LogConfigItems.BACKUP_COUNT,
            LogConfigItems.DEFAULT_MAX_BACKUP_COUNT
        ),
        encoding="UTF-8"
    )
    rotating_handler.setFormatter(
        logging.Formatter("%(asctime)s %(name)s:%(levelname)s:%(message)s"))
    logging.root.addHandler(rotating_handler)
