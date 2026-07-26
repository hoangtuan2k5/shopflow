package dev.hoangtuan.shopflow.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Kiểm chứng vòng CSRF đúng như trình duyệt thực hiện: đọc cookie {@code XSRF-TOKEN} rồi gửi lại
 * nguyên văn giá trị đó ở header {@code X-XSRF-TOKEN}.
 *
 * <p><b>Contract:</b> không class test nào ở đây được dùng {@code .with(csrf())}.
 *
 * <p><b>Why use:</b> {@code SecurityMockMvcRequestPostProcessors.csrf()} tự đặt token vào request
 * mà không đi qua {@code CsrfTokenRequestHandler} đã cấu hình, nên nó xanh kể cả khi cấu hình chặn
 * đứng mọi request ghi của trình duyệt. Đó chính là cách SF-101 lọt qua 27 test phân quyền và ra
 * tới production.
 *
 * <p><b>Non-obvious behavior:</b> thiếu token và sai token cho ra hai mã khác nhau. {@code
 * CsrfConfigurer} map {@code MissingCsrfTokenException} sang authentication entry point, tức {@code
 * 401} — thiếu token thường nghĩa là phiên đã hết hạn nên đáng để đăng nhập lại. Token sai thì đi
 * thẳng vào access denied handler và ra {@code 403}.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
// Context phải sạch: .with(csrf()) thay CsrfTokenRepository ngay trên bean CsrfFilter dùng chung,
// và context được cache nên một class chạy trước đã đủ làm repository thật biến mất khỏi cả JVM —
// khi đó không response nào phát cookie XSRF-TOKEN nữa và vòng lặp ở đây mất thứ để kiểm.
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class CsrfTokenRoundTripTests {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void buildMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void letsAGuestPlaceAnOrderWithTheTokenItWasHanded() throws Exception {
    Cookie token = tokenFromRejectedRequest(order());

    mockMvc
        .perform(order().cookie(token).header("X-XSRF-TOKEN", token.getValue()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void letsAnEmployeeWriteWithTheTokenItWasHanded() throws Exception {
    MockHttpSession session = signIn();
    Cookie token = tokenFromRejectedRequest(receiving().session(session));

    mockMvc
        .perform(
            receiving().session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsAWriteWhoseHeaderDoesNotMatchItsCookie() throws Exception {
    Cookie token = tokenFromRejectedRequest(order());

    mockMvc
        .perform(
            order().cookie(token).header("X-XSRF-TOKEN", "e0e2ad4c-0000-0000-0000-000000000000"))
        .andExpect(status().isForbidden());
  }

  @Test
  void keepsSignInExemptSoTheFirstTokenCanBeObtained() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"Owner@2026\"}"))
        .andExpect(status().isOk());
  }

  /**
   * Gửi một request ghi không kèm token và trả về cookie mà chính response từ chối đó phát ra.
   *
   * <p><b>Non-obvious behavior:</b> token là deferred nên không request đọc nào phát cookie này;
   * lần từ chối đầu tiên mới là lúc trình duyệt nhận được nó. Mã trả về là {@code 401} hay {@code
   * 403} tuỳ {@code CsrfConfigurer} phân loại thiếu token hay sai token, nên ở đây chỉ khẳng định
   * request bị từ chối — phần cần khoá chặt là mã của token sai, nằm ở test riêng.
   */
  private Cookie tokenFromRejectedRequest(MockHttpServletRequestBuilder write) throws Exception {
    MvcResult rejected = mockMvc.perform(write).andReturn();

    assertThat(rejected.getResponse().getStatus()).isIn(401, 403);
    Cookie token = rejected.getResponse().getCookie("XSRF-TOKEN");
    assertThat(token).as("XSRF-TOKEN cookie").isNotNull();
    assertThat(token.getValue()).isNotBlank();
    return token;
  }

  private MockHttpSession signIn() throws Exception {
    return (MockHttpSession)
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"warehouse\",\"password\":\"Warehouse@2026\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession(false);
  }

  private static MockHttpServletRequestBuilder order() {
    return post("/orders").contentType(MediaType.APPLICATION_JSON).content("{}");
  }

  private static MockHttpServletRequestBuilder receiving() {
    return post("/receivings").contentType(MediaType.APPLICATION_JSON).content("{}");
  }
}
