# proj-irc

간단한 IRC(Internet Relay Chat) 클라이언트/채널 관리 로직을 Java로 구현한 프로젝트입니다. 저장소 구조와 클래스명을 기준으로 네트워크 소켓 기반의 송수신 스레드, 채널 관리, 클라이언트 제어 흐름을 포함하고 있습니다.

> 참고: 저장소에는 빌드 도구 설정(Gradle/Maven)이 없고 `src` 디렉터리에 순수 Java 소스가 위치합니다. IntelliJ IDEA 프로젝트 파일(`.iml`, `.idea/`)이 포함되어 있어 IDE에서 바로 열어 실행할 수 있습니다.

---

## 프로젝트 구조

```
proj-irc/
├─ src/
│  ├─ ChannelManager.java
│  ├─ ChannelReceiver.java
│  ├─ Client.java
│  ├─ ClientReceiver.java
│  ├─ ClientSender.java
│  └─ MainController.java
├─ .gitignore
├─ 2hnK.iml
└─ IRC.iml
```

### 주요 클래스(역할 개요)

- `MainController.java`: 애플리케이션의 진입점(메인 클래스)으로 추정되며, 전반적인 실행 제어를 담당합니다.
- `Client.java`: IRC 서버와의 연결, 세션 정보, 클라이언트 상태를 관리하는 코어 객체로 보입니다.
- `ClientSender.java`: 서버로 메시지(명령)를 전송하는 송신 로직을 담당합니다.
- `ClientReceiver.java`: 서버로부터 메시지를 수신하는 수신 로직을 담당합니다.
- `ChannelManager.java`: 참여 중인 IRC 채널 목록과 채널별 상태/참여자 등을 관리합니다.
- `ChannelReceiver.java`: 채널 단위의 수신/처리 로직(예: 채널 메시지 분배)을 담당합니다.

> 위 역할 설명은 파일명과 구성으로부터의 개략적 추정입니다. 실제 동작은 소스 주석/코드를 참고하세요.

---

## 요구 사항

- Java 8 이상(JDK 1.8+)
- (선택) IntelliJ IDEA 2020+ 또는 호환 IDE

---

## 빌드 및 실행

빌드 도구가 없으므로 두 가지 방법 중 하나를 선택하세요.

### 1) IntelliJ IDEA로 실행
1. IntelliJ에서 프로젝트를 열기(import).
2. 프로젝트 SDK를 JDK 1.8+로 설정.
3. `MainController`를 실행 구성으로 선택하여 실행.
   - 만약 `MainController`에 `public static void main`이 없다면 `Client` 등 메인 메서드가 있는 클래스를 실행 대상으로 선택하세요.

### 2) 커맨드라인에서 실행
프로젝트 루트에서 다음을 실행합니다.

```bash
# 출력 디렉터리 생성
mkdir -p out

# 컴파일
javac -encoding UTF-8 -d out src/*.java

# 실행 (메인 클래스가 MainController인 경우)
java -cp out MainController

# 메인 클래스가 다를 경우(예: Client), 해당 클래스로 실행
# java -cp out Client
```

> 서버 주소/포트/닉네임 등 연결 파라미터가 코드 상의 상수/설정으로 정의되어 있을 수 있습니다. 실행 전에 관련 값을 소스에서 확인/수정하세요.

---

## 기능(추정)

- IRC 서버 접속 및 연결 유지
- 채널 참여/나가기 관리
- 메시지 송수신(별도 송신/수신 스레드)
- 채널 단위 메시지 처리 및 분배

> 구체적인 명령어 포맷, 입력 방식, 출력 포맷은 소스 코드의 주석과 구현을 참고하세요.

---

## 개발 노트

- 송신(`ClientSender`)과 수신(`ClientReceiver`/`ChannelReceiver`)을 분리하여 동시성 처리(스레드 기반)를 사용하는 구조로 보입니다.
- 채널 관련 로직은 `ChannelManager`가 집중 관리합니다.
- 빌드 도구 도입 시 Gradle/Maven 설정을 추가하면 배포/실행이 쉬워집니다.

---

## 개선 아이디어

- Gradle 또는 Maven 도입(빌드, 실행, 패키징 자동화)
- 구성 파일(.properties/.yaml)로 서버/채널/닉네임 등 외부 설정화
- 로깅 프레임워크(SLF4J/Logback) 도입
- 단위 테스트(JUnit) 추가
- 예외 처리 및 재연결 로직 강화
- README에 실제 사용 예시/스크린샷 추가

---

## 라이선스

현재 저장소에 LICENSE 파일이 없습니다. 별도의 라이선스가 명시되기 전까지는 코드 사용에 주의하시기 바랍니다.
