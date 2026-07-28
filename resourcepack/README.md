# JeminSMPv2 리소스팩

## 구조
```
resourcepack/
  pack.mcmeta                                          — 팩 메타데이터
  assets/minecraft/
    models/item/
      paper.json                 — 바닐라 종이 모델 override (건드릴 필요 없음)
      stick.json                 — 바닐라 막대기 모델 override (건드릴 필요 없음)
      job_change_scroll.json     — 전직의 서 전용 모델 (건드릴 필요 없음)
      skill_<직업>_<스킬>.json    — 스킬템 전용 모델, 16개 (건드릴 필요 없음)
    textures/item/
      job_change_scroll.png      — ⭐ 전직의 서 아이콘
      skill_<직업>_<스킬>.png     — ⭐ 스킬템 아이콘, 16개
```

## 왜 16장이냐면
스킬템은 **직업 × 스킬종류** 조합마다 완전히 다른 그림입니다 (같은 "딜"이어도 광부의 딜과 전사의 딜은 다른 그림). 4직업 × 4스킬 = 16장.

## 지금 만들어야 할 파일 (전부 임시 placeholder가 이미 들어있음)

| 파일명 | 직업 | 스킬 |
|---|---|---|
| `skill_miner_heal.png` | 광부 | 힐 |
| `skill_miner_deal.png` | 광부 | 딜 |
| `skill_miner_force.png` | 광부 | 포스 |
| `skill_miner_move.png` | 광부 | 이동기 |
| `skill_farmer_heal.png` | 농부 | 힐 |
| `skill_farmer_deal.png` | 농부 | 딜 |
| `skill_farmer_force.png` | 농부 | 포스 |
| `skill_farmer_move.png` | 농부 | 이동기 |
| `skill_warrior_heal.png` | 전사 | 힐 |
| `skill_warrior_deal.png` | 전사 | 딜 |
| `skill_warrior_force.png` | 전사 | 포스 |
| `skill_warrior_move.png` | 전사 | 이동기 |
| `skill_fisher_heal.png` | 어부 | 힐 |
| `skill_fisher_deal.png` | 어부 | 딜 |
| `skill_fisher_force.png` | 어부 | 포스 |
| `skill_fisher_move.png` | 어부 | 이동기 |

전부 `textures/item/` 안에 있고, 지금 들어있는 임시 그림은 직업별 배경색(광부=갈색, 농부=초록, 전사=빨강, 어부=파랑) + 스킬별 기호(힐=십자가, 딜=대각선 검, 포스=점4개, 이동기=화살표) 조합입니다. 실제 그림 그릴 때 이 조합 감(직업 배경 + 스킬 기호)을 참고하셔도 되고 완전히 새로 디자인하셔도 됩니다.

- 크기: 전부 **16×16px**
- 배경: 투명 (PNG 알파 채널)
- **파일명은 절대 바꾸지 마세요** — 위 표의 정확한 이름으로 덮어쓰기만 하면 됩니다 (`models/item/skill_*.json`이 이 이름을 그대로 참조하고 있음)

스킬템은 하나의 아이템(막대기 기반)이 우클릭할 때마다 해금된 스킬 종류를 순서대로 전환하고, 전환될 때마다 "현재 직업 + 현재 스킬" 조합에 맞는 텍스처로 아이콘이 바뀝니다. 전직하면 같은 스킬 종류라도 새 직업의 아이콘으로 자동 갱신됩니다.

새 아이템을 더 추가하고 싶으면 같은 패턴으로:
1. `models/item/<베이스아이템>.json`에 override 추가 (custom_model_data 번호는 겹치지 않게, 지금 1001=전직서, 2011~2044=스킬템 사용 중)
2. `models/item/<새모델이름>.json` 생성
3. `textures/item/<새텍스처>.png` 그리기
4. 자바 코드(`JobItems.java`)에 `setCustomModelData(번호)` 맞춰서 추가

## 패키징 & 배포 (Pi에서, 레포 클론된 경로)
```bash
cd resourcepack
zip -r ../resourcepack-dist/jeminsmp-resourcepack.zip .
cd ..
docker compose --env-file .env up -d resourcepack
```
`resourcepack-dist/`를 nginx 컨테이너가 정적으로 서빙합니다 (본서버 compose에서만 띄우면 됨 — 테스트 서버도 같은 URL 씀). 접속 주소는 `http://mcv2.imjemin.co.kr:8080/jeminsmp-resourcepack.zip` (포트는 `.env`의 `RESOURCEPACK_PORT`로 바꿀 수 있음, 기본 8080). 라우터에서 8080 포트포워딩도 해줘야 외부에서 접근 가능합니다.

## 서버 적용
`config.yml`의 `resourcepack.url`에 위 URL을 넣으면 플레이어 접속 시 `PlayerJoinEvent`에서 자동으로 `Player#setResourcePack(url)`을 호출해 적용합니다 (`ResourcePackListener.java`). **이미 떠 있는 서버는 `config.yml`이 이미 생성돼 있어서 새 기본값이 자동으로 안 들어갑니다** — `data/plugins/JeminSMPv2/config.yml`과 `data-test/plugins/JeminSMPv2/config.yml`에 아래 내용을 직접 추가해야 합니다:
```yaml
resourcepack:
  url: "http://mcv2.imjemin.co.kr:8080/jeminsmp-resourcepack.zip"
```
텍스처를 새로 그려서 zip을 다시 만들 때마다 위 패키징 명령만 다시 실행하면 됩니다 (URL은 그대로, 파일만 갱신).

pack.mcmeta의 `pack_format` 값(현재 34)은 실제 서버 버전에 안 맞으면 마인크래프트가 "호환 안 될 수도 있음" 경고만 뜨고 그냥 작동은 합니다. 정확한 값은 접속해서 경고 뜨는지 보고 조정하면 됩니다.
