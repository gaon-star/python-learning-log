package com.hackathon.aianalysis.controller;

import com.hackathon.aianalysis.dto.AnalyzeRequest;
import com.hackathon.aianalysis.dto.AnalyzeResponse;
import com.hackathon.aianalysis.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // 프론트엔드에서 호출할 수 있도록 CORS 허용
public class AnalyzeController {

    @Autowired
    private GeminiService geminiService;

    /**
     * 사용자 텍스트 분석 API
     *
     * 요청: POST /api/analyze
     * Body: { "text": "중도에 전공책 두고 왔는데 전정대로 가져다 주실분…" }
     *
     * 응답: { "출발지": "중도", "목적지": "전정대", "목표": "배달", "물건": "전공책", "기타": null }
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody AnalyzeRequest request) {
        try {
            AnalyzeResponse response = geminiService.analyzeInput(request.getText());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 실패 시 500 반환
            return ResponseEntity.internalServerError().build();
        }
    }
}
