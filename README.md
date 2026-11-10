# dskion touch

개인용 배달 라이더 화면 보조 음성 제어 어시스턴트. 배민/쿠팡이츠 라이더 앱의
화면을 접근성 서비스로 읽어서, 음성 명령(일반 모드) 또는 화면 자동 감지(배달
모드)로 버튼을 대신 탭해준다. **본인만 사용하는 사이드로딩 전용 앱**이라
플레이스토어 정책은 고려하지 않았다. 로그인/백엔드 없이 기기 내에서만 동작한다.

## 아키텍처

```
dskion-touch/
├── core/   순수 Kotlin(JVM) — 화면 스냅샷 모델, 화면/버튼 매칭 룰, 명령어 매처
│           (Android SDK 없이 유닛 테스트 가능)
└── app/    Android 앱 — 접근성 서비스, 오버레이, 음성 인식, Compose UI
```

- **음성 인식**: 안드로이드 기본 `SpeechRecognizer` (푸시투토크, 추가 의존성
  없음)로 시작. 소음/오프라인 환경에서 불안정하면 Vosk(온디바이스, 고정
  명령어집합 특화)로 교체하는 로드맵.
- **화면 인식**: `AccessibilityService`가 현재 화면의 노드 트리를 읽어
  `ScreenSnapshot`으로 변환 → `core`의 `CommandMatcher`가 등록된
  `ScreenProfile`/`CommandRule`과 비교해 대상 버튼을 찾음. 화면/명령어 규칙은
  실제 배민/쿠팡이츠 화면 덤프를 받아서 하나씩 채워나간다 (하드코딩된 Kotlin
  객체, 화면 수가 적어 DSL/JSON 룰 엔진은 과한 설계).
- **탭 실행**: `AccessibilityNodeInfo.performAction(ACTION_CLICK)` 우선 시도,
  실패하면 `dispatchGesture()`로 좌표 탭 폴백.

## 빌드 순서 (마일스톤)

1. ✅ 스캐폴드: core+app 모듈, CI, 권한 체크리스트만 있는 HomeScreen
2. ✅ 접근성 서비스 뼈대 (화면 덤프를 Console 화면에 표시만, 매칭·탭 없음)
3. 🔸 실제 덤프 기반 첫 `ScreenProfile`/`CommandRule`(배민 "운행시작") + 수동 음성 명령 탭 —
   콘솔 탭의 "음성 명령 테스트" 버튼으로 검증 중
4. 오버레이 배지
5. 배달 모드(자동 탭)
6. 화면/명령어 반복 추가

## 빌드/검증 현황

- `core` 모듈은 순수 Kotlin/JVM이라 로컬에서 직접 빌드/테스트했고 전부
  통과했습니다 (매칭 엔진 로직 검증, 가상 화면 덤프 픽스처 기반).
- `.github/workflows/build-apk.yml`이 `main` 브랜치 푸시마다 GitHub Actions에서
  `app`을 실제로 빌드하고, `debug-latest` GitHub Release에 APK를 자동
  게시합니다 — 사이드로딩용 안정적인 직접 다운로드 링크.

## 실행 방법 (Android Studio)

1. 저장소 클론해서 Android Studio로 열기
2. Gradle Sync
3. `app` 모듈 실행 → 휴대폰에 설치
4. 홈 화면에서 마이크/접근성/오버레이/배터리 제한 해제 권한을 하나씩 허용

## 알려진 리스크

- 배민/쿠팡이츠 라이더 앱의 접근성 노드 구조(resource-id, 텍스트)는 버전
  업데이트마다 바뀔 수 있어 룰을 주기적으로 재확인해야 함.
- 배달 플랫폼 이용약관상 화면 자동 조작에 대한 리스크는 사용자가 인지하고
  있음 (개인 사용 목적).
