<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!doctype html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Transfer</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            height: 100%;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
        }
        h1{
            font-size: 30px;
            padding: 25px 0;
            color: #292998;
        }

        form {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 20px;
            color: #292998;
        }

        label {
            display: flex;
            flex-direction: column;
            font-size: 14px;
            font-weight: bold;
            gap:8px;
        }
        input{
            min-width: 250px;
            padding: 10px;
            background-color: #d8dde6;
            border: 1px solid transparent;
            border-radius: 10px;
        }
        button{
            width: 150px;
            padding: 10px 5px;
            border: 1px solid transparent;
            border-radius: 20px;
            background-color: #41a9da;
            color: #ffffff;
            font-size: 16px;
            cursor: pointer;
        }
        pre{
            padding: 20px 0;
            font-size: 16px;
        }
    </style>
</head>
<body>

<h1>서버간 파일 전송</h1>

<form method="post" action="/transfer">
    <label>폴더명
        <input name="folderName" value="${defaultFolder}" placeholder="저장할 폴더 명"/>
    </label>
    <label>파일명
        <input name="fileName" value="${defaultFilename}" placeholder="저장할 파일 명"/>
    </label>
    <button type="submit">서버2 조회</button>
</form>

<pre>${message}</pre>
</body>
</html>