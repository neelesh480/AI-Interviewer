package org.neelesh;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CvAnalysisService {

    private static final List<String> COMMON_TECH_STACKS = Arrays.asList(
            "Java", "Spring Boot", "Microservices", "Kafka", "Docker", "Kubernetes",
            "AWS", "Azure", "GCP", "SQL", "NoSQL", "MongoDB", "PostgreSQL", "MySQL",
            "Redis", "React", "Angular", "Vue", "JavaScript", "TypeScript", "HTML", "CSS",
            "Node.js", "Python", "Go", "C++", "C#", ".NET", "Rest API", "GraphQL",
            "CI/CD", "Jenkins", "Git", "Maven", "Gradle", "Hibernate", "JPA",
            "Design Patterns", "System Design", "Agile", "Scrum"
    );

    public List<String> extractTechStack(String cvText) {
        if (cvText == null || cvText.isEmpty()) {
            return new ArrayList<>();
        }

        String lowerCaseCv = cvText.toLowerCase();
        Set<String> foundSkills = new HashSet<>();

        for (String skill : COMMON_TECH_STACKS) {
            if (lowerCaseCv.contains(skill.toLowerCase())) {
                foundSkills.add(skill);
            }
        }

        return new ArrayList<>(foundSkills);
    }
}