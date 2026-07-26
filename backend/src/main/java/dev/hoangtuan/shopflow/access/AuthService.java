package dev.hoangtuan.shopflow.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
class AuthService {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;

  AuthService(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  /**
   * Ném {@link org.springframework.security.core.AuthenticationException} cho mọi trường hợp từ
   * chối; bộ xử lý lỗi quy chúng về một phản hồi duy nhất.
   */
  AuthenticatedUser login(
      LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                request.username(), request.password()));

    // Đổi session id sau khi xác thực để chặn session fixation.
    HttpSession existingSession = httpRequest.getSession(false);
    if (existingSession != null) {
      existingSession.invalidate();
    }
    httpRequest.getSession(true);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);

    return principalOf(authentication).toAuthenticatedUser();
  }

  void logout(HttpServletRequest httpRequest) {
    HttpSession session = httpRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
  }

  SessionResponse currentSession() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal)) {
      return SessionResponse.anonymous();
    }
    return SessionResponse.of(principalOf(authentication).toAuthenticatedUser());
  }

  private AccountPrincipal principalOf(Authentication authentication) {
    return (AccountPrincipal) authentication.getPrincipal();
  }
}
