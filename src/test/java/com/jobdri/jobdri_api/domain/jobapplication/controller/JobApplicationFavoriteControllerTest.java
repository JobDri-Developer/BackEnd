package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobApplicationFavoriteControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired JobApplicationService service;
    @Autowired EntityManager em;

    @Test
    void setsExplicitStateIdempotentlyWithoutChangingDetailsStageOrderOrRevision() throws Exception {
        User owner = owner();
        long id = card(owner, "Original");
        mvc.perform(get("/api/job-applications/{id}", id).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.favorite").value(false));
        var before = applications.findById(id).orElseThrow();
        long revision = before.getContentRevision();
        int order = before.getStageOrder();
        for (boolean favorite : new boolean[]{true, true, false, false, true}) {
            mvc.perform(patch("/api/job-applications/{id}/favorite", id)
                            .with(user(new UserDetailsImpl(owner))).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"favorite\":" + favorite + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result.favorite").value(favorite));
        }
        em.flush();
        em.clear();
        var found = applications.findById(id).orElseThrow();
        assertThat(found.isFavorite()).isTrue();
        assertThat(found.getContentRevision()).isEqualTo(revision);
        assertThat(found.getStage()).isEqualTo(JobApplicationStage.PLANNED);
        assertThat(found.getStageOrder()).isEqualTo(order);
        assertThat(found.getPostingName()).isEqualTo("Original");
        mvc.perform(put("/api/job-applications/{id}", id).with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Company","postingName":"Edited","jobTitle":"Backend",
                                 "expectedRevision":%d}
                                """.formatted(revision)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.favorite").value(true));
    }

    @Test
    void boardCombinesFavoriteFilterSearchAndSortAndExcludesForeignAndArchivedCards() throws Exception {
        User owner = owner();
        long chosen = card(owner, "Spring favorite");
        long other = card(owner, "Spring ordinary");
        long different = card(owner, "Java favorite");
        long archived = card(owner, "Spring archived");
        applications.findById(chosen).orElseThrow().setFavorite(true);
        applications.findById(different).orElseThrow().setFavorite(true);
        var archivedCard = applications.findById(archived).orElseThrow();
        archivedCard.setFavorite(true);
        archivedCard.archive(LocalDateTime.now());
        long foreign = card(owner(), "Spring private");
        applications.findById(foreign).orElseThrow().setFavorite(true);
        applications.flush();
        mvc.perform(get("/api/job-applications/board").with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.columns[0].count").value(3));
        mvc.perform(get("/api/job-applications/board").with(user(new UserDetailsImpl(owner)))
                        .param("favoritesOnly", "true").param("query", " SPRING ").param("sort", "CREATED_DESC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.columns[0].count").value(1))
                .andExpect(jsonPath("$.result.columns[0].cards[0].jobApplicationId").value(chosen))
                .andExpect(jsonPath("$.result.columns[0].cards[0].favorite").value(true));
        mvc.perform(get("/api/job-applications/board").with(user(new UserDetailsImpl(owner)))
                        .param("favoritesOnly", "true").param("query", "missing").param("sort", "CREATED_ASC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.columns[0].count").value(0));
        assertThat(applications.findById(other).orElseThrow().isFavorite()).isFalse();
    }

    @Test
    void validatesRequiredBooleanAuthenticationAndOwnership() throws Exception {
        User owner = owner();
        long id = card(owner, "Card");
        long foreign = card(owner(), "Foreign");
        mvc.perform(patch("/api/job-applications/{id}/favorite", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(status().isUnauthorized());
        for (String body : new String[]{"{}", "{\"favorite\":null}"}) {
            mvc.perform(patch("/api/job-applications/{id}/favorite", id).with(user(new UserDetailsImpl(owner)))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/job-applications/{id}/favorite", foreign).with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/job-applications/{id}/favorite", Long.MAX_VALUE).with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(status().isNotFound());
        assertThat(applications.findById(foreign).orElseThrow().isFavorite()).isFalse();
    }

    @Test
    void migrationBackfillsExistingRowsAndDefaultsNewRows() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V202610261200__add_job_application_favorite.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:h2:mem:favorite-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE job_applications(id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO job_applications VALUES(1)");
            statement.execute(sql);
            statement.execute(sql);
            statement.execute("INSERT INTO job_applications(id) VALUES(2)");
            try (var result = statement.executeQuery("SELECT count(*) FROM job_applications WHERE favorite = false")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
        }
    }

    private User owner() {
        return users.saveAndFlush(User.signup("Favorite user", UUID.randomUUID() + "@example.com", "encoded"));
    }

    private long card(User owner, String name) {
        return service.create(owner, new JobApplicationCreateRequest(
                "Company", name, "Backend", null, null, null, null, null,
                null, null, null, null, null)).getJobApplicationId();
    }
}
