package org.neelesh;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
public class CvUploadController {

    private final CvParserService cvParserService;
    private final QuestionGeneratorService questionGeneratorService;
    private final CvAnalysisService cvAnalysisService;
    
    // STRICT LIMIT: Only allow 10 concurrent requests in the system.
    private final Semaphore semaphore = new Semaphore(10);

    public CvUploadController(CvParserService cvParserService, QuestionGeneratorService questionGeneratorService, CvAnalysisService cvAnalysisService) {
        this.cvParserService = cvParserService;
        this.questionGeneratorService = questionGeneratorService;
        this.cvAnalysisService = cvAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeCv(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("Analyzing CV: " + file.getOriginalFilename());
            String cvText = cvParserService.parseCv(file);
            System.out.println("CV Text Length: " + (cvText != null ? cvText.length() : 0));
            
            List<String> skills = cvAnalysisService.extractTechStack(cvText);
            System.out.println("Extracted Skills: " + skills);
            
            return ResponseEntity.ok(skills);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<String> generateQuestions(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("experienceLevel") String experienceLevel,
                                                    @RequestParam(value = "questionType", defaultValue = "Mixed") String questionType,
                                                    @RequestParam(value = "selectedSkills", required = false) List<String> selectedSkills) {
        // Try to acquire a permit. If full, return 429 immediately.
        if (!semaphore.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Server limit reached (10 active requests). Please try again in a minute.");
        }
        try {
            String cvText = cvParserService.parseCv(file);
            
            // Submit to queue
            Future<String> future = questionGeneratorService.queueQuestionGeneration(cvText, experienceLevel, questionType, selectedSkills);
            
            // Wait for result. 
            String questions = future.get(60, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(questions);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating questions: " + e.getMessage());
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .body("Request timed out. The server is under heavy load.");
        } finally {
            semaphore.release();
        }
    }
    
    // Keep the old endpoint for backward compatibility if needed, or redirect to generate
    @PostMapping("/upload")
    public ResponseEntity<String> uploadCv(@RequestParam("file") MultipartFile file,
                                           @RequestParam("experienceLevel") String experienceLevel) {
        return generateQuestions(file, experienceLevel, "Mixed", null);
    }
}