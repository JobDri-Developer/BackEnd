package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class JobPostingEmbeddingTextBuilder {

    public String build(JobPosting jobPosting) {
        List<String> sections = new ArrayList<>();
        addSection(sections, "직무", jobPosting.getJobTitle());
        addSection(sections, "주요업무", jobPosting.getTask());
        addSection(sections, "자격요건", jobPosting.getRequirement());
        addSection(sections, "우대사항", jobPosting.getPreferred());
        return String.join("\n\n", sections);
    }

    private void addSection(List<String> sections, String title, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sections.add(title + "\n" + value.trim());
    }
}
