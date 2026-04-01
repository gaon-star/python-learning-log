package com.hackathon.aianalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.aianalysis.dto.AnalyzeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
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
        // 요청 바디 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("response_format", Map.of("type", "json_object")); // JSON 응답 강제

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userText));
        requestBody.put("messages", messages);

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);  // Authorization: Bearer {apiKey}

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // OpenAI API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        // 응답에서 JSON 텍스트 추출
        List<Map> choices = (List<Map>) response.getBody().get("choices");
        Map message = (Map) choices.get(0).get("message");
        String content = (String) message.get("content");

        // JSON 문자열 → AnalyzeResponse 변환
        return objectMapper.readValue(content, AnalyzeResponse.class);
    }
}
