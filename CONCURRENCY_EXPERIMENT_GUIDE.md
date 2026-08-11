# Java Socket Chat 동시성 실험 가이드

이 문서는 현재 채팅 프로젝트를 이용해 **Socket 통신, Mutex, Monitor, Race Condition**을 직접 관찰하기 위한 실습 가이드입니다.

처음부터 성능 최적화를 목표로 하지 않습니다. 다음 흐름을 반복하는 것이 목적입니다.

> 문제를 예상한다 → 재현한다 → 원인을 설명한다 → 코드를 수정한다 → 같은 조건에서 다시 측정한다

---

## 1. 실험 전 알아둘 개념

### `synchronized`와 Monitor

Java의 `synchronized`는 객체의 **고유 모니터(intrinsic monitor)**를 이용합니다.

```java
public synchronized void doSomething() {
    // 한 번에 하나의 스레드만 실행
}
```

같은 객체의 `synchronized` 메서드를 여러 스레드가 호출하면 한 스레드가 모니터를 획득하고, 나머지는 모니터가 해제될 때까지 기다립니다.

모니터는 다음 두 기능을 제공합니다.

- 공유 자원에 한 번에 하나의 스레드만 접근하게 하는 상호 배제
- `wait()`, `notify()`, `notifyAll()`을 이용한 조건 대기

### Mutex

Mutex는 한 번에 하나의 실행 주체만 공유 자원에 접근하도록 만드는 상호 배제 개념입니다. Java에서는 다음 수단으로 구현할 수 있습니다.

- `synchronized`: JVM이 잠금 획득과 해제를 관리
- `ReentrantLock`: 개발자가 `lock()`과 `unlock()`을 명시적으로 호출

```java
private final ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    // 공유 자원 접근
} finally {
    lock.unlock();
}
```

`unlock()`은 반드시 `finally`에서 호출해야 합니다.

### Race Condition

여러 스레드의 실행 순서에 따라 결과가 달라지는 문제입니다. 메서드 각각이 `synchronized`여도 여러 메서드로 구성된 작업 전체가 원자적이지 않으면 발생할 수 있습니다.

---

## 2. 공통 실험 규칙

각 실험은 아래 순서로 진행합니다.

1. 수정 전 결과를 먼저 기록한다.
2. 한 번에 한 가지 조건만 변경한다.
3. 같은 클라이언트 수와 메시지 수로 수정 전후를 비교한다.
4. 한 번의 결과만 믿지 않고 최소 5회 반복한다.
5. 예상과 다른 결과도 삭제하지 말고 이유를 분석한다.

### 공통 기록 항목

| 항목 | 기록 내용 |
| --- | --- |
| 날짜 | 실험 날짜와 시간 |
| Java 버전 | `java -version` 결과 |
| 실험 브랜치/커밋 | 결과를 재현할 수 있는 Git 정보 |
| 가설 | 실험 전에 예상한 결과 |
| 독립 변수 | 실험에서 변경한 한 가지 조건 |
| 통제 조건 | 클라이언트 수, 메시지 수, PC 환경 등 |
| 결과 | 성공 수, 실패 수, 시간, 오류 메시지 |
| 결론 | 원인과 수정 효과 |
| 한계 | 정확하지 않을 수 있는 이유 |

시간은 가능하면 `System.nanoTime()`으로 측정합니다. `System.currentTimeMillis()`는 사용자에게 보여줄 현재 시각에 적합하고, 경과 시간 측정에는 `nanoTime()`이 더 적합합니다.

---

## 3. 실험 0: 여러 클라이언트가 동시에 동작하는지 관찰

### 난이도

매우 쉬움

### 학습 목표

- 연결당 스레드 구조 이해
- 여러 클라이언트의 요청 순서가 매번 같지 않음을 관찰
- TCP 연결과 서버 스레드의 관계 확인

### 준비

서버를 실행합니다.

```bash
javac -encoding UTF-8 -d out src/*.java
java -cp out MainController
```

별도 터미널 3개에서 클라이언트를 실행합니다.

```bash
java -cp out Client
```

### 진행

각 클라이언트에서 닉네임을 설정하고 같은 채널에 입장합니다.

```text
NICK user1
JOIN study
```

세 클라이언트에서 최대한 비슷한 시점에 메시지를 반복해서 보냅니다.

### 관찰할 내용

- 메시지를 입력한 순서와 다른 클라이언트가 받은 순서가 항상 같은가?
- 한 클라이언트의 메시지 순서는 유지되는가?
- 서로 다른 클라이언트가 보낸 메시지의 전체 순서는 누가 결정하는가?

### 정리 질문

- TCP가 보장하는 순서는 모든 클라이언트의 전역 순서인가, 하나의 연결 안에서의 바이트 순서인가?
- 서버에서 브로드캐스트 순서를 보장하려면 어떤 추가 구조가 필요한가?

---

## 4. 실험 1: 문자열 `"null"` 상태 버그 찾기

### 난이도

매우 쉬움

### 학습 목표

- 상태를 문자열로 표현했을 때 생기는 버그 이해
- 동시성 실험 전에 단일 연결의 상태 전이를 정확하게 만들기

### 현재 코드의 예상 문제

`ChannelReceiver`의 `currentChannel` 초기값은 실제 `null`이 아니라 문자열 `"null"`입니다.

```java
String currentChannel = "null";
```

### 진행

1. 서버와 클라이언트 하나를 실행한다.
2. 닉네임이나 채널을 설정하지 않고 일반 메시지를 보낸다.
3. 서버 응답이 있는지 확인한다.
4. `USER` 명령으로 현재 채널을 확인한다.

### 가설

일반 메시지를 보내면 “채널에 먼저 입장하라”는 응답이 와야 하지만, 현재 코드는 문자열 `"null"`을 실제 채널로 판단해 메시지를 조용히 버릴 수 있습니다.

### 개선 가이드

초기값을 실제 `null`로 변경합니다.

```java
String currentChannel;
```

가능하다면 이후에는 `ClientSession` 객체가 닉네임과 현재 채널 상태를 소유하도록 개선합니다.

### 성공 기준

- 채널 입장 전 메시지를 보내면 명확한 오류 응답을 받는다.
- 첫 `JOIN`에서 존재하지 않는 `"null"` 채널을 나가려고 하지 않는다.

---

## 5. 실험 2: 닉네임 중복 등록 Race Condition

### 난이도

쉬움

### 학습 목표

- check-then-act 경쟁 조건 이해
- “각 메서드가 동기화됨”과 “전체 작업이 원자적임”의 차이 이해
- `putIfAbsent()`의 사용 이유 이해

### 현재 코드의 예상 문제

닉네임 설정은 다음 두 작업으로 분리되어 있습니다.

```text
1. 닉네임이 사용 가능한지 확인
2. 닉네임 등록
```

두 메서드가 각각 `synchronized`여도 그 사이에 다른 스레드가 실행될 수 있습니다.

```text
클라이언트 A: 사용 가능 확인 → true
클라이언트 B: 사용 가능 확인 → true
클라이언트 A: 등록
클라이언트 B: 같은 이름으로 등록
```

### 간단한 수동 실험

1. 클라이언트 두 개를 실행한다.
2. 두 터미널에 `NICK sameUser`를 미리 입력한다.
3. 최대한 동시에 Enter를 누른다.
4. 두 클라이언트가 모두 성공 응답을 받는지 확인한다.
5. 최소 20회 반복한다.

수동 실험에서 문제가 잘 나오지 않는 것은 문제가 없다는 뜻이 아닙니다. 경쟁 조건은 특정 실행 순서에서만 나타날 수 있습니다.

### 재현 확률을 높이는 학습용 방법

사용 가능 여부를 확인한 직후에 임시로 짧은 지연을 추가합니다.

```java
if (channelManager.isNicknameAvailable(newNickname)) {
    Thread.sleep(100);
    // 등록 코드
}
```

이 지연은 문제를 관찰하기 위한 장치일 뿐이며 최종 코드에서는 제거해야 합니다.

### 1차 개선: 하나의 원자적 메서드

확인과 등록을 하나의 `synchronized` 메서드 안에서 처리합니다.

```java
public synchronized boolean registerNicknameIfAvailable(
        String nickname,
        DataOutputStream outputStream
) {
    if (users.containsKey(nickname)) {
        return false;
    }

    users.put(nickname, outputStream);
    return true;
}
```

### 2차 개선: ConcurrentHashMap

```java
return users.putIfAbsent(nickname, outputStream) == null;
```

### 성공 기준

동시에 같은 닉네임을 요청해도 다음 조건을 항상 만족해야 합니다.

```text
성공한 클라이언트 수 == 1
실패한 클라이언트 수 == 전체 클라이언트 수 - 1
닉네임 레지스트리에 저장된 사용자 수 == 1
```

### 정리 질문

- `isNicknameAvailable()`과 `registerNickname()`이 각각 `synchronized`인데도 왜 문제가 생기는가?
- `containsKey()` 후 `put()`과 `putIfAbsent()`는 무엇이 다른가?

---

## 6. 실험 3: 전역 Monitor가 관계없는 작업까지 막는지 확인

### 난이도

쉬움

### 학습 목표

- 임계 영역과 잠금 범위 이해
- coarse-grained lock의 장단점 이해
- 잠금 대기 시간을 직접 관찰

### 현재 코드의 예상 문제

`ChannelManager`의 주요 메서드가 모두 같은 객체의 `synchronized` 모니터를 사용합니다. 따라서 서로 다른 채널의 작업도 동시에 실행되지 못합니다.

### 관찰을 위한 임시 코드

`broadcast()`의 모니터 안에 학습용 지연을 넣습니다.

```java
public synchronized void broadcast(String channelName, String message) {
    try {
        Thread.sleep(3000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // 기존 코드
}
```

### 진행

1. 클라이언트 A와 B는 `room-a`에 입장한다.
2. 클라이언트 C는 `room-b`에 입장하거나 새 닉네임을 설정할 준비를 한다.
3. A가 메시지를 보내 `broadcast()`가 모니터를 획득하게 한다.
4. 즉시 C가 `NICK`, `JOIN` 또는 `LIST` 명령을 실행한다.
5. C의 응답이 약 3초 늦어지는지 확인한다.

### 예상 결과

`room-a`의 브로드캐스트와 직접 관계없는 C의 작업도 같은 `ChannelManager` 모니터를 기다립니다.

### 개선 가이드

- 잠금 안에서는 공유 컬렉션 조회와 수신자 목록 복사만 수행한다.
- 실제 소켓 전송은 잠금을 해제한 후 수행한다.
- 채널별로 독립적인 집합이나 잠금을 사용하는 방법을 검토한다.
- `ConcurrentHashMap`과 `ConcurrentHashMap.newKeySet()`을 검토한다.

### 성공 기준

`room-a`의 느린 브로드캐스트가 `room-b`의 입장이나 닉네임 등록을 지연시키지 않아야 합니다.

### 주의

학습을 위해 넣은 `Thread.sleep()`은 실험이 끝나면 반드시 제거합니다.

---

## 7. 실험 4: 느린 클라이언트와 Blocking I/O

### 난이도

보통

### 학습 목표

- 소켓 쓰기도 오래 걸릴 수 있음을 이해
- 잠금 안에서 blocking I/O를 수행하면 안 되는 이유 이해
- backpressure 필요성 이해

### 시나리오

한 클라이언트가 서버의 메시지를 읽지 않고, 다른 클라이언트들이 같은 채널에 메시지를 계속 보냅니다. 운영체제의 소켓 버퍼가 가득 차면 해당 클라이언트로의 `writeUTF()`가 느려질 수 있습니다.

### 처음에는 쉽게 대체 실험하기

실제 소켓 버퍼를 가득 채우는 실험은 환경에 따라 오래 걸립니다. 먼저 실험 3처럼 특정 수신자 전송 직전에 `Thread.sleep()`을 넣어 느린 I/O를 모사합니다.

### 측정 항목

- 메시지 100개를 보내는 데 걸린 시간
- `LIST`, `JOIN`, `NICK` 응답 시간
- 정상 클라이언트의 메시지 수신 지연
- 실패하거나 유실된 메시지 수

### 개선 구조

각 클라이언트가 자신의 송신 큐와 writer 하나를 가지게 합니다.

```text
여러 요청 처리 스레드
        ↓ 메시지 enqueue
클라이언트별 bounded queue
        ↓ dequeue
클라이언트별 writer 하나
        ↓
Socket OutputStream
```

채널 관리자는 소켓에 직접 쓰지 않고 `ClientSession.send(message)`만 호출합니다.

### 반드시 결정할 정책

송신 큐가 가득 찼을 때의 정책을 정해야 합니다.

- 생산자 스레드가 기다린다.
- 오래된 메시지를 버린다.
- 새 메시지를 거절한다.
- 느린 클라이언트의 연결을 종료한다.

채팅 서버 학습 프로젝트에서는 **일정 시간 동안 큐가 비워지지 않으면 느린 클라이언트 연결을 종료**하는 정책을 추천합니다.

### 성공 기준

느린 클라이언트 한 명 때문에 다른 클라이언트나 다른 채널의 응답이 멈추지 않아야 합니다.

---

## 8. 실험 5: `wait/notifyAll`로 Monitor 직접 사용하기

### 난이도

보통

### 학습 목표

- 생산자-소비자 문제 이해
- 모니터의 조건 대기 이해
- `if`가 아니라 `while`로 조건을 검사해야 하는 이유 이해

### 구현 대상

실험 4의 클라이언트별 bounded queue를 학습 목적으로 직접 구현합니다.

```java
public class MessageQueue {
    private final Queue<String> queue = new ArrayDeque<>();
    private final int capacity;

    public MessageQueue(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void put(String message) throws InterruptedException {
        while (queue.size() >= capacity) {
            wait();
        }

        queue.add(message);
        notifyAll();
    }

    public synchronized String take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }

        String message = queue.remove();
        notifyAll();
        return message;
    }
}
```

### 진행

1. 큐 용량을 3으로 설정한다.
2. 생산자는 빠르게 메시지 10개를 넣는다.
3. 소비자는 메시지 하나를 꺼낼 때마다 1초 기다린다.
4. 생산자가 큐가 가득 찼을 때 대기하는지 로그로 확인한다.
5. 소비자가 메시지를 꺼낸 후 생산자가 다시 실행되는지 확인한다.

### 실수 실험

조건문의 `while`을 잠시 `if`로 바꾸어 여러 생산자와 소비자를 실행해 봅니다. 조건이 다시 거짓이 되었는데도 깨어난 스레드가 진행할 가능성을 생각해 봅니다.

### 실무형 개선

직접 만든 큐를 다음으로 교체합니다.

```java
BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
```

### 비교할 내용

- 직접 구현한 Monitor queue의 코드 복잡도
- 종료 신호 전달 방식
- timeout 구현 난이도
- `ArrayBlockingQueue`가 제공하는 기능

최종 서버에는 검증된 `BlockingQueue`를 사용하고, 직접 구현은 학습 코드나 테스트로 남기는 것을 추천합니다.

---

## 9. 실험 6: `synchronized`와 `ReentrantLock` 비교

### 난이도

보통

### 학습 목표

- 암묵적 잠금과 명시적 잠금 비교
- 공정 잠금과 비공정 잠금의 차이 이해
- timeout과 interrupt 가능한 잠금 이해

### 비교 대상

```java
// Monitor
public synchronized void update() {
    // 공유 상태 수정
}
```

```java
// 명시적 Lock
private final ReentrantLock lock = new ReentrantLock();

public void update() {
    lock.lock();
    try {
        // 공유 상태 수정
    } finally {
        lock.unlock();
    }
}
```

공정 잠금도 비교합니다.

```java
private final ReentrantLock lock = new ReentrantLock(true);
```

### 진행

1. 공유 카운터를 0으로 만든다.
2. 스레드 10개가 각각 100,000번 증가시킨다.
3. 잠금 없이 실행한다.
4. `synchronized`로 실행한다.
5. 비공정 `ReentrantLock`으로 실행한다.
6. 공정 `ReentrantLock`으로 실행한다.
7. 각 방식을 최소 5회 반복한다.

### 기록할 내용

- 최종 카운터가 항상 1,000,000인지
- 전체 실행 시간
- 각 스레드의 완료 시간
- 공정 잠금에서 처리량이 어떻게 변하는지

### 해석 주의

이 간단한 측정은 정밀 벤치마크가 아닙니다. JVM warm-up, JIT 컴파일, GC, 운영체제 스케줄링의 영향을 받습니다. 결과를 절대적인 성능 순위로 표현하지 말고 동작과 trade-off를 이해하는 데 사용합니다.

### 정리 질문

- 단순 상호 배제라면 어떤 방식이 읽기 쉬운가?
- `tryLock(timeout)`, 여러 `Condition`, 공정성 설정이 필요하다면 어떤 방식이 적합한가?
- 공정성이 높아지는 대신 어떤 비용이 생겼는가?

---

## 10. 실험 7: 플랫폼 스레드와 가상 스레드 비교

### 난이도

보통

### 학습 목표

- 연결당 스레드 모델의 비용 이해
- Java 21 virtual thread의 사용 목적 이해
- 스레드 모델 변경이 race condition을 해결하지는 않는다는 점 이해

### 비교 대상

- 현재처럼 연결마다 플랫폼 스레드 생성
- 연결마다 가상 스레드 생성

예시:

```java
Thread.ofVirtual().start(() -> handleClient(clientSocket));
```

또는 다음 executor를 사용할 수 있습니다.

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> handleClient(clientSocket));
}
```

서버 전체 수명 동안 executor를 유지해야 하므로 실제 구현에서는 `accept()` 반복문 밖에서 생성합니다.

### 진행

먼저 메시지를 거의 보내지 않는 idle client를 여러 개 연결합니다.

1. 100개 연결
2. 500개 연결
3. 1,000개 연결

PC가 불안정해지거나 오류가 증가하면 즉시 클라이언트 수를 낮춥니다.

### 측정 항목

- 성공한 연결 수
- 프로세스 메모리 사용량
- 서버 스레드 수
- 연결 생성 시간
- `PING` 응답 시간

### 반드시 기록할 결론

가상 스레드는 많은 blocking 작업을 표현하기 쉽게 만들지만 다음 문제를 자동으로 해결하지 않습니다.

- 공유 `Map`의 race condition
- 같은 출력 스트림에 대한 동시 쓰기
- 잠금 안에서 수행하는 느린 I/O
- 무제한 큐로 인한 메모리 사용 증가

---

## 11. 테스트 도구를 만들 때의 가이드라인

터미널에서 Enter를 동시에 누르는 방식은 학습 시작에는 좋지만 반복하기 어렵습니다. 이후에는 `LoadClient` 같은 테스트 전용 클래스를 추가하는 것을 추천합니다.

### LoadClient가 제공하면 좋은 옵션

```text
--clients 100
--messages 1000
--channel study
--same-nickname raceUser
--read-delay-ms 1000
--duration-seconds 30
```

### 동시 시작 방법

여러 스레드가 준비된 후 한 번에 시작하도록 `CountDownLatch`를 사용합니다.

```java
CountDownLatch ready = new CountDownLatch(clientCount);
CountDownLatch start = new CountDownLatch(1);

// 각 클라이언트 스레드
ready.countDown();
start.await();
sendRequest();

// 제어 스레드
ready.await();
long startedAt = System.nanoTime();
start.countDown();
```

### 테스트가 자동 검증해야 할 값

- 연결 성공/실패 수
- 요청 성공/실패 수
- 보낸 메시지 수와 받은 메시지 수
- 중복 닉네임 성공 수
- 평균이 아닌 p50/p95/p99 응답 시간
- timeout 수

테스트 결과는 CSV로 저장하면 이후 그래프를 만들기 쉽습니다.

```csv
implementation,clients,messages,throughput,p50_ms,p95_ms,p99_ms,errors
synchronized,100,10000,1200,8,45,110,0
per_client_queue,100,10000,3100,4,12,25,0
```

숫자는 형식 예시이며 실제 측정값으로 교체해야 합니다.

---

## 12. 권장 진행 순서

처음에는 아래 네 단계만 완료해도 충분합니다.

### 1단계: 정확성

- 문자열 `"null"` 상태 수정
- 닉네임 race condition 재현 및 수정
- `QUIT`과 비정상 종료의 cleanup을 한 곳으로 통합

### 2단계: 잠금 범위

- 전역 모니터 안의 지연 실험
- 잠금 안에서는 수신자 목록만 복사
- 네트워크 I/O를 잠금 밖으로 이동

### 3단계: 느린 소비자

- 클라이언트별 bounded queue 추가
- writer를 클라이언트마다 하나만 유지
- 큐가 가득 찼을 때의 정책 정의

### 4단계: 확장성

- 자동 부하 클라이언트 작성
- 플랫폼 스레드와 가상 스레드 비교
- 처리량과 p95/p99 지연 기록

---

## 13. 실험 보고서 템플릿

각 실험 결과를 다음 형식으로 작성합니다.

```markdown
# 실험 제목

## 문제
어떤 코드에서 어떤 문제가 발생할 수 있는가?

## 가설
어떤 조건에서 어떤 결과가 나올 것으로 예상하는가?

## 실험 환경
- Java:
- OS:
- CPU/Memory:
- 클라이언트 수:
- 메시지 수:
- Git commit:

## 재현 절차
다른 사람이 그대로 따라 할 수 있도록 명령과 순서를 작성한다.

## 수정 전 결과
표, 로그, 처리량, p95/p99 지연을 기록한다.

## 원인 분석
스레드 실행 순서, 공유 상태, 잠금 범위를 중심으로 설명한다.

## 개선 방법
변경한 코드와 선택 이유를 설명한다.

## 수정 후 결과
수정 전과 같은 조건에서 다시 측정한다.

## 결론
정확성, 처리량, 지연, 코드 복잡도 사이의 trade-off를 정리한다.

## 한계와 다음 실험
이번 실험이 검증하지 못한 내용과 다음 계획을 적는다.
```

---

## 14. 포트폴리오 작성 가이드

“동시성 기능을 공부했다”보다 문제와 결과를 구체적으로 작성합니다.

### 좋은 설명 구조

```text
상황 → 재현된 문제 → 원인 → 선택한 해결책 → 검증 결과 → trade-off
```

### 작성 예시

> 동시에 같은 닉네임을 등록할 때 check-then-act 경쟁 조건으로 두 요청이 모두 성공할 수 있음을 테스트로 재현했습니다. 확인과 등록을 `putIfAbsent` 기반 원자 연산으로 변경했고, 100개 클라이언트의 동시 요청을 반복해 성공자가 항상 한 명임을 검증했습니다.

> 전역 모니터를 보유한 채 blocking socket write를 수행해 느린 클라이언트가 관계없는 채널 요청까지 지연시키는 문제를 재현했습니다. 클라이언트별 bounded outbound queue와 단일 writer 구조로 변경한 뒤 동일 부하에서 처리량과 p95/p99 지연을 비교했습니다.

### 포함하면 좋은 자료

- 실패가 재현된 서버 로그
- 실행 가능한 테스트 명령
- 수정 전후 구조 그림
- 수정 전후 결과 표
- p50/p95/p99 지연 그래프
- 선택하지 않은 대안과 그 이유
- 실험의 한계

성능 수치를 과장하지 말고 테스트 환경과 부하 조건을 함께 적어야 다른 사람이 결과를 해석하고 재현할 수 있습니다.

---

## 완료 체크리스트

- [ ] 문제 상황을 수정 전에 재현했다.
- [ ] 실험 전 가설을 기록했다.
- [ ] 한 번에 하나의 조건만 변경했다.
- [ ] 수정 전후를 같은 조건에서 비교했다.
- [ ] 최소 5회 반복했다.
- [ ] 성공뿐 아니라 실패와 timeout도 집계했다.
- [ ] 평균 외에 p95 또는 p99 지연을 기록했다.
- [ ] 잠금 범위 안에 blocking I/O가 없는지 확인했다.
- [ ] 하나의 출력 스트림에 writer 하나만 접근하게 했다.
- [ ] 느린 클라이언트의 backpressure 정책을 문서화했다.
- [ ] 실행 방법과 Git commit을 기록했다.
- [ ] 결과의 한계와 다음 실험을 작성했다.
