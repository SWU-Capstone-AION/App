# 규칙모델 v3 (monitor/rule)

포즈 33개 관절 + 손 21개 관절(양손)로 상동행동 17가지를 감지하고, 학습모델과 소프트 보팅(가중 평균)한다.
감지 기준은 「AION 상동행동 감지 기준서 (규칙모델 v3)」 문서와 같고, HTML 대시보드
`1024_aion_monitor_v3_33pose_hands.html` 의 CORE 와 같은 식이다.

## 파일

| 파일 | 하는 일 | 안드로이드 의존 |
| --- | --- | --- |
| `RuleConfig` (RuleBehaviors.kt) | 설정값, 행동 17가지 정의 | 없음 |
| `RuleMath.kt` | 계산 도구 (규칙성, 보간 등) | 없음 |
| `RuleModelV3.kt` | 프레임 → 행동별 점수, 0.5초마다 최근 2초 근거 r | 없음 |
| `SoftVoting.kt` | 확률 보정 → 가중 평균 → 4초 지속 → 단계·알림 | 없음 |
| `TouchTracker.kt` | 화면 터치 기록 → 기능적 행동 f | 없음 |
| `RuleV3Android.kt` | MediaPipe 결과 변환, `assets/ensemble_config.json` 읽기, Logcat | 있음 |

연결된 곳
- `PoseLandmarkerHelper` — `enableHands = true` 면 손 모델도 같은 프레임에 돌림
- `StereotypyDetectionHost` — 기존 규칙모델·학습모델과 함께 v3 + 소프트 보팅 실행, `StereotypySignal.ruleV3 / ensemble` 에 결과
- `MainActivity.dispatchTouchEvent` — 터치 시각을 `TouchTracker` 로

## 준비

1. 손 모델 파일을 받아 `app/src/main/assets/hand_landmarker.task` 에 넣는다 (약 7.8MB).
   https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
   없으면 손 모델 없이 돌아간다 (손가락 행동은 '인식 약함', 손 위치는 포즈의 손 관절 3개로 대신).
2. (선택) 검증 데이터로 정한 보정값을 `app/src/main/assets/ensemble_config.json` 에 둔다.

```json
{"w": 0.6, "decision": 0.45, "touch_penalty": 0.8, "alert_mode": "immediate",
 "rule_calibration": {"type": "logistic", "bias": -2.1, "coef": {"r": 0.9, "reg": 0.6, "amp": 0.3}},
 "dl_calibration": {"type": "temperature", "T": 1.4}}
```

## 켜고 끄기

- 지금은 **값만 계산**한다. 아동 화면 알람은 기존 `StereotypyDetector` 그대로.
- v3 결과로 알람을 정하려면 `RuleModelV3.USE_FOR_ALARM = true`.
- 확인: Logcat 에서 `RuleV3` 검색 → 0.5초마다 `r`, 앙상블 `p`, 지속 `D`, 단계, 터치 `f`, 상위 3개 행동.

## 시험

```
./gradlew :app:testDebugUnitTest --tests "*RuleModelV3Test*"
```
가짜 사람으로 9가지 행동 + 정지 + 터치 감소 + 4초 위험 판정을 확인한다 (12개).

## 아직 안 된 것

- 기준값은 실제 아동 영상으로 맞추지 않은 첫 값이다.
- 규칙모델 확률은 보정 전이라 설계 점수다 (`ensemble_config.json` 의 `rule_calibration` 필요).
- 서버 전송(`DetectionSender`)은 아직 기존 규칙모델 점수를 보낸다.
- 개발용 모니터링 화면(`StereotypyMonitorScreen`) 대시보드에는 v3 표시가 없다.
