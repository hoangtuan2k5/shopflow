package dev.hoangtuan.shopflow.security;

import dev.hoangtuan.shopflow.repository.jdbc.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AccountSessionValidationFilter extends OncePerRequestFilter {
  private final AccountRepository repository;

  public AccountSessionValidationFilter(AccountRepository repository) {
    this.repository = repository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AccountPrincipal principal) {
      var state = repository.findSessionState(principal.getUserId());
      if (state == null || !state.active() || state.authVersion() != principal.getAuthVersion()) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
