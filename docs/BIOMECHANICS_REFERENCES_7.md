# 신규 5종 운동 · 논문 → 관찰 지표 매핑

검토: 2026-11-12. 아래는 논문의 실험 사실과 앱의 관찰 설계를 구분한 자료다. 논문 표본·도구·운동 조건이 이 앱과 다르므로 연구 수치를 앱의 정상/안전 경계로 옮기지 않는다. 원문/공식 초록에서 확인하지 못한 값은 미확인으로 표시한다.

`PAPER_DIRECT` = 아래 논문의 조건/결과 자체. `PAPER_SUPPORTIVE` = 그 조건을 참고한 앱 지표 선택. `ENGINEERING_INITIAL` = 실제 앱의 모든 신규 threshold/가중치/상태 전이. 신규 지표가 논문의 장비 측정을 재현한다는 뜻이 아니다.

| 운동·출처 | 대상·조건 (`PAPER_DIRECT`) | 측정 도구/관찰 | 앱에 참고한 항목 (`PAPER_SUPPORTIVE`) | 적용 한계 |
|---|---|---|---|---|
| 컬 · [Oliveira LF 등, 2009](https://pmc.ncbi.nlm.nih.gov/articles/PMC3737788/), PMID 24150552 | 훈련 경력 ≥1년 남성 22명, standing/incline/preacher 덤벨 컬, 40% MVC 부하, 1–2주기 | 표면 EMG, 손목 단축 가속도계, MVC load cell | 팔꿈치 ROM, 굽힘/폄 구간, 상완 위치 조건을 분리 | 앱은 근활성/MVC를 측정하지 않는다. 70°/150° 기준은 이 논문이 정한 값이 아니다. |
| 컬 · [Oliveira AS·Gonçalves, 2009](https://pubmed.ncbi.nlm.nih.gov/19387393/), DOI 10.1519/JSC.0b013e3181a00c25 | 건강인 20명, standing/adapted standing/seated preacher, 25–40% 1RM, 1분 20회 | EMG·심박·주관적 운동 강도 | 자세 조건에 따라 결과가 달라짐; 다른 자세를 무조건 같은 정상 범위로 취급하지 않음 | 2D 관절로 피로도/심박/EMG를 추정하지 않음 |
| 푸시업 · [An 등, 1990](https://pubmed.ncbi.nlm.nih.gov/2334780/) | 손바닥 위치·팔의 면·발 위치·속도 변화, 초록상 표본수 미확인 | 압전 force platform·전자기 추적, 관절 하중 | 팔꿈치 변화와 촬영 조건을 구분 | 카메라로 관절 힘/하중을 산출하지 않음 |
| 푸시업 · [Lou 등, 2001](https://pubmed.ncbi.nlm.nih.gov/11390048/), DOI 10.1016/S0268-0033(01)00008-0 | 전완 중립/90° 내회전/90° 외회전, 초록상 표본수·정확한 장비 미확인 | 운동학·운동역학 분석 | 팔/손 자세가 결과에 영향을 준다는 한계 안내 | 전완 회전·관절 하중을 이 앱의 2D 팔꿈치 각도로 대체하지 않음 |
| 푸시업 · [Suprak 등, 2013](https://pmc.ncbi.nlm.nih.gov/articles/PMC3867095/), DOI 10.4085/1062-6050-48.5.08 | 건강인 16명, self-selected/몸통 옆/어깨 90° 조건 | Fastrak 3Space·견갑 추적 장치, 구심성 단계의 3D 견갑 운동 | 어깨 조건과 단계 구분의 필요성 | 앱은 견갑 후방경사·상방회전·외회전을 측정하지 않음 |
| 푸시업 · [Freeman 등, 2006](https://pubmed.ncbi.nlm.nih.gov/16540847/), DOI 10.1249/01.mss.0000189317.08635.1b | 대학 연령의 체력 좋은 10명(남9/여1), 최대12변형, 각3trial | 표면 EMG·해부학적 척추 모델, L4–5 압박/토크 | 측면 몸통 선 변화의 보조적 관찰 | 앱 정렬 지표는 척추 하중·손상 위험 계산이 아님 |
| 로우 · [Fenwick·Brown·McGill, 2009](https://pubmed.ncbi.nlm.nih.gov/19197209/), DOI 10.1519/JSC.0b013e3181942019 | 건강한 대학 남성7명, 3종 로우 비교 | EMG·전자기 척추 위치 센서·비디오 관절 모멘트 | 몸통 위치/변화와 팔 당김을 분리 관찰 | 비교 조건 내 하중 차이를 사용자 위험도/척추 하중으로 환산하지 않음 |
| 로우 · [Kanno 등, 2025](https://www.jstage.jst.go.jp/article/jscicoachingtraining/8/1/8_23/_article/-char/en), DOI 10.32171/jscicoachingtraining.8.1_23 | 대학 럭비 남성9명, 몸통40°/65° 조건 | 4근육 표면 EMG, 개인차 관찰 | 몸통 각도 조건을 별도 기록 | 40°=오류/65°=정답이 아님; 앱 투영각이 실험각과 같지 않음 |
| 익스텐션 · [Maeo 등, 2023](https://www.tandfonline.com/doi/abs/10.1080/17461391.2022.2100279), PMID 35819335 | 젊은21명(남14/여7), 12주, 한 팔 **케이블**, 어깨180°/0°, 팔꿈치 굴곡90→0°, 70%1RM 10회×5세트 주2회 | MRI 근육 부피·1RM | overhead 상완 위치와 팔꿈치 움직임 분리 | 덤벨/개인 정상 ROM의 직접 근거가 아님; 근비대 예측 불가 |
| 익스텐션 · [Alves 등, 2018](https://pubmed.ncbi.nlm.nih.gov/28677940/), DOI 10.23736/S0022-4707.17.06849-9 | 저항운동 남성21명, overhead/lying 덤벨, 40%MVC 1–2회 | 삼두 장두/외측두 EMG, ROM 3구간 | 어깨 조건·운동 단계에 따른 관찰 분리 | 근활성/특정 근두 사용을 MediaPipe로 판단하지 않음 |
| 사이드 벤드 · [Konrad 등, 2001](https://pubmed.ncbi.nlm.nih.gov/12937449/), PMCID PMC155519 | 숙련 건강인10명, 12종 체조 각9회 중 2종 측면 운동 | 표면 EMG·전자 각도계 | 좌우 측굴 방향과 ROM 분리 | standing 덤벨과 동일 프로토콜 아님; 외복사근 활성을 카메라로 산출하지 않음 |
| 사이드 벤드 · [Huang·Andersson·Thorstensson, 2001](https://pubmed.ncbi.nlm.nih.gov/11458152/), DOI 10.1097/00007632-200107010-00017 | 건강인6명, 정적0/15/30°·한 손20kg 유무 | 초음파 유도 fine-wire EMG·S1–C7 수직각 각도기 | 좌우/부하/위치에 따라 측굴 관찰이 달라짐 | 앱 어깨–골반 중점은 S1–C7이 아님; 15°·20kg을 운동 처방하지 않음 |

앱 적용 수치/수식/코드 경계는 [7종 확장 문서](EXERCISE_EXPANSION_7.md)와 `ExerciseMotionPolicy`를 참조한다. 논문으로 정상/비정상, 안전 보장, 의료 진단, 전문가 인증을 주장하지 않는다. 기존 스쿼트/프레스의 과거 조사 기록은 [기존 매핑](BIOMECHANICS_REFERENCES.md)에 보존한다.
