# 🗡️ jeminSMPv1

> Paper 1.21.11 기반 통합 SMP 플러그인  
> 기존 플러그인 10개를 하나로 합친 fat jar (~15 MB)

---

## 📦 빌드

```bash
mvn package -q
# → target/jeminSMPv1-1.0.jar
```

---

## 🧩 포함된 기능

| 기능 | 주요 명령어 |
|---|---|
| 🏠 홈 | `/sethome` `/home` `/delhome` `/homes` |
| ⚔️ 전투 태그 | 전투 중 로그아웃 즉사, `/home` 차단 |
| 📡 TPA | `/tpa` `/tpaccept` `/tpdeny` |
| 👥 팀 | `/team` `/tc` |
| 💬 닉네임 | `/nick <닉네임>` `/nick reset` |
| 🏅 칭호 | `/title list/buy/equip/remove/mytitles` |
| 🤖 자동 칭호 | 킬·플레이타임·발전과제 달성 시 자동 지급 |
| 💰 경제 | `/balance` `/eco give\|take\|set` |
| 🛒 상점 | `/shop` `/우편함` |
| 📊 킬 통계 | `/kills` `/top kills\|deaths\|kda\|streak` |
| 💤 수면 투표 | `/sleepvote yes\|no` (1일 1회) |
| 📍 웨이포인트 | `/wp add\|list\|tp\|del\|share` |
| 💤 AFK | 5분 무활동 → 탭에 `[AFK]` 표시 |
| 🔖 탭 목록 | `[Xms] [AFK] [칭호] 닉네임` 1초 갱신 |
| 📢 자동 공지 | 5분 주기 순환 방송 |
| 📈 플레이어 현황 | `/mystatus` |
| 🎒 스타터팩 | `/스타터팩` |
| 🌍 SMP 시작 | `/startSMP` |
| 🕐 운영 스케줄 | 시간대별 서버 오픈/마감 + 공허 대기열 |
| 🔗 Discord 봇 | 채팅 중계, 원격 명령어, 스케줄 설정 |

---

## ⚙️ 설정 (`plugins/jeminSMPv1/config.yml`)

```yaml
discord:
  enabled: true
  bot-token: "봇토큰"
  admin-role-id: "역할ID"
  command-prefix: "!"
  channels:
    events: "채널ID"   # 채팅 중계
    console: "채널ID"  # 콘솔 로그
  console-log-level: WARNING

schedule:
  enabled: false          # true = 운영 시간 외 대기열 활성화
  timezone: "Asia/Seoul"
  slots:
    # - days: "평일"
    #   start: "18:00"
    #   end: "23:00"

smp:
  world-name: smp_world
  scatter-radius: 2000

combat-tag-seconds: 10
afk:
  timeout-minutes: 5
```

---

## 💤 수면 투표

- 1명이라도 침대에 누우면 투표 자동 시작 (야간에만)
- Discord/게임 내 `[찬성]` `[반대]` 클릭 버튼
- 과반수 찬성 or 전원 투표 시 즉시 낮으로 전환
- `/sleepvote yes|no` (또는 찬성/반대)
- 30초 타임아웃
- 밤을 건너뛴 날은 하루 동안 재투표 불가 (1일 1회)

---

## 🤖 자동 칭호

### 킬 기반
| 킬 수 | 칭호 |
|---|---|
| 1 | 입문자 |
| 10 | 사냥꾼 |
| 30 | 전사 |
| 50 | 암살자 |
| 100 | 학살자 |

### 플레이타임 기반
| 시간 | 칭호 |
|---|---|
| 1h | 새싹 |
| 5h | 모험가 |
| 20h | 베테랑 |
| 50h | 전설 |

### 발전과제 기반
| 개수 | 칭호 |
|---|---|
| 10개 | 탐험가 |
| 25개 | 모험가 |
| 50개 | 탐구자 |
| 75개 | 개척자 |
| 100개 | 완성자 |

---

## 📈 `/mystatus`

```
⏱ 플레이타임: 2시간 30분  → 다음 칭호까지 2시간 30분
⚔ 킬: 15  데스: 3  KDA: 5.0  → 다음 킬 칭호까지 15킬
📜 발전과제: 18/100  → 다음 발전과제 칭호까지 7개
🏅 현재 칭호: [전사]
```

---

## 🕐 서버 운영 스케줄 & 대기열

### 동작 방식
1. `config.yml` 또는 디스코드에서 운영 슬롯 설정
2. 닫힌 시간에 접속하면 **공허 대기 월드**로 이동 (아무것도 없는 허공)
3. 대기 중: 인벤토리 보관, 관전 모드, 1분마다 오픈 시간 + 대기열 순서 표시
4. 서버 오픈 시: **대기열 순서대로** 2초 간격으로 원래 위치에 복귀

### 대기열 중 차단
- 다른 월드로 텔레포트 불가
- 모든 인게임 명령어 차단 (`/w`, `/msg` 채팅 제외)
- 낙하 피해 없음 / 허기 변화 없음

### 인게임 명령어
```
/schedule                  — 스케줄 현황 조회
/schedule on|off           — 활성/비활성 (OP)
/schedule add <요일> <시작>-<끝>
/schedule remove <번호>
```

---

## 🤖 Discord 봇 명령어

| 명령어 | 권한 | 설명 |
|---|---|---|
| `!help` | 모두 | 명령어 목록 |
| `!online` | 모두 | 접속 중인 플레이어 |
| `!title list` | 모두 | 칭호 목록 임베드 |
| `!title color` | 모두 | 색상코드 목록 |
| `!schedule` | 모두 | 스케줄 현황 |
| `!run <명령어>` | 관리자 | 원격 명령어 실행 |
| `!announce list\|add\|remove` | 관리자 | 공지 관리 |
| `!title add\|edit\|delete` | 관리자 | 칭호 관리 |
| `!kicka` | 관리자 | 전체 킥 (점검) |
| `!update <제목> \| <내용>` | 관리자 | 업데이트 공지 임베드 |
| `!schedule setting` | 관리자 | **스케줄 설정 임베드** (버튼 UI) |
| `!schedule add <요일> <시작>-<끝>` | 관리자 | 슬롯 직접 추가 |
| `!schedule remove <번호>` | 관리자 | 슬롯 삭제 |
| `!schedule on\|off` | 관리자 | 스케줄 활성/비활성 |
| `!opennow` | 관리자 | 강제 오픈 + 대기열 해제 |
| `!closenow` | 관리자 | 강제 닫기 + 대기열 전환 |

### `!schedule setting` 버튼 UI
```
[✅ 스케줄 ON]  [❌ 스케줄 OFF]
[🟢 강제 오픈]  [🔴 강제 닫기]  [↩ 강제 해제]
[➕ 슬롯 추가 → 모달 팝업 → 요일 선택 버튼]  [🔄 새로고침]
[🗑 1. 평일 18:00~23:00]  [🗑 2. 주말 14:00~23:00]
```

---

## 📁 데이터 파일 (`plugins/jeminSMPv1/`)

```
config.yml          — 전체 설정 (스케줄 포함)
smp_state.yml       — SMP 진행 상태
homes.yml           — 홈
kills.yml           — 킬/데스/스트릭
nicks.yml           — 닉네임
titles.yml          — 칭호 정의
players.yml         — 플레이어별 칭호
waypoints.yml       — 웨이포인트
starterpack.yml     — 스타터팩 수령 기록
economy.yml         — 잔액
waiting_queue.yml   — 대기열 인벤토리 백업 (서버 재시작 대비)
```

---

## 🔧 권장 서버 설정

**`config/paper-world-defaults.yml`** — 엑스레이 차단
```yaml
anticheat:
  anti-xray:
    enabled: true
    engine-mode: 2
```

**`server.properties`**
```properties
reduced-debug-info=false
```
