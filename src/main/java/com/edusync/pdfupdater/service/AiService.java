package com.edusync.pdfupdater.service;

import com.edusync.pdfupdater.model.UpdateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiService {

    private static final String FACT_CHECK_PROMPT =
            "You are a factual verifier AI. Analyze the following text.\n" +
            "Your task is to identify any factually incorrect or outdated information/sentences, and provide the latest factually correct updated sentence along with the source.\n" +
            "Return a JSON object with a single key 'updates' containing an array of objects. " +
            "Each object must have exactly three fields:\n" +
            "1. 'originalSentence': the exact outdated/incorrect sentence from the text (MUST be an exact substring match).\n" +
            "2. 'updatedSentence': the new factual sentence replacing it.\n" +
            "3. 'source': a short citation or URL for the new fact.\n" +
            "DO NOT hallucinate. Only include sentences that are actually factually incorrect or outdated. If nothing is incorrect, return { \"updates\": [] }.\n" +
            "Ensure the output is ONLY valid JSON, with no markdown formatting like ```json\n" +
            "\nText to analyze:\n";

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${ai.chunk.size:3500}")
    private int maxChunk;

    @Value("${ai.groq.model:mixtral-8x7b-32768}")
    private String groqModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public List<UpdateResponse.SentenceUpdate> getOutdatedSentences(String fullText) {
        if (fullText == null || fullText.trim().length() < 10) {
            log.warn("Text extracted from PDF is too short or empty. Skipping AI fact-check.");
            return Collections.emptyList();
        }

        List<String> chunks = new java.util.ArrayList<>();
        int currentStart = 0;

        while (currentStart < fullText.length()) {
            int currentEnd = Math.min(currentStart + maxChunk, fullText.length());
            
            // Try to avoid breaking sentences/paragraphs by looking back for a safe split point
            if (currentEnd < fullText.length()) {
                int lastPeriod = fullText.lastIndexOf('.', currentEnd);
                int lastNewline = fullText.lastIndexOf('\n', currentEnd);
                int splitPoint = Math.max(lastPeriod, lastNewline);
                if (splitPoint > currentStart + (maxChunk / 2)) {
                    currentEnd = splitPoint + 1;
                }
            }

            chunks.add(fullText.substring(currentStart, currentEnd));
            currentStart = currentEnd;
        }

        log.info("Split PDF text into " + chunks.size() + " chunks for parallel AI processing.");

        List<UpdateResponse.SentenceUpdate> allUpdates = chunks.parallelStream()
                .map(chunk -> {
                    log.info("Parallel executing chunk (length " + chunk.length() + ")");
                    return getOutdatedSentencesForChunk(chunk);
                })
                .filter(java.util.Objects::nonNull)
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toList());

        return allUpdates;
    }

    private List<UpdateResponse.SentenceUpdate> getOutdatedSentencesForChunk(String text) {
        // Strategy: Try Groq first (fast + reliable), then Gemini as fallback
        
        // 1. Try Groq (if API key is configured)
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            log.info("Trying Groq API (primary)...");
            List<UpdateResponse.SentenceUpdate> result = callGroq(text);
            if (result != null) {
                return result;
            }
            log.warn("Groq failed, falling back to Gemini...");
        } else {
            log.info("No Groq API key configured, using Gemini only.");
        }

        // 2. Try Gemini models
        String[][] geminiModels = {
                {"v1beta", "gemini-3.5-flash-lite"},
                {"v1beta", "gemini-3.8-flash"},
                {"v1beta", "gemini-3.5-flash"}
        };

        for (String[] config : geminiModels) {
            List<UpdateResponse.SentenceUpdate> result = callGemini(text, config[0], config[1]);
            if (result != null) {
                return result;
            }
            log.info("Gemini model " + config[1] + " failed, trying next...");
        }

        log.error("ALL AI providers failed.");
        return Collections.emptyList();
    }

    // ===================== GROQ API (OpenAI-compatible) =====================

    private List<UpdateResponse.SentenceUpdate> callGroq(String text) {
        try {
            // Groq uses OpenAI-compatible chat completions API
            Map<String, Object> requestBody = Map.of(
                    "model", groqModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a factual verifier. Respond ONLY in valid JSON."),
                            Map.of("role", "user", "content", FACT_CHECK_PROMPT + text)
                    ),
                    "temperature", 0.2,
                    "max_tokens", 2048,
                    "response_format", Map.of("type", "json_object")
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            for (int attempt = 1; attempt <= 2; attempt++) {
                log.info("Calling Groq API (llama-3.3-70b, attempt " + attempt + "/2)");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + groqApiKey)
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    log.info("SUCCESS! Groq API responded.");
                    return parseGroqResponse(response.body());
                }

                if ((response.statusCode() == 429 || response.statusCode() == 503) && attempt < 2) {
                    log.warn("Groq returned " + response.statusCode() + ", retrying in 3s...");
                    Thread.sleep(3000);
                    continue;
                }

                log.error("Groq API error (status=" + response.statusCode() + "): "
                        + response.body().substring(0, Math.min(300, response.body().length())));
                return null;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Groq API call failed: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<UpdateResponse.SentenceUpdate> parseGroqResponse(String responseBody) {
        try {
            Map<String, Object> json = objectMapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");
                content = content.replace("```json", "").replace("```", "").trim();
                log.info("Groq AI response: " + content.substring(0, Math.min(300, content.length())));
                UpdateResponse updateResponse = objectMapper.readValue(content, UpdateResponse.class);
                List<UpdateResponse.SentenceUpdate> updates = updateResponse.getUpdates();
                log.info("Groq found " + (updates != null ? updates.size() : 0) + " updates");
                return updates != null ? updates : Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to parse Groq response: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    // ===================== GEMINI API =====================

    private List<UpdateResponse.SentenceUpdate> callGemini(String text, String apiVersion, String model) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", FACT_CHECK_PROMPT + text)
                            ))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "maxOutputTokens", 2048,
                            "responseMimeType", "application/json"
                    )
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String url = "https://generativelanguage.googleapis.com/" + apiVersion + "/models/" + model + ":generateContent?key=" + geminiApiKey;

            for (int attempt = 1; attempt <= 2; attempt++) {
                log.info("Calling Gemini API (" + model + ", attempt " + attempt + "/2)");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(90))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    log.info("SUCCESS! Gemini responded (model=" + model + ")");
                    return parseGeminiResponse(response.body());
                }

                if (response.statusCode() == 404) {
                    log.warn("Model " + model + " not available (404). Skipping.");
                    return null;
                }

                if ((response.statusCode() == 429 || response.statusCode() == 503) && attempt < 2) {
                    log.warn("Gemini returned " + response.statusCode() + " for " + model + ", retrying in 5s...");
                    Thread.sleep(5000);
                    continue;
                }

                log.error("Gemini error (model=" + model + ", status=" + response.statusCode() + ")");
                return null;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Gemini call failed (model=" + model + "): " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<UpdateResponse.SentenceUpdate> parseGeminiResponse(String responseBody) {
        try {
            Map<String, Object> jsonNode = objectMapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) jsonNode.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    String aiText = (String) parts.get(0).get("text");
                    aiText = aiText.replace("```json", "").replace("```", "").trim();
                    log.info("Gemini AI response: " + aiText.substring(0, Math.min(300, aiText.length())));
                    UpdateResponse updateResponse = objectMapper.readValue(aiText, UpdateResponse.class);
                    List<UpdateResponse.SentenceUpdate> updates = updateResponse.getUpdates();
                    log.info("Gemini found " + (updates != null ? updates.size() : 0) + " updates");
                    return updates != null ? updates : Collections.emptyList();
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: " + e.getMessage());
        }
        return Collections.emptyList();
    }
}
