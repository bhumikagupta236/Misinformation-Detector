package com.datastellers.detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automated tests verifying the REST API endpoints, input validation,
 * baseline detection logic, and history CRUD operations.
 */
class MisinformationDetectorApplicationTests {

    private MockMvc mockMvc;
    private final List<NewsClaim> store = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        store.clear();
        idSequence.set(1);

        // Simple Java dynamic proxy implementing NewsClaimRepository for testing
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            String name = method.getName();
            if ("save".equals(name)) {
                NewsClaim claim = (NewsClaim) args[0];
                if (claim.getId() == null) {
                    claim.setId(idSequence.getAndIncrement());
                }
                store.add(claim);
                return claim;
            }
            if ("findAllByOrderByCreatedAtDesc".equals(name)) {
                List<NewsClaim> sorted = new ArrayList<>(store);
                sorted.sort(Comparator.comparing(NewsClaim::getCreatedAt).reversed());
                return sorted;
            }
            if ("existsById".equals(name)) {
                Long id = (Long) args[0];
                return store.stream().anyMatch(c -> id.equals(c.getId()));
            }
            if ("deleteById".equals(name)) {
                Long id = (Long) args[0];
                store.removeIf(c -> id.equals(c.getId()));
                return null;
            }
            if ("deleteAll".equals(name)) {
                store.clear();
                return null;
            }
            return null;
        };

        NewsClaimRepository testRepository = (NewsClaimRepository) Proxy.newProxyInstance(
                NewsClaimRepository.class.getClassLoader(),
                new Class<?>[]{NewsClaimRepository.class},
                handler
        );

        JdbcTemplate noOpJdbcTemplate = new JdbcTemplate() {
            @Override
            public void execute(final String sql) {
                // No-op during unit tests
            }
        };

        NewsClaimService service = new NewsClaimService(testRepository, noOpJdbcTemplate);
        NewsClaimController controller = new NewsClaimController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void analyzeSensationalClaimReturnsPotentiallyMisleading() throws Exception {
        String jsonPayload = "{\"claimText\": \"SHOCKING TRUTH: Secret revealed! Share immediately!!!\"}";

        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.result", is("Potentially Misleading")))
                .andExpect(jsonPath("$.explanation", containsString("shocking truth")))
                .andExpect(jsonPath("$.explanation", containsString("keyword-based heuristic demonstration")));
    }

    @Test
    void analyzeNeutralClaimReturnsNeedsVerification() throws Exception {
        String jsonPayload = "{\"claimText\": \"City council approves annual public library budget for next year.\"}";

        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.result", is("Needs Verification")))
                .andExpect(jsonPath("$.explanation", containsString("absence of suspicious keywords is not proof")));
    }

    @Test
    void analyzeEmptyClaimReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("cannot be empty")));
    }

    @Test
    void getHistoryReturnsSavedRecordsNewestFirst() throws Exception {
        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"First claim analyzed\"}"))
                .andExpect(status().isCreated());

        Thread.sleep(15);

        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"Second claim analyzed\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].claimText", is("Second claim analyzed")))
                .andExpect(jsonPath("$[1].claimText", is("First claim analyzed")));
    }

    @Test
    void deleteExistingRecordRemovesItFromHistory() throws Exception {
        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"Claim to be deleted\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/claims/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("deleted successfully")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteNonExistentRecordReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/claims/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("not found")));
    }

    @Test
    void clearAllHistoryRemovesAllRecords() throws Exception {
        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"First claim\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"Second claim\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("cleared")));

        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
