# LocalFileOrganizer

로컬 디스크에 흩어져 있는 파일들을 파일명 앞 8자리의 날짜(yyyyMMdd)를 기준으로 연/월 폴더로 자동 정리해 주는 Spring Boot 애플리케이션입니다. 간단한 웹 UI를 통해 소스/대상 폴더를 입력하고 진행 상황을 실시간(SSE)으로 확인할 수 있습니다.

> 원본 파일은 유지되고, 대상 폴더에 동일 파일이 있으면 건너뜁니다. (아래 "동작 방식" 참고)

---

## 주요 기능
- 파일명 접두 yyyyMMdd(예: `20240131_회의록.docx`)를 기준으로 날짜를 파싱하여 분류
- 연/월 구조로 폴더 생성: `YYYY/YYYY_MM` (예: `2024/2024_01`)
- 동일 파일이 대상 폴더에 이미 있는 경우 건너뜀
- 진행 상황/요약/경고/오류를 Server‑Sent Events(SSE)로 스트리밍
- 웹 UI 제공: 경로 입력, 진행 로그, 요약 대시보드(완료/건너뜀/에러)
- 경로 입력 로컬 저장(localStorage) 지원: 다음 접속 시 자동 복원

## 시스템 요구 사항
- Java 17+
- Gradle(동봉된 Gradle Wrapper 사용 권장)
- OS 제약 없음(예시 및 기본 설정은 Windows 경로 기준)

## 빠른 시작
### 1) 소스 코드 실행(개발 모드)
- Windows PowerShell 기준
```powershell
# 프로젝트 루트에서
./gradlew.bat bootRun
```
- macOS/Linux
```bash
./gradlew bootRun
```

애플리케이션이 시작되면 브라우저로 `http://localhost:8080/` 접속합니다.

### 2) 실행 JAR 빌드/실행
```powershell
# 빌드
./gradlew.bat clean build

# 실행 (버전은 build 결과에 따라 다를 수 있음)
java -jar build/libs/LocalFileOrganizer-0.0.1-SNAPSHOT.jar
```

## 설정
설정 파일: `src/main/resources/application.properties`

```properties
spring.application.name=LocalFileOrganizer

# 기본 소스 디렉토리 (예)
file.source-dir=E:/temp/source

# 기본 대상 디렉토리 (예)
file.target-dir=E:/temp/target

# 파일 분류 동작방식 (MOVE/COPY)
file.working.mode=MOVE
```

우선순위 규칙:
1) 웹 UI에서 사용자가 입력한 값(쿼리 파라미터)
2) `application.properties`의 기본값

즉, UI에 값을 입력하면 해당 실행에 한해 설정 파일 값을 덮어씁니다.

## 웹 UI 사용법
1. `http://localhost:8080/` 접속
2. 소스 폴더(Source Dir)와 대상 폴더(Target Dir)를 입력하거나 비워 두면 서버 기본값 사용
3. "분류 시작" 버튼 클릭
4. 하단 "진행 상황 로그"와 상단 대시보드를 통해 실시간 진행 확인
   - 분류완료: 대상 폴더로 복사된 파일 수
   - 건너뜀: 대상 폴더에 동일 이름의 파일이 이미 있어 스킵된 건수
   - 에러: 처리 중 오류 발생 건수
5. 경로 입력값은 브라우저 localStorage에 저장되어 다음 접속 때 자동 복원됩니다.

## 동작 방식
- 컨트롤러: `GET /api/sort-and-progress`로 SSE 연결 생성
- 서비스: `FileSortingService#sortFiles`가 비동기로 파일을 순회하며 처리
- 파일 선택: 소스 폴더 하위 전체를 `Files.walk`로 탐색, 정규 파일만 대상
- 날짜 파싱: 파일명 앞 8자리를 `yyyyMMdd`로 파싱(유효하지 않으면 스킵)
- 폴더 구조: `<target>/<YYYY>/<YYYY_MM>/` 생성 후 파일 배치
- 충돌 처리: 대상에 동일 파일 존재 시 스킵
- 전송 방식: 진행 메시지를 JSON으로 직렬화하여 SSE 이벤트 스트림으로 전송
- 완료 처리: 요약 메시지 전송 후 `complete` 이벤트 송신 및 스트림 종료

> application.properties 파일의 file.working.mode 값에 따라 파일 분류시 이동/복사 동작방식을 변경 할 수 있습니다.
> (권한/충돌/디스크 용량 등 환경 차이에 유의)
```java
if (MODE_MOVE.equals(workingMode)) {
    // 파일 이동
    Files.move(file, targetFile);
} else {
    Files.copy(file, targetFile);
}
```


## API
### SSE: 분류 시작 및 진행 스트리밍
- 메서드/경로: `GET /api/sort-and-progress`
- 쿼리 파라미터(선택):
  - `sourceDir`: 소스 디렉토리 절대경로
  - `targetDir`: 대상 디렉토리 절대경로
- 응답: `text/event-stream` (SSE)
- 이벤트 이름:
  - `message`: 일반 정보/경고(JSON)
  - `error`: 오류(JSON)
  - `complete`: 완료 알림(JSON)

예시 메시지 페이로드(JSON):
```json
{
  "level": "INFO|WARN|ERROR|COMPLETE",
  "message": "사람이 읽을 수 있는 설명 텍스트"
}
```
주요 메시지 예:
- 초기 연결: `"파일 분류 서버에 연결되었습니다... (Source: ...)(Target: ...)"`
- 파일 이동(복사) 로그: `"이동: '파일명.ext' -> YYYY/MM"` → UI에서 "분류완료" 카운트 증가
- 중복 파일 스킵: `"'파일명.ext' 파일이 대상 폴더에 이미 존재하여 건너뜁니다."` → UI에서 "건너뜀" 카운트 증가
- 유효하지 않은 파일명: `"'파일명.ext'의 파일명이 yyyyMMdd로 시작하지 않아 건너뜁니다."`
- 완료 요약: `"작업 완료: 총 N개 파일 중 X개 이동, Y개 건너뜀."` 이후 `COMPLETE`

## 폴더/파일 규칙 및 제약
- 지원 파일명 패턴: 파일명 앞 8자리가 `yyyyMMdd` 형식이어야 함
  - 예: `20231201_report.pdf`, `20240131-회의록.docx`
- 하위 폴더 포함 전체 스캔 수행
- 대상 폴더에 동일 파일명이 있으면 덮어쓰지 않고 스킵
- 대용량 처리 시 I/O 비용이 큼: SSD 권장, 충분한 디스크 용량 확보
- 권한 문제: 소스/대상 경로에 읽기/쓰기 권한 필요
- 네트워크 드라이브/클라우드 동기화 폴더에서는 동작이 느리거나 실패할 수 있음

## 로깅
- 서버 로그: 처리 요약/오류를 SLF4J로 출력
- 클라이언트(UI): SSE로 수신한 메시지를 화면에 누적 표시, 레벨에 따라 색상 구분(INFO, WARN, ERROR)

## 개발 가이드
### 프로젝트 구조(요약)
```
LocalFileOrganizer/
├─ build.gradle
├─ src
│  ├─ main
│  │  ├─ java/com/chochocho/lforganizer
│  │  │  ├─ LocalFileOrganizerApplication.java   # 진입점(@EnableAsync)
│  │  │  ├─ controller/FileSortingController.java # SSE 엔드포인트
│  │  │  ├─ service/FileSortingService.java       # 분류 로직(비동기)
│  │  │  └─ dto/ProgressEvent.java                # 진행 이벤트 DTO
│  │  └─ resources
│  │     ├─ application.properties                # 기본 경로 설정
│  │     └─ static/index.html                     # 웹 UI(Tailwind + jQuery)
│  └─ test
│     └─ java/com/chochocho/lforganizer/LocalFileOrganizerApplicationTests.java
└─ README.md
```

### 빌드 스택
- Spring Boot 3.5.x
- Java 17
- Web: Spring MVC + SSE
- UI: 정적 HTML(Tailwind CSS CDN, jQuery)
- Lombok 사용(선택적)

### 로컬 실행 포트 변경
`application.properties`에서 `server.port` 설정을 추가하세요.
```properties
server.port=9090
```

### 테스트
기본 스켈레톤 테스트가 포함되어 있습니다.
```powershell
./gradlew.bat test
```

## 자주 묻는 질문(FAQ)
- Q. 왜 파일을 이동하지 않고 복사하나요?
  - A. 안전을 위해 기본 동작을 복사로 두었습니다. 원본 보존이 목적이며, 이동이 필요하면 `FileSortingService`에서 `Files.copy`를 `Files.move`로 변경하세요.
- Q. 파일명이 규칙을 따르지 않으면?
  - A. 앞 8자리가 `yyyyMMdd` 패턴이 아니면 스킵합니다. 필요 시 규칙을 확장하도록 코드를 수정하세요.
- Q. 대소문자/로케일 영향은?
  - A. 날짜 파싱은 숫자만 사용하므로 로케일 영향이 없습니다.
- Q. 덮어쓰기를 허용하려면?
  - A. 현재는 동일 파일명이 있으면 건너뜁니다. 정책 변경이 필요하면 존재 체크 후 `REPLACE_EXISTING` 옵션으로 복사/이동을 수행하도록 수정하세요.

## 라이선스 / 기여
- 라이선스: 미정(원하시는 라이선스를 알려주시면 반영하겠습니다. 예: MIT, Apache-2.0)
- 기여: Issue/PR 환영합니다. 개선 희망 사항을 등록해주세요.

---

문의나 변경 요청이 있으시면 이슈로 남겨주세요. 즐거운 파일 정리 되세요! 🎯