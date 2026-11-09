package com.jobdri.jobdri_api.domain.jobposting.dto.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Editable registration fields and required values still missing after extraction. */
public record JobPostingRegistrationPreviewResponse(Draft draft, List<String> missingRequiredFields) {

    /** Matches the corresponding fields of the manual card registration request. */
    public record Draft(
            String companyName, String postingName, String jobTitle,
            String task, String requirement, String preferred, List<String> requiredSkills, LocalDateTime deadlineAt
    ) {}

    /** Maps extracted data without inventing company, title or deadline values. */
    public static JobPostingRegistrationPreviewResponse from(JobPostingExtractResponse extracted) {
        String companyName = clean(extracted.companyName());
        String jobTitle = clean(extracted.jobTitle());
        List<String> missing = new ArrayList<>();
        if (companyName.isEmpty()) {
            missing.add("companyName");
        }
        if (jobTitle.isEmpty()) {
            missing.add("jobTitle");
        }
        List<String> skills = extracted.requiredSkills().stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(skill -> !skill.isEmpty() && skill.length() <= 50)
                .distinct()
                .limit(20)
                .toList();
        return new JobPostingRegistrationPreviewResponse(
                new Draft(companyName, clean(extracted.postingName()), jobTitle, clean(extracted.task()),
                        clean(extracted.requirements()), clean(extracted.preferredQualifications()), skills,
                        parseDeadline(extracted.deadlineAt())),
                List.copyOf(missing)
        );
    }

    /** Keeps unknown or invalid source values unset instead of assuming a date or zone. */
    private static LocalDateTime parseDeadline(String value) {
        try {
            return LocalDateTime.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Normalizes missing text to the empty form value. */
    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
