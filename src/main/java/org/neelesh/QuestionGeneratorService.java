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

    private enum TaskType {
        QUESTION_GENERATION,
        CODE_ANALYSIS
    }

    private static class RequestTask {
        TaskType type;
        // Fields for Question Generation
        String cvText;
        String experienceLevel;
        String questionType;
        List<String> selectedSkills;
        String jobDescription; // New field for JD

        // Fields for Code Analysis
        String code;

        CompletableFuture<String> future;

        // Constructor for Question Generation
        public RequestTask(String cvText, String experienceLevel, String questionType, List<String> selectedSkills, String jobDescription, CompletableFuture<String> future) {
            this.type = TaskType.QUESTION_GENERATION;
            this.cvText = cvText;
            this.experienceLevel = experienceLevel;
            this.questionType = questionType;
            this.selectedSkills = selectedSkills;
            this.jobDescription = jobDescription;
            this.future = future;
        }

        // Constructor for Code Analysis
        public RequestTask(String code, CompletableFuture<String> future) {
            this.type = TaskType.CODE_ANALYSIS;
            this.code = code;
            this.future = future;
        }
    }

    public Future<String> queueQuestionGeneration(String cvText, String experienceLevel, String questionType, List<String> selectedSkills, String jobDescription) {
        CompletableFuture<String> future = new CompletableFuture<>();
        queue.offer(new RequestTask(cvText, experienceLevel, questionType, selectedSkills, jobDescription, future));
        return future;
    }

    public Future<String> queueCodeAnalysis(String code) {
        CompletableFuture<String> future = new CompletableFuture<>();
        queue.offer(new RequestTask(code, future));
        return future;
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            RequestTask task = null;
            try {
                task = queue.take();
                
                String result = "";
                if (task.type == TaskType.QUESTION_GENERATION) {
                    result = generateQuestionsWithRetry(task);
                } else if (task.type == TaskType.CODE_ANALYSIS) {
                    result = analyzeCodeWithRetry(task);
                }

                task.future.complete(result);

                Thread.sleep(DEFAULT_DELAY_MS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
                if (task != null && task.future != null) {
                    task.future.completeExceptionally(e);
                }
            }
        }
    }

    private String generateQuestionsWithRetry(RequestTask task) {
        return executeWithRetry(() -> generateQuestionsInternal(task.cvText, task.experienceLevel, task.questionType, task.selectedSkills, task.jobDescription));
    }

    private String analyzeCodeWithRetry(RequestTask task) {
        return executeWithRetry(() -> analyzeCodeInternal(task.code));
    }

    private String executeWithRetry(Callable<String> action) {
        int maxRetries = 5;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                return action.call();
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
                return "Error: " + e.getMessage();
            }
        }
        return "Error: Failed after retries due to rate limits.";
    }

    private long parseRetryAfter(String errorMessage) {
        try {
            Pattern pattern = Pattern.compile("retry in ([0-9.]+)s");
            Matcher matcher = pattern.matcher(errorMessage);
            if (matcher.find()) {
                double seconds = Double.parseDouble(matcher.group(1));
                return (long) (seconds * 1000) + 1000;
            }
        } catch (Exception e) {
            // ignore
        }
        return 30000;
    }

    private String generateQuestionsInternal(String cvText, String experienceLevel, String questionType, List<String> selectedSkills, String jobDescription) {
        String prompt = createPrompt(cvText, experienceLevel, questionType, selectedSkills, jobDescription);
        return callGeminiApi(prompt);
    }

    private String analyzeCodeInternal(String code) {
        String prompt = "Analyze the following code snippet. Provide a structured review covering:\n" +
                "1. **Correctness**: Does it look logically correct? Identify potential bugs.\n" +
                "2. **Time & Space Complexity**: Estimate the Big O complexity.\n" +
                "3. **Best Practices**: Suggest improvements for readability, naming conventions, and clean code.\n" +
                "4. **Optimized Code**: Provide a refactored/optimized version of the code.\n\n" +
                "Code:\n" + code;
        return callGeminiApi(prompt);
    }

    private String callGeminiApi(String prompt) {
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
    private String createPrompt(String cvText, String experienceLevel, String questionType, List<String> selectedSkills, String jobDescription) {
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

        String complexityPrompt = "";
        if (experienceLevel.contains("Fresher")) {
            complexityPrompt = "The questions should be simple, fundamental, and easy to answer, suitable for a beginner with 0 years of experience. Focus on basic concepts and syntax. ";
        } else if (experienceLevel.contains("2-5")) {
            complexityPrompt = "The questions should be of medium complexity, focusing on practical application, problem-solving, and common use cases. ";
        } else if (experienceLevel.contains("5-8")) {
            complexityPrompt = "The questions should be complex, focusing on system design, optimization, deep technical understanding, and trade-offs. ";
        } else if (experienceLevel.contains("8-10") || experienceLevel.contains("10-15") || experienceLevel.contains(">15")) {
            complexityPrompt = "The questions should be highly advanced, focusing on architecture, scalability, leadership, strategic technical decision making, and complex system design scenarios. ";
        } else {
            complexityPrompt = "Tailor the complexity of the questions to match the candidate's experience level. ";
        }

        String jdPrompt = "";
        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            // Truncate JD to avoid token limits
            String truncatedJd = jobDescription.length() > 3000 ? jobDescription.substring(0, 3000) + "... [truncated]" : jobDescription;
            jdPrompt = "IMPORTANT: Tailor the questions specifically to the following Job Description requirements: " + truncatedJd + ". ";
        }

        return "Generate 10 technical interview questions for a " + experienceLevel +
                " candidate. " + skillsPrompt + typePrompt + complexityPrompt + jdPrompt +
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