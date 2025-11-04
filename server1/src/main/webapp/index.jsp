<%@ page contentType="text/html; charset=UTF-8" %>
<!doctype html>
<html>
<head><meta charset="utf-8"><title>Server1</title></head>
<body>
<h1>Server1 OK</h1>
<form method="post" action="/send">
    <label>저장 폴더명: <input name="dir"></label><br>
    <label>파일 이름: <input name="name"></label><br>
    <button type="submit">서버2에 요청 → 저장</button>
</form>
</body>
</html>
