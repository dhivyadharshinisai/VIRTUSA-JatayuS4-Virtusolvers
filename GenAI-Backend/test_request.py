import requests

url = "http://localhost:5001/predict"
data = {
    "userId": "67f53",
    "userName": "Kavi",
    "query": "i hate vegetables",
    "timeSpent": "10m 18s",
    "dateAndTime": "2025-07-13T16:23:44.000Z"
}
response = requests.post(url, json=data)
print("Server Response:")
print(response.json())
