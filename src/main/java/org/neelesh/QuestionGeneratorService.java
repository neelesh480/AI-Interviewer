package org.neelesh;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionGeneratorService {

    @Value("${google.gemini.key}")
    private String apiKey;

    @Value("${google.gemini.model}")
    private String modelName;

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    
    // Increased default delay to 10 seconds to be very safe
    private static final long DEFAULT_DELAY_MS = 10000; 
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final BlockingQueue<RequestTask> queue = new LinkedBlockingQueue<>();

    @PostConstruct
    public void startProcessing() {
        executorService.submit(this::processQueue);
    }

    @PreDestroy
    public void stopProcessing() {
        executorService.shutdownNow();
    }

    private static class RequestTask {
        String cvText;
        String experienceLevel;
        String questionType;
        List<String> selectedSkills;
        CompletableFuture<String> future;

        public RequestTask(String cvText, String experienceLevel, String questionType, List<String> selectedSkills, CompletableFuture<String> future) {
            this.cvText = cvText;
            this.experienceLevel = experienceLevel;
            this.questionType = questionType;
            this.selectedSkills = selectedSkills;
            this.future = future;
        }
    }

    public Future<String> queueQuestionGeneration(String cvText, String experienceLevel, String questionType, List<String> selectedSkills) {
        CompletableFuture<String> future = new CompletableFuture<>();
        queue.offer(new RequestTask(cvText, experienceLevel, questionType, selectedSkills, future));
        return future;
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                RequestTask task = queue.take();
                
                String result = generateQuestionsWithRetry(task.cvText, task.experienceLevel, task.questionType, task.selectedSkills);
                task.future.complete(result);

                // Always wait a bit between successful requests too
                Thread.sleep(DEFAULT_DELAY_MS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String generateQuestionsWithRetry(String cvText, String experienceLevel, String questionType, List<String> selectedSkills) {
        int maxRetries = 5; // Increased retries
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                return generateQuestionsInternal(cvText, experienceLevel, questionType, selectedSkills);
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempt++;
                long waitTime = parseRetryAfter(e.getMessage());
                System.out.println("Rate limit hit (429). Waiting " + waitTime + "ms before retry " + attempt + "/" + maxRetries);
                
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "Error: Interrupted during retry wait.";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Error generating questions: " + e.getMessage();
            }
        }
        return "Error: Failed to generate questions after retries due to rate limits.";
    }

    private long parseRetryAfter(String errorMessage) {
        // Try to find "Please retry in X s"
        try {
            Pattern pattern = Pattern.compile("retry in ([0-9.]+)s");
            Matcher matcher = pattern.matcher(errorMessage);
            if (matcher.find()) {
                double seconds = Double.parseDouble(matcher.group(1));
                return (long) (seconds * 1000) + 1000; // Add 1s buffer
            }
        } catch (Exception e) {
            // ignore parsing errors
        }
        return 30000; // Default to 30s if we can't parse it
    }

    private String generateQuestionsInternal(String cvText, String experienceLevel, String questionType, List<String> selectedSkills) {
        String prompt = createPrompt(cvText, experienceLevel, questionType, selectedSkills);
        RestTemplate restTemplate = new RestTemplate();

        Map<String, String> part = new HashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", Collections.singletonList(part));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", Collections.singletonList(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        String trimmedKey = apiKey.trim();
        String cleanModelName = modelName.replace("models/", "");
        String url = GEMINI_API_BASE_URL + cleanModelName + ":generateContent?key=" + trimmedKey;
        
        System.out.println("Processing request for model: " + cleanModelName);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        return extractTextFromResponse(response.getBody());
    }

    private String createPrompt(String cvText, String experienceLevel, String questionType, List<String> selectedSkills) {
        // Aggressively truncate CV text to 4000 chars to stay under token limits
        String truncatedCvText = cvText;
        if (cvText != null && cvText.length() > 4000) {
            truncatedCvText = cvText.substring(0, 4000) + "... [truncated]";
        }
        
        String skillsPrompt = "";
        if (selectedSkills != null && !selectedSkills.isEmpty()) {
            skillsPrompt = "Focus ONLY on the following technical skills: " + String.join(", ", selectedSkills) + ". ";
        } else {
            skillsPrompt = "Focus on the skills mentioned in the CV. ";
        }

        String typePrompt = "";
        if ("Programming".equalsIgnoreCase(questionType)) {
            typePrompt = "Generate ONLY practical coding/programming questions. ";
        } else if ("Theoretical".equalsIgnoreCase(questionType)) {
            typePrompt = "Generate ONLY theoretical/conceptual questions. ";
        } else {
            typePrompt = "Generate a mix of theoretical and practical questions. ";
        }
        
        return "Generate 10 technical interview questions for a " + experienceLevel +
                " candidate. " + skillsPrompt + typePrompt +
                "Use the candidate's CV context where relevant: " + truncatedCvText;
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map<String, Object> responseBody) {
        try {
            if (responseBody == null) return "Empty response from API";

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.get(0);
                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                if (content != null) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error parsing response from Gemini: " + e.getMessage();
        }
        return "No questions generated.";
    }
}