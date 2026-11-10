package com.datastellers.detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automated tests verifying REST API endpoints, input validation,
 * baseline detection logic, authentication, and history/analytics operations.
 */
class MisinformationDetectorApplicationTests {

    private MockMvc claimsMockMvc;
    private MockMvc authMockMvc;

    private final List<NewsClaim> claimStore = new ArrayList<>();
    private final List<User> userStore = new ArrayList<>();
    private final AtomicLong claimIdSeq = new AtomicLong(1);
    private final AtomicLong userIdSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        claimStore.clear();
        userStore.clear();
        claimIdSeq.set(1);
        userIdSeq.set(1);

        // Dynamic proxy for NewsClaimRepository
        InvocationHandler claimRepoHandler = (Object proxy, Method method, Object[] args) -> {
            String name = method.getName();
            if ("save".equals(name)) {
                NewsClaim claim = (NewsClaim) args[0];
                if (claim.getId() == null) {
                    claim.setId(claimIdSeq.getAndIncrement());
                }
                claimStore.add(claim);
                return claim;
            }
            if ("findAllByOrderByCreatedAtDesc".equals(name)) {
                List<NewsClaim> sorted = new ArrayList<>(claimStore);
                sorted.sort(Comparator.comparing(NewsClaim::getCreatedAt).reversed());
                return sorted;
            }
            if ("findAllByUserIdOrderByCreatedAtDesc".equals(name)) {
                Long uid = (Long) args[0];
                List<NewsClaim> filtered = new ArrayList<>();
                for (NewsClaim c : claimStore) {
                    if (uid.equals(c.getUserId())) {
                        filtered.add(c);
                    }
                }
                filtered.sort(Comparator.comparing(NewsClaim::getCreatedAt).reversed());
                return filtered;
            }
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                return claimStore.stream().filter(c -> id.equals(c.getId())).findFirst();
            }
            if ("existsById".equals(name)) {
                Long id = (Long) args[0];
                return claimStore.stream().anyMatch(c -> id.equals(c.getId()));
            }
            if ("deleteById".equals(name)) {
                Long id = (Long) args[0];
                claimStore.removeIf(c -> id.equals(c.getId()));
                return null;
            }
            if ("deleteAll".equals(name)) {
                claimStore.clear();
                return null;
            }
            if ("deleteAllByUserId".equals(name)) {
                Long uid = (Long) args[0];
                claimStore.removeIf(c -> uid.equals(c.getUserId()));
                return null;
            }
            return null;
        };

        NewsClaimRepository testClaimRepo = (NewsClaimRepository) Proxy.newProxyInstance(
                NewsClaimRepository.class.getClassLoader(),
                new Class<?>[]{NewsClaimRepository.class},
                claimRepoHandler
        );

        // Dynamic proxy for UserRepository
        InvocationHandler userRepoHandler = (Object proxy, Method method, Object[] args) -> {
            String name = method.getName();
            if ("save".equals(name)) {
                User u = (User) args[0];
                if (u.getId() == null) {
                    u.setId(userIdSeq.getAndIncrement());
                }
                userStore.add(u);
                return u;
            }
            if ("findByEmail".equals(name)) {
                String email = (String) args[0];
                return userStore.stream().filter(u -> email.equalsIgnoreCase(u.getEmail())).findFirst();
            }
            if ("existsByEmail".equals(name)) {
                String email = (String) args[0];
                return userStore.stream().anyMatch(u -> email.equalsIgnoreCase(u.getEmail()));
            }
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                return userStore.stream().filter(u -> id.equals(u.getId())).findFirst();
            }
            return null;
        };

        UserRepository testUserRepo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                userRepoHandler
        );

        JdbcTemplate noOpJdbcTemplate = new JdbcTemplate() {
            @Override
            public void execute(final String sql) {
                // No-op during unit tests
            }
        };

        NewsClaimService claimService = new NewsClaimService(testClaimRepo, noOpJdbcTemplate);
        NewsClaimController claimController = new NewsClaimController(claimService);
        claimsMockMvc = MockMvcBuilders.standaloneSetup(claimController).build();

        UserService userService = new UserService(testUserRepo);
        AuthController authController = new AuthController(userService);
        authMockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void passwordUtilHashesAndVerifiesCorrectly() {
        String plain = "SecretPassword123";
        String hash = PasswordUtil.hashPassword(plain);
        assertTrue(PasswordUtil.checkPassword(plain, hash));
        assertFalse(PasswordUtil.checkPassword("WrongPassword", hash));
    }

    @Test
    void userRegistrationAndLoginWorkflow() throws Exception {
        // Register user
        String regPayload = """
                {
                    "fullName": "Student Demo",
                    "email": "demo@college.edu",
                    "password": "Password123",
                    "confirmPassword": "Password123"
                }
                """;

        authMockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", containsString("Registration successful")));

        // Login user
        String loginPayload = """
                {
                    "email": "demo@college.edu",
                    "password": "Password123"
                }
                """;

        MockHttpSession session = new MockHttpSession();

        authMockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Login successful")))
                .andExpect(jsonPath("$.user.fullName", is("Student Demo")));

        // Current user check
        authMockMvc.perform(get("/api/auth/current-user").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn", is(true)))
                .andExpect(jsonPath("$.user.email", is("demo@college.edu")));

        // Logout
        authMockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Logged out")));
    }

    @Test
    void analyzeSensationalClaimReturnsPotentiallyMisleading() throws Exception {
        String jsonPayload = "{\"claimText\": \"SHOCKING TRUTH: Secret revealed! Share immediately!!!\"}";

        claimsMockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.result", is("Potentially Misleading")))
                .andExpect(jsonPath("$.falsePercentage").isNumber())
                .andExpect(jsonPath("$.explanation", containsString("shocking truth")));
    }

    @Test
    void analyzeNeutralClaimReturnsNeedsVerification() throws Exception {
        String jsonPayload = "{\"claimText\": \"City council approves annual public library budget for next year.\"}";

        claimsMockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.result", is("Needs Verification")))
                .andExpect(jsonPath("$.truePercentage").isNumber())
                .andExpect(jsonPath("$.explanation", containsString("absence of suspicious keywords is not proof")));
    }

    @Test
    void getClaimByIdReturnsSavedRecord() throws Exception {
        claimsMockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"Important news announcement.\"}"))
                .andExpect(status().isCreated());

        claimsMockMvc.perform(get("/api/claims/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.claimText", is("Important news announcement.")));
    }

    @Test
    void analyticsEndpointReturnsComputedStatistics() throws Exception {
        claimsMockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"SHOCKING TRUTH: secret revealed!\"}"))
                .andExpect(status().isCreated());

        claimsMockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"Government research data published.\"}"))
                .andExpect(status().isCreated());

        claimsMockMvc.perform(get("/api/claims/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims", is(2)))
                .andExpect(jsonPath("$.misleadingCount", is(1)))
                .andExpect(jsonPath("$.reliableCount", is(1)));
    }

    @Test
    void deleteExistingRecordRemovesItFromHistory() throws Exception {
        claimsMockMvc.perform(post("/api/claims/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimText\": \"Claim to be deleted\"}"))
                .andExpect(status().isCreated());

        claimsMockMvc.perform(delete("/api/claims/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("deleted successfully")));

        claimsMockMvc.perform(get("/api/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
