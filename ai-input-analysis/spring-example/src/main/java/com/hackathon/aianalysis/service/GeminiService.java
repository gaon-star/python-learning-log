package com.hackathon.aianalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.aianalysis.dto.AnalyzeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
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
            """;

    public AnalyzeResponse analyzeInput(String userText) throws Exception {
        String fullPrompt = SYSTEM_PROMPT + "\n\n분석할 텍스트: " + userText;

        // Gemini API 요청 바디 구성
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", fullPrompt)))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json"  // JSON 응답 강제
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String urlWithKey = apiUrl + "?key=" + apiKey;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Gemini API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(urlWithKey, entity, Map.class);

        // 응답에서 JSON 텍스트 추출
        List<Map> candidates = (List<Map>) response.getBody().get("candidates");
        Map content = (Map) candidates.get(0).get("content");
        List<Map> parts = (List<Map>) content.get("parts");
        String jsonText = (String) parts.get(0).get("text");

        // JSON 문자열 → AnalyzeResponse 변환
        return objectMapper.readValue(jsonText, AnalyzeResponse.class);
    }
}
