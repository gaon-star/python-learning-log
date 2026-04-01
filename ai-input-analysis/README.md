# AI API를 활용한 사용자 Input 분석 - 조사 문서

## 개요

사용자의 자연어 입력을 AI API(GPT / Gemini)로 분석해서
구조화된 태그(출발지, 목적지, 목표 등)를 추출하는 기능을 Spring 서버에서 구현하는 방법.

---

## 핵심 아이디어: Prompt Engineering (프롬프트 설계)

AI API는 "어떻게 물어보냐"에 따라 결과가 완전히 달라짐.
원하는 형식(JSON)으로 답변을 강제하는 **System Prompt**를 잘 짜는게 핵심.

### 예시 System Prompt
```
너는 심부름/배달 요청 텍스트를 분석하는 도우미야.
사용자의 입력을 분석해서 반드시 아래 JSON 형식으로만 답해줘.
{
  "출발지": "...",
  "목적지": "...",
  "목표": "...",
  "물건": "...",
  "기타": "..."
}
값을 알 수 없으면 null로 채워. JSON 외에 다른 텍스트는 절대 포함하지 마.
```

### 예시 입력/출력
- **Input**: "중도에 전공책 두고 왔는데 전정대로 가져다 주실분…"
- **Output**:
```json
{
  "출발지": "중도",
  "목적지": "전정대",
  "목표": "배달",
  "물건": "전공책",
  "기타": null
}
```

---

## 구현 방법 (Spring Boot 기준)

### 전체 흐름

```
클라이언트(앱/웹)
    ↓  POST /api/analyze  { "text": "중도에 전공책 두고 왔는데..." }
Spring 서버 (Controller → Service)
    ↓  HTTP 요청 (RestTemplate 또는 WebClient)
AI API (OpenAI GPT-4o / Google Gemini)
    ↓  JSON 응답
Spring 서버
    ↓  파싱 후 반환
클라이언트
```

### 사용 가능한 AI API 비교

| 항목 | OpenAI GPT-4o | Google Gemini 1.5 Flash |
|------|--------------|------------------------|
| 성능 | 매우 좋음 | 좋음 |
| 속도 | 빠름 | 매우 빠름 |
| 무료 크레딧 | $5 (신규) | 무료 티어 있음 |
| JSON 강제 출력 | `response_format` 옵션 지원 | System Prompt로 가능 |
| 추천 상황 | 정확도 중요할 때 | 빠르게 프로토타입 만들 때 |

**해커톤 추천: Gemini 1.5 Flash** → 무료 티어가 있어서 API 키만 발급하면 바로 사용 가능

---

## Option 1: OpenAI GPT API 연동

### 1. 의존성 추가 (build.gradle)
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

### 2. application.properties
```properties
openai.api.key=sk-여기에_발급받은_키_입력
openai.api.url=https://api.openai.com/v1/chat/completions
```

### 3. DTO 클래스들
```java
// 요청 DTO
public class AnalyzeRequest {
    private String text;
    // getter/setter
}

// 응답 DTO
public class AnalyzeResponse {
    private String departure;   // 출발지
    private String destination; // 목적지
    private String goal;        // 목표
    private String item;        // 물건
    private String etc;         // 기타
    // getter/setter
}
```

### 4. OpenAI Service
```java
@Service
public class OpenAiService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyzeResponse analyzeInput(String userText) throws Exception {
        // 요청 body 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini"); // 저렴한 모델
        requestBody.put("response_format", Map.of("type", "json_object")); // JSON 강제

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
            "role", "system",
            "content", """
                너는 심부름/배달 요청 텍스트를 분석하는 도우미야.
                사용자의 입력을 분석해서 반드시 아래 JSON 형식으로만 답해줘.
                {
                  "출발지": "...",
                  "목적지": "...",
                  "목표": "...",
                  "물건": "...",
                  "기타": "..."
                }
                값을 알 수 없으면 null로 채워. JSON 외에 다른 텍스트는 절대 포함하지 마.
                """
        ));
        messages.add(Map.of("role", "user", "content", userText));
        requestBody.put("messages", messages);

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        // 응답 파싱
        List<Map> choices = (List<Map>) response.getBody().get("choices");
        Map message = (Map) choices.get(0).get("message");
        String content = (String) message.get("content");

        // JSON 문자열 → AnalyzeResponse 변환
        Map<String, String> parsed = objectMapper.readValue(content, Map.class);
        AnalyzeResponse result = new AnalyzeResponse();
        result.setDeparture(parsed.get("출발지"));
        result.setDestination(parsed.get("목적지"));
        result.setGoal(parsed.get("목표"));
        result.setItem(parsed.get("물건"));
        result.setEtc(parsed.get("기타"));
        return result;
    }
}
```

### 5. Controller
```java
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    @Autowired
    private OpenAiService openAiService;

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody AnalyzeRequest request) {
        try {
            AnalyzeResponse response = openAiService.analyzeInput(request.getText());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

---

## Option 2: Google Gemini API 연동 (해커톤 추천)

### 1. API 키 발급
1. https://aistudio.google.com 접속
2. "Get API Key" 클릭 → 무료로 발급 가능

### 2. application.properties
```properties
gemini.api.key=여기에_발급받은_키_입력
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent
```

### 3. Gemini Service
```java
@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyzeResponse analyzeInput(String userText) throws Exception {
        String prompt = """
                너는 심부름/배달 요청 텍스트를 분석하는 도우미야.
                사용자의 입력을 분석해서 반드시 아래 JSON 형식으로만 답해줘.
                {
                  "출발지": "...",
                  "목적지": "...",
                  "목표": "...",
                  "물건": "...",
                  "기타": "..."
                }
                값을 알 수 없으면 null로 채워. JSON 외에 다른 텍스트는 절대 포함하지 마.
                
                분석할 텍스트: """ + userText;

        // Gemini API 요청 형식
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json" // JSON 응답 강제
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String urlWithKey = apiUrl + "?key=" + apiKey;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(urlWithKey, entity, Map.class);

        // 응답 파싱
        List<Map> candidates = (List<Map>) response.getBody().get("candidates");
        Map content = (Map) candidates.get(0).get("content");
        List<Map> parts = (List<Map>) content.get("parts");
        String jsonText = (String) parts.get(0).get("text");

        Map<String, String> parsed = objectMapper.readValue(jsonText, Map.class);
        AnalyzeResponse result = new AnalyzeResponse();
        result.setDeparture(parsed.get("출발지"));
        result.setDestination(parsed.get("목적지"));
        result.setGoal(parsed.get("목표"));
        result.setItem(parsed.get("물건"));
        result.setEtc(parsed.get("기타"));
        return result;
    }
}
```

---

## API 호출 테스트 (curl)

서버 실행 후 터미널에서 테스트:
```bash
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{"text": "중도에 전공책 두고 왔는데 전정대로 가져다 주실분…"}'
```

예상 응답:
```json
{
  "departure": "중도",
  "destination": "전정대",
  "goal": "배달",
  "item": "전공책",
  "etc": null
}
```

---

## 주의사항 / 팁

1. **API 키는 절대 GitHub에 올리지 마세요!**
   - `application.properties`를 `.gitignore`에 추가하거나
   - 환경변수로 관리: `${GEMINI_API_KEY}`

2. **Gemini 무료 티어 제한**: 분당 15회 요청 → 해커톤에서는 충분

3. **응답이 JSON이 아닌 경우 대비**: try-catch로 파싱 실패 처리 필요

4. **Spring Boot 버전**: 3.x 기준으로 작성됨 (2.x도 거의 동일)

---

## 참고 링크

- OpenAI API 문서: https://platform.openai.com/docs/api-reference/chat
- Gemini API 문서: https://ai.google.dev/gemini-api/docs
- Google AI Studio (키 발급): https://aistudio.google.com
