import time
import json
from kafka import KafkaProducer

PRODUCER = KafkaProducer(
    bootstrap_servers="localhost:9092",
    acks=1,
    retries=3,
    value_serializer=str.encode
)

def main():
    try:
        for behavior in ("注册成功", "完善资料", "浏览商品", "提交订单"):
            for i in range(0, 1000000):
                _upload_event(str(i), behavior)
    finally:
        PRODUCER.close()
    
def _upload_event(user_id, behavior):
    event = {
        "type": "user_behavior",
        "attr": {
            "user_id": user_id,
            "behavior": behavior
        },
        "time": int(time.time() * 1000)
    }
    PRODUCER.send("playwell", key=None, value=json.dumps(event))

if __name__ == "__main__":
    main()
