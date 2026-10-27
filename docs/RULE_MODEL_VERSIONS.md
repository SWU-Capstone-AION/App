# 상동행동 규칙모델 버전 기록

앱 안의 버전 번호는 `RuleV3.MODEL_VERSION`(RuleV3Config.kt)에 있다.
모니터링 화면 맨 위(`AION · 규칙모델 v3.x`)와 기록 CSV의 `model_version` 칸에 찍힌다.

- 작은 수정 → `3.n`의 n을 올린다
- 판정 방식이 크게 바뀌면 → `4.0`
- 파일·클래스 이름(`RuleV3Engine.kt` 등)은 버전마다 바꾸지 않는다. 이름을 바꾸면 이를 쓰는 모든 코드를 같이 고쳐야 하고, 변경 기록(git)을 따라가기 어려워진다.

| 버전 | 병합 | 바뀐 점 |
|---|---|---|
| 3.3 | #61, #62 | HTML(1024_aion_monitor_v3) Kotlin 변환 — 포즈 33 + 손 21×2, 상동행동 17가지 · 위험 민감도 단계(기본 민감) · 판정 기록 CSV + w·τ 조정 도구 · 위험 순간 1.0 즉시 전송, 부위(part) 정확도 |
| 3.4 | | 서버로 행동 유형(behaviorType) 함께 보냄 — 서버 리포트 '가장 많이 감지된 행동'이 살아남 · 부위(part) 확장: left_hand / right_hand / both_hands / shoulder 추가 · 리포트 행동 이름 19가지 |

## 서버로 보내는 값 (v3.4)

| 칸 | 값 |
|---|---|
| score | 0~1, 1.0 = 앱 위험 순간 |
| part | left_arm / right_arm / left_hand / right_hand / both_hands / head / shoulder / body |
| behaviorType | 아래 표의 코드. 모델이 추정한 행동이라 비슷한 행동끼리는 틀릴 수 있다 |

| behaviorType | 리포트 이름 | part |
|---|---|---|
| body_rocking | 몸 앞뒤 흔들기 | body |
| body_swaying | 몸 좌우 흔들기 | body |
| back_arching | 등 젖히기 | body |
| jumping | 점프 | body |
| spinning | 회전 | body |
| head_rolling | 머리 굴리기 | head |
| head_nodding | 고개 끄덕이기 | head |
| head_tilting | 고개 갸웃거리기 | head |
| head_turning | 고개 돌리기 | head |
| shoulder_shrugging | 어깨 으쓱하기 | shoulder |
| arm_flapping | 팔 퍼덕이기 | left_arm / right_arm |
| arm_waving | 손·팔 흔들기 | left_arm / right_arm |
| self_hitting | 자기 때리기 | left_arm / right_arm |
| finger_flicking | 손가락 꼼지락 | left_hand / right_hand |
| finger_fluttering | 얼굴 앞 손가락 팔랑이기 | left_hand / right_hand |
| eye_covering | 눈 가리기 | left_hand / right_hand |
| ear_covering | 귀 막기 | left_hand / right_hand |
| eye_rubbing | 눈 비비기 | left_hand / right_hand |
| clapping | 박수 | both_hands |
