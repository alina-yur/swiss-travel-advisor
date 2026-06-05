package com.example.service;

import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class RerankingService {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "for", "with", "to", "of", "in", "on",
            "at", "by", "from", "is", "are", "was", "were", "be", "been", "being",
            "i", "me", "my", "we", "our", "you", "your", "it", "this", "that",
            "recommend", "suggest", "find", "show", "please"
    ));

    public <T> List<T> rerank(
            String query,
            List<T> candidates,
            Function<T, String> textExtractor,
            int limit
    ) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                        candidate -> -score(query, textExtractor.apply(candidate))
                ))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private double score(String query, String document) {
        if (query == null || document == null) {
            return 0.0;
        }

        String normalizedQuery = normalize(query);
        String normalizedDocument = normalize(document);

        Set<String> queryTerms = extractImportantTerms(normalizedQuery);

        if (queryTerms.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;

        for (String term : queryTerms) {
            if (normalizedDocument.contains(term)) {
                score += 1.0;
            }
        }

        if (normalizedDocument.contains(normalizedQuery)) {
            score += 3.0;
        }

        return score / queryTerms.size();
    }

    private Set<String> extractImportantTerms(String text) {
        return Arrays.stream(text.split("\\s+"))
                .filter(token -> token.length() > 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}