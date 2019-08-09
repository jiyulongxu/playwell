"""Playwell system API
"""
from playwell import (
    API,
    Arg,
    ArgPos,
    Methods,
)


## Refresh compare and callback item ##
REFRESH_CAC = API(
    Methods.POST,
    "/v1/system/CAC/refresh",
    (
        Arg(
            "datasource",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target data source"
            }
        ),
        Arg(
            "item",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target item"
            }
        ),
    )
)

## Seek kafka offset ##
SEEK_KAFKA_OFFSET = API(
    Methods.POST,
    "/v1/system/kafka/seek_to_offset",
    (
        Arg(
            "consumer",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka consumer"
            }
        ),
        Arg(
            "topic",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka topic"
            }
        ),
        Arg(
            "partition",
            ArgPos.BODY,
            {
                "required": False,
                "default": -1,
                "type": int,
                "help": "The target kafka partition, only partition consumer need this arg"
            }
        ),
        Arg(
            "offset",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The new offset"
            }
        )
    )
)

## Seek kafka offset to beginning ##
SEEK_KAFKA_OFFSET_TO_BEGINNING = API(
    Methods.POST,
    "/v1/system/kafka/seek_to_beginning",
    (
        Arg(
            "consumer",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka consumer"
            }
        ),
        Arg(
            "topic",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka topic"
            }
        ),
        Arg(
            "partition",
            ArgPos.BODY,
            {
                "required": False,
                "default": -1,
                "type": int,
                "help": "The target kafka partition, only partition consumer need this arg"
            }
        ),
    )
)

## Seek kafka offset to end ##
SEEK_KAFKA_OFFSET_TO_END = API(
    Methods.POST,
    "/v1/system/kafka/seek_to_end",
    (
        Arg(
            "consumer",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka consumer"
            }
        ),
        Arg(
            "topic",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka topic"
            }
        ),
        Arg(
            "partition",
            ArgPos.BODY,
            {
                "required": False,
                "default": -1,
                "type": int,
                "help": "The target kafka partition, only partition consumer need this arg"
            }
        ),
    )
)

## Seek kafka offset to timestamp ##
SEEK_KAFKA_OFFSET_TO_TIMESTAMP = API(
    Methods.POST,
    "/v1/system/kafka/seek_to_timestamp",
    (
        Arg(
            "consumer",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka consumer"
            }
        ),
        Arg(
            "topic",
            ArgPos.BODY,
            {
                "required": True,
                "help": "The target kafka topic"
            }
        ),
        Arg(
            "partition",
            ArgPos.BODY,
            {
                "required": False,
                "default": -1,
                "type": int,
                "help": "The target kafka partition, only partition consumer need this arg"
            }
        ),
        Arg(
            "timestamp",
            ArgPos.BODY,
            {
                "required": True,
                "type": int,
                "help": "The target timestamp"
            }
        )
    )
)

CLOSE_CONSUMER = API(
    Methods.POST,
    "/v1/system/kafka/close_consumer",
    (
        Arg(
            "consumer",
            ArgPos.BODY,
            {
                "required": True,
                "type": str,
                "help": "The name of the consumer"
            }
        ),
    )
)

FULL_COMPACT_ROCKSDB = API(
    Methods.POST,
    "/v1/system/rocksdb/full_compact",
    (
        Arg(
            "cf",
            ArgPos.BODY,
            {
                "required": True,
                "type": str,
                "help": "The column family name"
            }
        ),
    )
)

ROCKSDB_CHECKPOINT = API(
    Methods.POST,
    "/v1/system/rocksdb/checkpoint",
    (
        Arg(
            "dir",
            ArgPos.BODY,
            {
                "required": True,
                "type": str,
                "help": "The checkpoint dir"
            }
        ),
        Arg(
            "async",
            ArgPos.BODY,
            {
                "required": False,
                "type": bool,
                "default": False,
                "help": "Use async operation"
            }
        )
    )
)

SHOW_ROCKSDB_BACKUPS = API(
    Methods.GET,
    "/v1/system/rocksdb/backup"
)

CREATE_ROCKSDB_BACKUP = API(
    Methods.POST,
    "/v1/system/rocksdb/backup",
    (
        Arg(
            "flush_before_backup",
            ArgPos.BODY,
            {
                "required": False,
                "type": bool,
                "default": False,
                "help": "Flush memtable before backup"
            }
        ),
    )
)

PURGE_OLD_ROCKSDB_BACKUPS = API(
    Methods.POST,
    "/v1/system/rocksdb/backup/purge",
    (
        Arg(
            "num",
            ArgPos.BODY,
            {
                "required": True,
                "type": int,
                "help": "The number of RocksDB backups to purge"
            }
        ),
    )
)

SHOW_PERF = API(
    Methods.GET,
    "/v1/system/perflog"
)

PERFLOG = API(
    Methods.POST,
    "/v1/system/perflog",
    (
        Arg(
            "enable",
            ArgPos.BODY,
            {
                "action": "store_true",
                "help": "Enable perflog"
            }
        ),
        Arg(
            "period",
            ArgPos.BODY,
            {
                "required": False,
                "default": 1000,
                "help": "Output perflog period"
            }
        )
    )
)
