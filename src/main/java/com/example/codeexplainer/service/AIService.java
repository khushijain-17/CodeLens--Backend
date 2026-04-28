package com.example.codeexplainer.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AIService {

    @Value("${groq.api.key}")
    private String API_KEY;

    public String explainCode(String code) {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        RestTemplate restTemplate = new RestTemplate();

        // Build request body
        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("max_tokens", 1024);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", "Explain this code in simple terms:\n" + code);
        messages.add(msg);
        request.put("messages", messages);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(API_KEY); // Groq uses Bearer token

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        // Parse response
        Map<?, ?> body = response.getBody();
        if (body == null) return "Error: empty response from Groq";

        List<?> choices = (List<?>) body.get("choices");
        if (choices == null || choices.isEmpty()) return "Error: no choices in response";

        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        if (message == null) return "Error: no message in response";

        return message.get("content").toString();
    }
}