package com.medicalreportapp.reports;

import com.medicalreportapp.testcatalog.TestCatalogMatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ParsedObservationParser {

    private static final Pattern SIMPLE_RESULT_LINE = Pattern.compile(
        "^\\s*(?<testName>[A-Za-z][A-Za-z0-9 .()/%+_-]*?)\\s+" +
            "(?<rawValue>[<>]?\\d+(?:\\.\\d+)?)\\s+" +
            "(?<unit>[A-Za-z0-9/%^._+-]+)\\s+" +
            "(?<referenceRange>[<>]?\\d+(?:\\.\\d+)?\\s*-\\s*[<>]?\\d+(?:\\.\\d+)?(?:\\s*[A-Za-z0-9/%^._+-]+)?)\\s*$"
    );
    private static final Pattern NORMALIZE_SEPARATOR = Pattern.compile("[^a-z0-9]+");

    List<ParsedObservation> parse(String extractedText, LocalDate observedAt, List<TestCatalogMatch> catalogMatches) {
        if (!StringUtils.hasText(extractedText)) {
            return List.of();
        }

        Map<String, UUID> catalogIndex = buildCatalogIndex(catalogMatches);

        return extractedText.lines()
            .map(line -> parseLine(line, observedAt, catalogIndex))
            .flatMap(List::stream)
            .toList();
    }

    private static Map<String, UUID> buildCatalogIndex(List<TestCatalogMatch> catalogMatches) {
        Map<String, UUID> index = new HashMap<>();
        for (TestCatalogMatch catalogMatch : catalogMatches) {
            putNormalized(index, catalogMatch.canonicalName(), catalogMatch.id());
            putNormalized(index, catalogMatch.displayName(), catalogMatch.id());
        }
        return index;
    }

    private static void putNormalized(Map<String, UUID> index, String name, UUID id) {
        String normalizedName = normalizeName(name);
        if (StringUtils.hasText(normalizedName)) {
            index.putIfAbsent(normalizedName, id);
        }
    }

    private static List<ParsedObservation> parseLine(String line, LocalDate observedAt, Map<String, UUID> catalogIndex) {
        Matcher matcher = SIMPLE_RESULT_LINE.matcher(line);
        if (!matcher.matches()) {
            return List.of();
        }

        String rawTestName = trimToMax(matcher.group("testName"), 255);
        String rawValue = trimToMax(matcher.group("rawValue"), 100);
        String unit = trimToMax(matcher.group("unit"), 50);
        String referenceRange = trimToMax(matcher.group("referenceRange").replaceAll("\\s+", " "), 255);
        UUID matchedTestId = catalogIndex.get(normalizeName(rawTestName));

        return List.of(new ParsedObservation(
            UUID.randomUUID(),
            null,
            rawTestName,
            matchedTestId,
            observedAt,
            rawValue,
            new BigDecimal(rawValue.replaceFirst("^[<>]", "")),
            unit,
            referenceRange,
            ParsedObservationStatus.NEEDS_REVIEW
        ));
    }

    private static String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return NORMALIZE_SEPARATOR.matcher(name.toLowerCase(Locale.ROOT).trim()).replaceAll(" ").trim();
    }

    private static String trimToMax(String value, int maxLength) {
        String trimmedValue = value.trim();
        return trimmedValue.length() <= maxLength ? trimmedValue : trimmedValue.substring(0, maxLength);
    }
}
