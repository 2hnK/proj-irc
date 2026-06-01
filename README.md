# proj-irc

Java `Socket`을 이용해 **IRC 스타일 채팅 서버/클라이언트 구조를 연습하기 위한 프로젝트**입니다.

하나의 서버가 여러 클라이언트 접속을 받고, 클라이언트는 닉네임 설정, 채널 입장, 채널 메시지 전송, 개인 메시지, 채널 목록 조회 등의 명령을 통해 간단한 채팅 흐름을 실습할 수 있습니다.

---

## 프로젝트 목적

이 프로젝트는 완성형 IRC 서버를 만드는 것보다, Java 기반 네트워크 프로그래밍의 기본 흐름을 직접 구현해보는 데 목적이 있습니다.

주요 학습 포인트는 다음과 같습니다.

- `ServerSocket`과 `Socket`을 이용한 TCP 연결 처리
- 클라이언트별 스레드 생성 및 요청 처리
- `DataInputStream`, `DataOutputStream` 기반 문자열 송수신
- 서버-클라이언트 간 간단한 명령어 프로토콜 설계
- 채널 단위 메시지 브로드캐스트
- 닉네임 등록 및 중복 검사
- 개인 메시지 전송
- 연결 종료 시 리소스 정리

---

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| Language | Java |
| Network | Java TCP Socket |
| I/O | `DataInputStream`, `DataOutputStream` |
| Concurrency | Thread 기반 처리 |
| Build Tool | 없음 |
| IDE | IntelliJ IDEA 프로젝트 설정 포함 |

> 별도의 Gradle/Maven 설정 없이 `src` 디렉터리 아래의 Java 파일로 구성된 연습용 프로젝트입니다.

---

## 프로젝트 구조

```text
proj-irc/
├─ src/
│  ├─ MainController.java
│  ├─ ChannelManager.java
│  ├─ ChannelReceiver.java
│  ├─ Client.java
│  ├─ ClientSender.java
│  └─ ClientReceiver.java
├─ .gitignore
├─ 2hnK.iml
└─ IRC.iml
```

---

## 주요 클래스 설명

### `MainController`

서버의 진입점입니다.

- `9910` 포트에서 `ServerSocket`을 생성합니다.
- 클라이언트 접속을 `accept()`로 대기합니다.
- 클라이언트가 접속하면 `ChannelReceiver` 스레드를 생성해 요청 처리를 위임합니다.

### `Client`

클라이언트의 진입점입니다.

- `127.0.0.1:9910` 서버에 접속합니다.
- 접속 성공 시 `ClientSender`, `ClientReceiver` 두 개의 스레드를 실행합니다.

### `ClientSender`

사용자 콘솔 입력을 서버로 전송하는 클라이언트 송신 스레드입니다.

- `Scanner(System.in)`으로 입력을 받습니다.
- 입력한 문자열을 `DataOutputStream.writeUTF()`로 서버에 전송합니다.
- `QUIT` 입력 시 송신 루프를 종료합니다.

### `ClientReceiver`

서버에서 온 메시지를 수신하는 클라이언트 수신 스레드입니다.

- `DataInputStream.readUTF()`로 서버 메시지를 읽습니다.
- 일반 메시지는 콘솔에 출력합니다.
- `PONG` 응답은 클라이언트 기준 ping 시간으로 계산해 출력합니다.

### `ChannelReceiver`

서버 측에서 클라이언트 한 명의 요청을 처리하는 스레드입니다.

- 클라이언트 메시지를 계속 수신합니다.
- `JOIN`, `NICK`, `PRIVMSG`, `LIST`, `PING` 등 명령어를 파싱합니다.
- 명령어가 아닌 일반 메시지는 현재 채널에 브로드캐스트합니다.
- 연결 종료 또는 예외 발생 시 닉네임/채널 정보를 정리하고 소켓을 닫습니다.

### `ChannelManager`

서버의 채널과 사용자 정보를 관리하는 객체입니다.

- 채널별 접속자 출력 스트림 관리
- 닉네임과 출력 스트림 매핑 관리
- 채널 참여/퇴장 처리
- 채널 메시지 브로드캐스트
- 닉네임 중복 검사
- 개인 메시지 전송

---

## 실행 방법

### 1. 컴파일

프로젝트 루트에서 실행합니다.

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/*.java
```

### 2. 서버 실행

첫 번째 터미널에서 서버를 실행합니다.

```bash
java -cp out MainController
```

실행되면 다음과 비슷한 메시지가 출력됩니다.

```text
IRC Server started on port 9910
```

### 3. 클라이언트 실행

다른 터미널에서 클라이언트를 실행합니다.

```bash
java -cp out Client
```

여러 명의 접속을 테스트하려면 클라이언트 터미널을 2개 이상 실행하면 됩니다.

```bash
java -cp out Client
java -cp out Client
```

---

## 사용 가능한 명령어

| 명령어 | 설명 | 예시 |
| --- | --- | --- |
| `HELP` | 사용 가능한 명령어 목록 출력 | `HELP` |
| `LIST` | 현재 생성된 채널 목록 조회 | `LIST` |
| `NICK <nickname>` | 닉네임 설정 | `NICK jihun` |
| `JOIN <channel>` | 채널 입장 | `JOIN study` |
| `PART` | 현재 채널 나가기 | `PART` |
| `PRIVMSG <user> <message>` | 특정 사용자에게 개인 메시지 전송 | `PRIVMSG minsu hello` |
| `USER` | 현재 사용자 정보 출력 | `USER` |
| `PING` | 서버와의 연결 상태 확인 | `PING` |
| `QUIT` | 서버 연결 종료 | `QUIT` |

닉네임은 영문, 숫자, 한글을 사용할 수 있으며 2~12자로 제한됩니다.

---

## 사용 예시

### 클라이언트 A

```text
NICK jihun
JOIN study
안녕하세요
```

### 클라이언트 B

```text
NICK minsu
JOIN study
반갑습니다
PRIVMSG jihun 개인 메시지 테스트
```

같은 채널에 접속한 클라이언트는 서로의 일반 채팅 메시지를 받을 수 있습니다.

---

## 동작 흐름

```text
서버 실행
  ↓
ServerSocket 생성 및 9910 포트 대기
  ↓
클라이언트 접속
  ↓
클라이언트마다 ChannelReceiver 스레드 생성
  ↓
클라이언트가 명령어 또는 채팅 메시지 전송
  ↓
ChannelReceiver가 명령어 파싱
  ↓
ChannelManager가 채널/닉네임/브로드캐스트 처리
  ↓
결과 메시지를 클라이언트에게 전송
```

---

## 구현된 기능

- 서버 실행 및 클라이언트 접속 수락
- 클라이언트별 독립 스레드 처리
- 닉네임 설정 및 중복 방지
- 채널 생성 및 참여
- 채널 목록 조회
- 채널 내 메시지 브로드캐스트
- 채널 나가기
- 사용자 정보 조회
- 개인 메시지 전송
- ping/pong 기반 연결 확인
- 클라이언트 연결 종료 및 리소스 정리

---

## 현재 한계 및 개선 아이디어

연습용 프로젝트이므로 다음과 같은 부분을 개선해볼 수 있습니다.

### 구조 개선

- `currentChannel` 초기값을 문자열 `"null"`이 아니라 실제 `null` 또는 별도 상태 객체로 관리
- 서버 종료 기능 추가
- 명령어 파싱 로직을 별도 클래스로 분리
- 클라이언트 세션 정보를 `ClientSession` 같은 객체로 추출
- 채널 사용자 목록 조회 기능 추가

### 안정성 개선

- 비정상 종료된 클라이언트의 출력 스트림 제거 강화
- `HashSet` 접근에 대한 동시성 처리 개선
- 예외 발생 시 서버 로그와 클라이언트 응답 분리
- 중복 닉네임 처리, 퇴장 처리, 재접속 처리 테스트 추가

### 사용성 개선

- 서버 주소/포트를 설정 파일 또는 실행 인자로 분리
- 명령어 입력 프롬프트 추가
- 클라이언트 화면 출력 포맷 정리
- Gradle 또는 Maven 도입
- JUnit 기반 단위 테스트 추가

---

## 이 프로젝트로 연습할 수 있는 질문

- 서버는 여러 클라이언트를 어떻게 동시에 처리하는가?
- 클라이언트 입력과 서버 응답 수신을 왜 별도 스레드로 분리해야 하는가?
- 채널 브로드캐스트는 어떤 방식으로 구현되는가?
- 닉네임 중복 검사는 서버의 어느 계층에서 처리하는 것이 적절한가?
- `Socket`, `InputStream`, `OutputStream`은 언제 닫아야 하는가?
- 현재 구조에서 동시성 문제가 발생할 수 있는 지점은 어디인가?

---

## 라이선스

현재 저장소에 별도의 라이선스 파일은 포함되어 있지 않습니다.
