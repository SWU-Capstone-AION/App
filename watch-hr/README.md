# watch-hr — 갤럭시 워치 심박 전송·보관 (실습)

갤럭시 워치에서 잰 심박수를 태블릿(아동앱)으로 보내고, 태블릿에 안전하게 보관한 뒤
개인 기준선 대비 0~1 위험도를 계산하는 실습 프로젝트입니다.
AION 앱(`app/`)과는 **별도의 Gradle 프로젝트**라 기존 빌드에 영향을 주지 않습니다.

| 모듈 | 기기 | 하는 일 |
|---|---|---|
| `wear/` | 갤럭시 워치 (Wear OS) | 포그라운드 서비스로 심박 측정 → 페어링된 기기로 전송 |
| `mobile/` | 태블릿/폰 | 수신 → 최신값·버퍼·Room DB 보관 → 기준선 M, S → 위험도 → 비전 AI와 통합 판정 |

## 열기
Android Studio → File → Open → 이 `watch-hr` 폴더 선택. `mobile`은 태블릿/폰에, `wear`는 워치에 실행합니다.

## 동작 조건
- 워치와 받는 기기가 **Galaxy Wearable 앱으로 페어링**돼 있어야 합니다 (블루투스 연결만으로는 안 됨).
- 두 모듈의 `applicationId`가 같아야 합니다 (`com.aion.hrtest`). 같은 PC에서 디버그 빌드하면 서명도 같아집니다.

## 주요 파일
**wear**
- `HeartRateService.kt` — 포그라운드 서비스. 화면이 꺼져도 측정 유지
- `HealthServicesManager.kt` — 운동 세션(ExerciseClient)으로 심박 측정, 화면 꺼짐 중 5초 배치
- `HeartRateSender.kt` — MessageClient로 `/aion/hr` 경로에 `{"bpm","at"}` 전송

**mobile**
- `HeartRateListenerService.kt` — 백그라운드 수신 (앱 화면이 꺼져 있어도 받음)
- `HrRepository.kt` — 최신값·버퍼·DB 동시 기록, 재시작 시 복원
- `HrDatabase.kt` — Room DB (7일 보관)
- `HeartRateBaseline.kt` — 기준선(최근 10분 창), 이상치 필터, 조용한 구간 가드(90초 보류·종료 후 120초 제외)
- `Fusion.kt` — Case A(위험) / Case B(유예) / 비전 단독 판정
- `MainActivity.kt`, `DebugInjectReceiver.kt` — 테스트용 화면과 adb 입력 (AION 앱에 옮길 때는 제외)

## 공식
```
M = 기준선 중앙값,  S = max(MAD × 1.4826, 3)
위험도 = clamp((현재 심박 − M) ÷ (2 × S), 0, 1)
초록 < 0.3 ≤ 노랑 < 0.7 ≤ 빨강   (0.7 이상 + 비전 감지 = Case A)
```

## 검증
- 단위 테스트 17개: `./gradlew :mobile:testDebugUnitTest`
- 실기기(갤럭시 워치7 → 갤럭시 S25 Ultra): 화면 켬 3분 187건, 화면 끔 3분 117건 모두 손실 0
- 앱 강제 종료 후 DB에서 버퍼·기준선 복원 확인

## AION 앱에 합칠 때
- `mobile`의 테스트용 파일을 뺀 5개 파일을 `app/`으로 옮기고, Room·play-services-wearable 의존성을 추가합니다.
- 워치 앱의 `applicationId`를 AION 앱(`com.example.aion_app`)과 맞춰야 통신됩니다.
- 비전 AI의 상동행동 감지 상태 → `behaviorActive`, 1차 위험 신호 → `onVisionAlert()` 를 연결합니다.
- 워치가 갤럭시 탭과 페어링되지 않으면 교사폰 중계 등 다른 전송 경로가 필요합니다.
