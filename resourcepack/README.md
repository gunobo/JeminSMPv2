# JeminSMPv2 리소스팩

## 구조
```
resourcepack/
  pack.mcmeta                                          — 팩 메타데이터
  assets/minecraft/
    models/item/
      paper.json                — 바닐라 종이 모델에 override 추가 (건드릴 필요 없음)
      job_change_scroll.json    — 전직의 서 전용 모델 (건드릴 필요 없음)
    textures/item/
      job_change_scroll.png     — ⭐ 여기가 실제로 새로 그려야 할 파일 (지금은 임시 placeholder)
```

## 지금 만들어야 할 것
- **`assets/minecraft/textures/item/job_change_scroll.png`**
  - 크기: **16×16px** (마인크래프트 아이템 아이콘 표준 크기)
  - 배경: 투명 (PNG 알파 채널)
  - 지금 들어있는 건 보라색 두루마리 모양 placeholder라 바로 테스트는 되지만, 원하는 그림으로 덮어쓰면 됩니다.

새 아이템을 더 추가하고 싶으면(예: 스킬북 등) 같은 패턴으로:
1. `models/item/<베이스아이템>.json`에 override 추가 (custom_model_data 번호는 겹치지 않게)
2. `models/item/<새모델이름>.json` 생성
3. `textures/item/<새텍스처>.png` 그리기
4. 자바 코드(`JobItems.java`)에 `setCustomModelData(번호)` 맞춰서 추가

## 패키징
```bash
cd resourcepack
zip -r ../jeminsmp-resourcepack.zip .
```

## 서버 적용 방법 (둘 중 하나)
1. **자동 강제 적용** — 어딘가(GitHub Releases, 개인 웹서버 등)에 zip을 올리고 URL을 받은 다음, `server.properties`의 `resource-pack`/`resource-pack-sha1`에 설정 (SHA1은 `shasum jeminsmp-resourcepack.zip`으로 구함)
2. **플러그인에서 코드로 적용** — 플레이어 접속 시 `Player#setResourcePack(url, hash)` 호출. 더 세밀한 제어(필수/선택, 안내 메시지) 가능. 팩을 실제로 호스팅할 URL이 생기면 이 방식으로 붙여드릴 수 있음.

pack.mcmeta의 `pack_format` 값(현재 34)은 실제 서버 버전에 안 맞으면 마인크래프트가 "호환 안 될 수도 있음" 경고만 뜨고 그냥 작동은 합니다. 정확한 값은 접속해서 경고 뜨는지 보고 조정하면 됩니다.
