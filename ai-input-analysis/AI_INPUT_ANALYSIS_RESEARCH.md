# AI API를 활용한 사용자 Input 분석 - 상세 조사 문서

> 작성 목적: Spring 서버에서 Gemini API를 호출해 사용자 텍스트를 구조화된 태그로 추출하는 방법 조사  
> 우리 프로젝트 구조 기반 (Flutter → Spring Server → Gemini)

---

## 목차
1. [전체 흐름 이해](#1-전체-흐름-이해)
2. [왜 Gemini인가?](#2-왜-gemini인가)
3. [Gemini API 키 발급 방법](#3-gemini-api-키-발급-방법)
4. [핵심 개념: Prompt Engineering](#4-핵심-개념-prompt-engineering)
5. [Spring에서 Gemini API 호출하는 법](#5-spring에서-gemini-api-호출하는-법)
6. [게시물 작성 흐름에 AI 분석 통합](#6-게시물-작성-흐름에-ai-분석-통합)
7. [API 키 보안 관리](#7-api-키-보안-관리)
8. [예외 처리 방법](#8-예외-처리-방법)
9. [테스트 방법](#9-테스트-방법)
10. [요약 및 구현 순서](#10-요약-및-구현-순서)

---

## 1. 전체 흐름 이해

우리 프로젝트 아키텍처(Excalidraw 기준)에서 AI 분석이 어디에 들어가는지:

```
[Flutter 앱]
    │
    │  ① POST /api/posts  { "content": "중도에 전공책 두고 왔는데 전정대로 가져다 주실분…" }
    │     Header: Authorization: Bearer {JWT토큰}
    ▼
[Spring 서버]
    │
    │  ② Gemini API 호출
    │     POST https://generativelanguage.googleapis.com/...
    │     Body: { 프롬프트 + 사용자 텍스트 }
    ▼
[Gemini API]
    │
    │  ③ JSON 응답 반환
    │     { "출발지": "중도", "목적지": "전정대", "목표": "배달", "물건": "전공책" }
    ▼
[Spring 서버]
    │
    │  ④ 파싱 후 DB(Firestore)에 태그 포함해서 저장
    │     postId 반환
    ▼
[Flutter 앱]
```

**핵심 포인트:**
- Flutter는 그냥 텍스트만 보내면 됨 (AI 호출은 Spring이 알아서 처리)
- Spring이 Gemini 호출 → 태그 추출 → DB 저장을 한 번에 처리
- Flutter는 결과로 postId만 받음

---

## 2. 왜 Gemini인가?

Excalidraw 아키텍처 다이어그램에 이미 **Gemini**로 명시되어 있고, 실질적 이유:

| 비교 항목 | Gemini 1.5 Flash | OpenAI GPT-4o-mini |
|-----------|-----------------|-------------------|
| **무료 티어** | ✅ 있음 (분당 15회, 일 1500회) | ❌ 없음 (크레딧 소진 후 유료) |
| **응답 속도** | 매우 빠름 | 빠름 |
| **JSON 출력 강제** | `responseMimeType` 옵션 지원 | `response_format` 옵션 지원 |
| **한국어 처리** | 우수 | 우수 |
| **API 키 발급** | Google 계정만 있으면 즉시 | 신용카드 등록 필요 |

**해커톤 환경에서는 Gemini가 압도적으로 유리** (무료 + 빠른 발급)

---

## 3. Gemini API 키 발급 방법

### 단계별 가이드

**Step 1.** https://aistudio.google.com 접속 (Google 계정 로그인)

**Step 2.** 좌측 메뉴에서 **"Get API key"** 클릭

**Step 3.** **"Create API key"** 클릭 → 프로젝트 선택 또는 새로 생성
- 준호님이 Firebase 프로젝트를 추가해주시면 그 프로젝트 선택
- 아니면 "Create API key in new project"로 새로 만들어도 됨

**Step 4.** 생성된 키 복사 (예: `AIzaSy...`)

**Step 5.** `application.properties`에 입력:
```properties
gemini.api.key=AIzaSy...여기에_발급받은_키
```

### 무료 티어 제한
- 분당 15회 요청
- 일 1,500회 요청
- 해커톤 발표/테스트 용도로는 충분

---

## 4. 핵심 개념: Prompt Engineering

AI API의 핵심은 **어떻게 질문하냐**에 따라 결과가 완전히 달라진다는 것.
우리가 원하는 건 항상 **일정한 JSON 형식**으로 답변을 받는 것이므로,
이를 강제하는 프롬프트 설계가 가장 중요하다.

### 4-1. System Prompt vs User Prompt

| 구분 | 역할 | 예시 |
|------|------|------|
| **System Prompt** | AI의 역할과 출력 형식을 고정 | "너는 분석 도우미야. 반드시 JSON으로만 답해." |
| **User Prompt** | 실제 분석할 텍스트 | "중도에 전공책 두고 왔는데..." |

Gemini는 System/User 구분 없이 하나의 텍스트로 합쳐서 보내도 되지만,
역할 지시 + 사용자 입력을 구분해서 작성하는 것이 더 정확하다.

### 4-2. 프롬프트 설계 전략

**나쁜 프롬프트 (출력 형식 불명확):**
```
이 텍스트에서 출발지, 목적지, 목표를 알려줘: 중도에 전공책 두고 왔는데...
```
→ AI가 "출발지는 중도입니다. 목적지는..." 같은 자연어로 답할 수 있음

**좋은 프롬프트 (JSON 강제):**
```
너는 심부름/배달 요청 텍스트를 분석하는 도우미야.
사용자의 입력에서 아래 정보를 추출해서 반드시 JSON 형식으로만 답해줘.
다른 설명 텍스트는 절대 포함하지 마.

추출할 정보:
- 출발지: 물건이 현재 있는 장소
- 목적지: 물건을 가져다줄 장소  
- 목표: 요청의 종류 (배달/구매/기타 중 하나)
- 물건: 심부름 대상 물건
- 기타: 그 외 특이사항

값을 알 수 없으면 null로 채워.

분석할 텍스트:
```

**응답 예시:**
```json
{
  "출발지": "중도",
  "목적지": "전정대",
  "목표": "배달",
  "물건": "전공책",
  "기타": null
}
```

### 4-3. `responseMimeType` 옵션으로 JSON 강제

Gemini API는 `generationConfig`에 `responseMimeType: "application/json"` 옵션을 주면
프롬프트와 관계없이 **항상 JSON 형식으로만** 응답하도록 강제할 수 있다.

```java
"generationConfig": {
    "responseMimeType": "application/json"
}
```

이 옵션 + 좋은 프롬프트 = JSON 파싱 실패 거의 없음

---

## 5. Spring에서 Gemini API 호출하는 법

### 5-1. 필요한 의존성 (build.gradle)

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // Jackson (JSON 파싱) - Spring Boot에 기본 포함
    implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

별도 Gemini SDK 없이 **기본 RestTemplate**으로 HTTP 요청만 보내면 됨.

### 5-2. application.properties 설정

```properties
# Gemini API 설정
gemini.api.key=${GEMINI_API_KEY}  # 환경변수로 관리 (보안)
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent
```

### 5-3. Gemini API 요청/응답 구조

**요청 (Spring → Gemini):**
```json
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={API_KEY}
Content-Type: application/json

{
  "contents": [
    {
      "parts": [
        {
          "text": "프롬프트 + 사용자 텍스트"
        }
      ]
    }
  ],
  "generationConfig": {
    "responseMimeType": "application/json"
  }
}
```

**응답 (Gemini → Spring):**
```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "{\"출발지\": \"중도\", \"목적지\": \"전정대\", \"목표\": \"배달\", \"물건\": \"전공책\", \"기타\": null}"
          }
        ],
        "role": "model"
      },
      "finishReason": "STOP"
    }
  ],
  "usageMetadata": {
    "promptTokenCount": 150,
    "candidatesTokenCount": 30
  }
}
```

응답에서 꺼내야 할 경로:
```
response.candidates[0].content.parts[0].text
```
이 값이 우리가 원하는 JSON 문자열.

### 5-4. GeminiService 전체 코드

```java
@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 프롬프트 상수로 분리 (수정 편의)
    private static final String PROMPT_TEMPLATE = """
            너는 심부름/배달 요청 텍스트를 분석하는 도우미야.
            사용자의 입력에서 아래 정보를 추출해서 반드시 JSON 형식으로만 답해줘.
            다른 설명 텍스트는 절대 포함하지 마.

            추출할 정보:
            - 출발지: 물건이 현재 있는 장소 (없으면 null)
            - 목적지: 물건을 가져다줄 장소 (없으면 null)
            - 목표: 요청의 종류. 반드시 "배달", "구매", "기타" 중 하나
            - 물건: 심부름 대상 물건 (없으면 null)
            - 기타: 그 외 특이사항 (없으면 null)

            분석할 텍스트:
            """;

    public PostTagDto analyzePostContent(String content) throws Exception {
        String fullPrompt = PROMPT_TEMPLATE + content;

        // Gemini API 요청 바디 구성
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", fullPrompt)))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json"  // JSON 응답 강제
            )
        );

        // HTTP 헤더
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // API Key는 URL 파라미터로 전달 (Gemini 방식)
        String urlWithKey = apiUrl + "?key=" + apiKey;

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(
            urlWithKey, entity, Map.class
        );

        // 응답에서 JSON 텍스트 추출
        // 경로: candidates[0].content.parts[0].text
        List<Map> candidates = (List<Map>) response.getBody().get("candidates");
        Map content0 = (Map) candidates.get(0).get("content");
        List<Map> parts = (List<Map>) content0.get("parts");
        String jsonText = (String) parts.get(0).get("text");

        // JSON 문자열 → PostTagDto 변환
        return objectMapper.readValue(jsonText, PostTagDto.class);
    }
}
```

### 5-5. PostTagDto (AI 분석 결과 DTO)

```java
public class PostTagDto {

    @JsonProperty("출발지")
    private String departure;    // 출발지

    @JsonProperty("목적지")
    private String destination;  // 목적지

    @JsonProperty("목표")
    private String goal;         // 목표 (배달/구매/기타)

    @JsonProperty("물건")
    private String item;         // 물건

    @JsonProperty("기타")
    private String etc;          // 기타

    // Getter / Setter 생략 (Lombok @Data 사용 시 불필요)
}
```

`@JsonProperty("출발지")` 어노테이션 덕분에
Gemini가 한국어 키로 응답해도 Java 객체로 자동 변환된다.

---

## 6. 게시물 작성 흐름에 AI 분석 통합

명세서 기준: `POST /api/posts` 에서 게시물 저장 시 **자동으로** AI 분석이 실행된다.

### 6-1. 전체 서비스 흐름

```
PostController.createPost(PostCreateReqDto)
    │
    ├─ ① PostCreateReqDto에서 content(텍스트) 꺼냄
    │
    ├─ ② GeminiService.analyzePostContent(content) 호출
    │      → Gemini API 호출
    │      → PostTagDto 반환 (출발지, 목적지, 목표, 물건)
    │
    ├─ ③ Post 객체 생성 (content + 태그 포함)
    │
    ├─ ④ Firestore에 저장
    │      - Posts 컬렉션에 문서 추가
    │      - Users.myPosts 배열에 postId 추가
    │
    └─ ⑤ postId 반환
```

### 6-2. PostCreateReqDto (게시물 작성 요청 DTO)

```java
public class PostCreateReqDto {
    private String content;    // 사용자가 입력한 원문 텍스트
    private String userId;     // 작성자 ID
    // 필요 시 추가 필드
}
```

### 6-3. PostService 예시

```java
@Service
public class PostService {

    @Autowired
    private GeminiService geminiService;

    // Firestore 관련 의존성도 여기 주입 (백엔드팀 구현 부분)

    public String createPost(PostCreateReqDto reqDto) throws Exception {
        // ① AI 분석 실행
        PostTagDto tags = geminiService.analyzePostContent(reqDto.getContent());

        // ② Post 객체 구성 (Firestore 저장용)
        Map<String, Object> postData = new HashMap<>();
        postData.put("content", reqDto.getContent());      // 원문
        postData.put("userId", reqDto.getUserId());        // 작성자
        postData.put("departure", tags.getDeparture());    // 출발지
        postData.put("destination", tags.getDestination()); // 목적지
        postData.put("goal", tags.getGoal());              // 목표
        postData.put("item", tags.getItem());              // 물건
        postData.put("isAccepted", false);                 // 수락 여부 초기값
        postData.put("createdAt", System.currentTimeMillis());

        // ③ Firestore 저장 (백엔드팀 구현 부분)
        // String postId = firestoreService.save("posts", postData);
        // firestoreService.addToArray("users", reqDto.getUserId(), "myPosts", postId);

        // return postId;
        return "postId_임시값";
    }
}
```

### 6-4. PostController 예시

```java
@RestController
@RequestMapping("/api")
public class PostController {

    @Autowired
    private PostService postService;

    @PostMapping("/posts")
    public ResponseEntity<String> createPost(
            @RequestBody PostCreateReqDto reqDto,
            @RequestHeader("Authorization") String token  // JWT 토큰
    ) {
        try {
            // TODO: JWT 토큰 검증 (백엔드팀 구현 부분)
            String postId = postService.createPost(reqDto);
            return ResponseEntity.ok(postId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("게시물 작성 실패: " + e.getMessage());
        }
    }
}
```

---

## 7. API 키 보안 관리

**절대 API 키를 코드에 직접 쓰거나 GitHub에 올리면 안 됨!**
구글이 자동으로 감지하고 키를 즉시 비활성화함.

### 방법 1: 환경변수 사용 (권장)

`application.properties`:
```properties
gemini.api.key=${GEMINI_API_KEY}
```

서버 실행 시:
```bash
# 환경변수 설정 후 실행
export GEMINI_API_KEY=AIzaSy...
./gradlew bootRun
```

IntelliJ에서는 Run Configuration → Environment Variables에 입력.

### 방법 2: application-local.properties 분리

`application-local.properties` (이 파일만 .gitignore 추가):
```properties
gemini.api.key=AIzaSy...실제키값
```

`application.properties`:
```properties
spring.profiles.active=local
```

`.gitignore`에 추가:
```
application-local.properties
```

### 방법 3: 개발 중 임시로 직접 입력 (테스트 용도만)

```properties
gemini.api.key=AIzaSy...실제키값
```
단, 커밋 전에 반드시 제거할 것.

---

## 8. 예외 처리 방법

### 8-1. Gemini API 호출 실패

```java
try {
    PostTagDto tags = geminiService.analyzePostContent(content);
} catch (HttpClientErrorException e) {
    // 4xx 오류: API 키 잘못됨, 요청 형식 오류 등
    // e.getStatusCode(), e.getResponseBodyAsString() 로 원인 확인
    throw new RuntimeException("Gemini API 호출 오류: " + e.getMessage());
} catch (HttpServerErrorException e) {
    // 5xx 오류: Gemini 서버 문제
    throw new RuntimeException("Gemini 서버 오류, 잠시 후 재시도");
}
```

### 8-2. JSON 파싱 실패 (AI가 JSON이 아닌 텍스트로 응답했을 때)

```java
try {
    return objectMapper.readValue(jsonText, PostTagDto.class);
} catch (JsonProcessingException e) {
    // JSON 파싱 실패 시 빈 태그로 대체
    return new PostTagDto();  // 모든 필드가 null인 기본 객체 반환
}
```

### 8-3. 타임아웃 설정

Gemini API가 응답이 느릴 때를 대비:
```java
// RestTemplate에 타임아웃 설정
@Bean
public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);   // 연결 5초
    factory.setReadTimeout(10000);     // 응답 10초
    return new RestTemplate(factory);
}
```

---

## 9. 테스트 방법

### 9-1. curl로 직접 Gemini API 테스트 (Spring 없이)

```bash
curl -X POST \
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=발급받은_키" \
  -H "Content-Type: application/json" \
  -d '{
    "contents": [{
      "parts": [{
        "text": "너는 심부름 요청 텍스트 분석 도우미야. JSON으로만 답해. 출발지, 목적지, 목표, 물건 추출.\n\n분석: 중도에 전공책 두고 왔는데 전정대로 가져다 주실분…"
      }]
    }],
    "generationConfig": {
      "responseMimeType": "application/json"
    }
  }'
```

### 9-2. Spring 서버 실행 후 Postman / curl 테스트

```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 테스트용_토큰" \
  -d '{
    "content": "중도에 전공책 두고 왔는데 전정대로 가져다 주실분…",
    "userId": "user123"
  }'
```

**예상 응답:**
```json
"postId_생성된값"
```

### 9-3. 다양한 입력으로 프롬프트 검증

```
입력: "편의점에서 삼각김밥 사다줄사람"
기대: { "출발지": null, "목적지": null, "목표": "구매", "물건": "삼각김밥", "기타": null }

입력: "도서관 3층에 충전기 놓고 나왔어요 공대 앞으로 가져다주실 분"
기대: { "출발지": "도서관 3층", "목적지": "공대 앞", "목표": "배달", "물건": "충전기", "기타": null }
```

---

## 10. 요약 및 구현 순서

### 백엔드팀이 구현해야 할 순서

```
Step 1. Gemini API 키 발급 (Google AI Studio)
        ↓
Step 2. application.properties에 키 입력
        ↓
Step 3. GeminiService 구현 (Gemini 호출 + JSON 파싱)
        ↓
Step 4. PostTagDto 구현 (@JsonProperty로 한국어 키 매핑)
        ↓
Step 5. PostService에서 GeminiService 호출
        (PostCreateReqDto.content → analyzePostContent → PostTagDto)
        ↓
Step 6. 태그 포함해서 Firestore에 저장
        ↓
Step 7. curl / Postman으로 테스트
```

### 파일 구조 제안

```
src/main/java/com/프로젝트명/
├── controller/
│   └── PostController.java         ← POST /api/posts
├── service/
│   ├── PostService.java            ← 게시물 저장 로직
│   └── GeminiService.java          ← Gemini API 호출 로직
└── dto/
    ├── PostCreateReqDto.java       ← 게시물 작성 요청
    ├── PostTagDto.java             ← AI 분석 결과 (출발지, 목적지 등)
    └── PostResDto.java             ← 게시물 응답
```

### 핵심 요약 (한 줄씩)

1. **Gemini API** = Google에서 만든 AI. 무료 티어 있어서 해커톤에 최적
2. **Prompt Engineering** = AI한테 "무조건 JSON으로만 답해"라고 강제하는 기술
3. **RestTemplate** = Spring에서 외부 HTTP API 호출할 때 쓰는 도구
4. **@JsonProperty** = AI가 한국어로 응답해도 Java 객체로 자동 변환해주는 어노테이션
5. **게시물 작성 시 자동 분석** = Flutter는 텍스트만 보내면, Spring이 알아서 Gemini 호출 후 태그 포함해서 DB 저장

---

## 참고 링크

| 항목 | 링크 |
|------|------|
| Gemini API 공식 문서 | https://ai.google.dev/gemini-api/docs |
| Google AI Studio (키 발급) | https://aistudio.google.com |
| Gemini 모델 목록 | https://ai.google.dev/gemini-api/docs/models/gemini |
| Spring RestTemplate 공식 문서 | https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/RestTemplate.html |
| Jackson @JsonProperty | https://www.baeldung.com/jackson-annotations |
