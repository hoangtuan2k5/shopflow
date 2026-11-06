package dev.hoangtuan.shopflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class GoogleDisabledTests {
  @Test
  void missingCredentialsDisableOnlyGoogleBeforeAnyProviderRequest() throws Exception {
    var clients = mock(ClientRegistrationRepository.class);
    var filter = new GoogleAuthorizationRequests(clients, Clock.systemUTC(), false, false);
    var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
    request.addHeader("Host", "shopflow.hoangtuan.dev");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getHeader("Location")).isNull();
    assertThat(request.getSession(false)).isNull();
    verifyNoInteractions(clients);
  }
}
