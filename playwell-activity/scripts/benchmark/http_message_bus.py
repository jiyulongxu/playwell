import time
import requests

MESSAGE_BUS = "http://127.0.0.1:1923/input"

def main():
    for behavior in ("注册成功", "完善资料", "浏览商品"):
        events = []
        for i in range(0, 1000000):
            events.append({
                "type": "user_behavior",
                "attr": {
                    "user_id": str(i),
                    "behavior": behavior
                },
                "time": int(time.time() * 1000)
            })
            if i % 50000 == 0:
                _upload_events(events)
                events = []
                time.sleep(1)
        else:
            if events:
                _upload_events(events)
                time.sleep(1)


def _upload_events(events):
    rs = requests.post(MESSAGE_BUS, json=events, headers={'Connection': 'close'})
    print(rs.text)

def _upload_event(user_id, behavior):
    event = {
        "type": "user_behavior",
        "attr": {
            "user_id": user_id,
            "behavior": behavior
        },
        "time": int(time.time() * 1000)
    }
    rs = requests.post(MESSAGE_BUS, json=event,  headers={'Connection': 'close'})
    print(rs.text)

main()
