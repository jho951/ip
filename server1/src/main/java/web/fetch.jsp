<%@ page contentType="text/html; charset=UTF-8" %>
<!doctype html>
<html lang="ko">
<head>
    <meta charset="UTF-8" />
    <title>파일 가져와 저장하기</title>
    <style>
        body { font: 14px/1.5 system-ui, sans-serif; padding: 24px; }
        label { display:block; margin: 10px 0 4px; }
        input[type=text]{ width: 520px; padding: 8px; }
        button { padding: 8px 14px; }
        .row { margin-bottom: 12px; }
        .ok { color: #0a7c2f; }
        .err{ color: #b00020; }
        .hint{ color:#666; font-size:12px; }
    </style>
</head>
<body>
<h2>서버2에서 파일 받아 서버1에 저장</h2>

<!-- (1) 서버2 파일 URL -->
<div class="row">
    <label for="fromUrl">서버2 파일 URL (Servlet 경로)</label>
    <input id="fromUrl" type="text"
           value="http://localhost:8082/server2-provider/files"
           placeholder="http://서버2:포트/컨텍스트/files" />
    <div class="hint">예: http://localhost:8082/server2-provider/files</div>
</div>

<!-- (2) 서버2에 존재하는 원본 파일명 -->
<div class="row">
    <label for="sourceName">서버2의 원본 파일명</label>
    <input id="sourceName" type="text" placeholder="예: report.txt" />
    <div class="hint">서버2의 /files?name=... 에서 내려줄 실제 파일명</div>
</div>

<!-- (3) 서버1에 저장할 '파일 제목'(저장 파일명) -->
<div class="row">
    <label for="saveTitle">저장할 파일 제목(서버1 파일명)</label>
    <input id="saveTitle" type="text" placeholder="예: 보고서_2025-10-29.txt" />
    <div class="hint">확장자 포함해서 원하는 이름으로 저장됩니다</div>
</div>

<!-- 저장 버튼 -->
<div class="row">
    <button id="btnSave">통신을 통한 저장</button>
</div>

<!-- 결과 표시 -->
<div id="result"></div>

<script>
    const el = (id) => document.getElementById(id);
    const resultBox = el('result');

    el('btnSave').addEventListener('click', async () => {
        const fromUrl    = el('fromUrl').value.trim();
        const sourceName = el('sourceName').value.trim();
        const saveTitle  = el('saveTitle').value.trim();

        if (!fromUrl || !sourceName || !saveTitle) {
            resultBox.innerHTML = '<p class="err">모든 입력값을 채워주세요.</p>';
            return;
        }

        try {
            const form = new URLSearchParams();
            form.set('fromUrl', fromUrl);
            form.set('sourceName', sourceName);
            form.set('saveTitle', saveTitle);

            const res = await fetch('fetch-json', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: form.toString()
            });

            const data = await res.json();
            if (data.ok) {
                resultBox.innerHTML =
                    `<p class="ok">저장 성공!</p>
             <ul>
               <li>저장 경로: ${data.path}</li>
               <li>바이트 수: ${data.bytes}</li>
             </ul>`;
            } else {
                resultBox.innerHTML = `<p class="err">실패: ${data.message || '알 수 없는 오류'}</p>`;
            }
        } catch (e) {
            resultBox.innerHTML = `<p class="err">요청 실패: ${e.message}</p>`;
        }
    });
</script>
</body>
</html>
