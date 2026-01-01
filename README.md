프로젝트 이름: ArPtApp(3D AR 기반 포즈 추정 모델을 활용한 피트니스 자세 교정 앱 프로젝트)

프로젝트 설명: AI Pose Estimation 기술을 활용하여 실시간으로 운동 자세를 교정해주는 지능형 홈 트레이닝 솔루션입니다.

기획 배경: 홈 트레이닝 환경을 넘어 헬스장에서도 사용자가 자립적으로 올바른 운동을 수행 할 수 있도록 돕는데 초점을 맞췄고 현대 헬스장 이용객들은 다음과 같은 실질적 어려움에 직면해 있습니다.

1. 초보자의 기구 사용 및 자세 숙지 어려움
2. PT의 심리적, 경제적 진입장벽
3. 타 스마트폰 앱들의 활용 한계
4. 운동 기록에 대한 데이터 관리의 부재

기획 목적: 복잡한 헬스장 환경 내에서도 스마트폰 하나만으로 전문가의 도움 없이 정확하게 안전하게 운도할 수 있는 '실시간 지능형 운동 가이드 시스템' 구축을 최종 목적으로 하며, 세부적인 목표는 다음과 같습니다.

1. 비대면 실시간 자세 교정 시스템 구현
2. 온 디바이스 AI 기반의 최적화된 사용자 경험 제공
3. 기구 인식 및 자동 맞춤 가이드 제공
4. 데이터 기반의 정량적 운동 관리

실시간 구동과 모바일 최적화를 위해 객체 탐지 및 포즈 추정 분야의 주요 무델들을 비교 분석하였습니다.

비교 항목	    | OpenPose |	                 | MediaPipe(BlazePose) | 	                | YOLOv11 Pose |
처리 방식	 | Bottom-up(정밀도 중심) |	          | Top-down(속도 중심) |	            | Single-stage(최신 최적화) |
지원 관절 수  | 18 ~ 25개(2D 중심) |	            | 33개(3D 좌표 지원) |	                  | 17개(COCO기준) |
모바일 성능	        | 낮음 |	                        | 매우 높음 |	                           | 높음 |
장점	     | 다중 인원 인식에 강함 |	            | 3D Vector 추출 가능 |	              | 검출과 포즈 추정 동시 수행 |
한계점	    | 모바일 실시간 구동 불가 |	             | 1인 추적에 최적화됨 |	           | V11 기반의 최신 런타임 필요 |

-> 최종적으로 MediaPipe를 선택하게 되었고 그 이유는 다음과 같습니다.
단일 사용자의 전식을 정밀하게 분석해야 하므로, 33개의 관절 랜드마크를 제공하고 깊이(Z축) 정보를 포함한 3D좌표를 실시간으로 추출할 수 있는 MediaPipe를 메인 포즈 추정 엔진으로 채택하였습니다.


핵심 기능
1. AI 자세 분석: MediaPipe/YOLO를 이용한 실시간 모션 트래킹
2. 다정한 UX: 사용자 친화적인 피드백 메시지 제공
3. 운동 기록 관리: 데이터 기반의 체계적인 신체 변화 추적

-----------------------------------------------------------------------------------------------------------------------------------------------------

ArPtApp: AI Motion Analysis PT Solution
Current Status: Milestone 2 - Camera Infrastructure Completed

🛠 기술 스택
Language: Kotlin 1.9.x

UI Framework: XML (View-based) with ViewBinding

Architecture: Android Jetpack (Lifecycle, Intent, Activity Result API)

Hardware Interface: CameraX (Core, Lifecycle, View)

🚀 주요 개발 성과
1. UI/UX 디자인 시스템 구축
Branding: Deep Matte Dark 테마와 Action Blue 포인트를 통한 고대비 UI 설계로 시인성 확보.

Components: 12dp의 라운드 코너가 적용된 입력 필드 및 버튼 디자인 시스템화.

2. 사용자 인증 및 화면 전환 아키텍처
Security Flow: 로그인 성공 시 MainActivity를 종료(finish)하여 보안 스택을 관리하는 현업 수준의 로직 적용.

Navigation: Intent 메신저를 활용한 액티비티 간 데이터 흐름 및 화면 전환 구현.

3. 보안 및 하드웨어 권한 시스템 (Critical)
Runtime Permission: 안드로이드 6.0 이상 정책에 대응하는 Activity Result API 기반 실시간 카메라 권한 요청 로직 탑재.

Manifest Optimization: 카메라 하드웨어 필수 조건(uses-feature) 선언을 통한 앱 안정성 강화.

4. AI 카메라 인프라 준비
CameraX Integration: 구글의 최신 카메라 라이브러리(CameraX) 의존성 설정 및 하드웨어 가속 준비 완료.