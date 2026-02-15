package org.neelesh;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
public class CodeAnalysisController {

    private final QuestionGeneratorService questionGeneratorService;
    
    // Shared semaphore with CvUploadController could be better, but separate is fine for now
    // as the bottleneck is the Service queue.
    private final Semaphore semaphore = new Semaphore(5);

    public CodeAnalysisController(QuestionGeneratorService questionGeneratorService) {
        this.questionGeneratorService = questionGeneratorService;
    }

    @PostMapping("/analyze-code")
    public ResponseEntity<String> analyzeCode(@RequestBody String code) {
        if (!semaphore.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Server limit reached. Please try again later.");
        }
        try {
            Future<String> future = questionGeneratorService.queueCodeAnalysis(code);
            String result = future.get(60, TimeUnit.SECONDS);
            return ResponseEntity.ok(result);
        } catch (InterruptedException | ExecutionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error analyzing code: " + e.getMessage());
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .body("Request timed out.");
        } finally {
            semaphore.release();
        }
    }
}