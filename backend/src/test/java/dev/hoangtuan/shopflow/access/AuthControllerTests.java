package dev.hoangtuan.shopflow.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

  private static final String REJECTED = "Invalid username or password";

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void buildMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .defaultRequest(get("/").with(csrf()))
            .build();
  }

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void resetTestAccounts() {
    jdbcTemplate.update("DELETE FROM shopflow.users WHERE username LIKE 'test-%'");
  }

  @Test
  void signsInAnActiveAccountAndReturnsItsRole() throws Exception {
    insertAccount("test-keeper", "Str0ng@Pass", "Thủ kho", "WAREHOUSE", true);

    MvcResult result =
        mockMvc
            .perform(login("test-keeper", "Str0ng@Pass"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("test-keeper"))
            .andExpect(jsonPath("$.displayName").value("Thủ kho"))
            .andExpect(jsonPath("$.role").value("WAREHOUSE"))
            .andExpect(jsonPath("$.userId").isNumber())
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("password", "passwordHash", "$2a$");
    assertThat(result.getRequest().getSession(false)).isNotNull();
  }

  @Test
  void rejectsWrongPasswordUnknownAccountAndDeactivatedAccountIdentically() throws Exception {
    insertAccount("test-active", "Str0ng@Pass", "Đang hoạt động", "WAREHOUSE", true);
    insertAccount("test-locked", "Str0ng@Pass", "Đã khóa", "WAREHOUSE", false);

    for (String[] attempt :
        new String[][] {
          {"test-active", "WrongPass"},
          {"test-missing", "Str0ng@Pass"},
          {"test-locked", "Str0ng@Pass"}
        }) {
      mockMvc
          .perform(login(attempt[0], attempt[1]))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.message").value(REJECTED))
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
  }

  @Test
  void rejectsMalformedCredentialsRequests() throws Exception {
    mockMvc
        .perform(login("", "Str0ng@Pass"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.username").exists());

    mockMvc
        .perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void keepsSurroundingWhitespaceInPasswordsAndIgnoresUsernameCase() throws Exception {
    insertAccount("test-spaced", "  spaced pass  ", "Có khoảng trắng", "SHOP_OWNER", true);

    mockMvc.perform(login("test-spaced", "  spaced pass  ")).andExpect(status().isOk());
    mockMvc.perform(login("test-spaced", "spaced pass")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(login("TEST-SPACED", "  spaced pass  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("SHOP_OWNER"));
  }

  @Test
  void reportsAnonymousSessionWithoutFailing() throws Exception {
    mockMvc
        .perform(get("/auth/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false))
        .andExpect(jsonPath("$.user").doesNotExist());
  }

  @Test
  void carriesTheSignedInAccountAcrossRequestsUntilLogout() throws Exception {
    insertAccount("test-owner", "Str0ng@Pass", "Chủ shop", "SHOP_OWNER", true);

    MockHttpSession session =
        (MockHttpSession)
            mockMvc
                .perform(login("test-owner", "Str0ng@Pass"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

    mockMvc
        .perform(get("/auth/session").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.user.username").value("test-owner"))
        .andExpect(jsonPath("$.user.role").value("SHOP_OWNER"));

    mockMvc
        .perform(post("/auth/logout").session(session))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    mockMvc
        .perform(get("/auth/session").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false));
  }

  @Test
  void treatsLogoutWithoutASessionAsSuccess() throws Exception {
    mockMvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
  }

  @Test
  void storesSeededDemoPasswordsOnlyAsBcryptHashes() {
    jdbcTemplate
        .queryForList(
            "SELECT username, password_hash FROM shopflow.users WHERE username IN (?, ?, ?)",
            "customer",
            "warehouse",
            "owner")
        .forEach(
            row -> {
              String hash = (String) row.get("password_hash");
              assertThat(hash).startsWith("$2a$10$").hasSize(60);
              assertThat(hash).doesNotContain((String) row.get("username"));
            });
  }

  @Test
  void signsInEachSeededDemoAccountWithItsDocumentedRole() throws Exception {
    for (String[] account :
        new String[][] {
          {"customer", "Customer@2026", "CUSTOMER"},
          {"warehouse", "Warehouse@2026", "WAREHOUSE"},
          {"owner", "Owner@2026", "SHOP_OWNER"}
        }) {
      mockMvc
          .perform(login(account[0], account[1]))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.role").value(account[2]));
    }
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
      String username, String password) {
    return post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":%s,\"password\":%s}".formatted(quote(username), quote(password)));
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private void insertAccount(
      String username, String rawPassword, String displayName, String role, boolean active) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.users (username, password_hash, display_name, role, active) "
            + "VALUES (?, ?, ?, ?, ?)",
        username,
        passwordEncoder.encode(rawPassword),
        displayName,
        role,
        active);
  }
}
