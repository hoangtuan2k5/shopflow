package dev.hoangtuan.shopflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AuthRequestFilter extends OncePerRequestFilter {
  private final Set<String> origins;

  public AuthRequestFilter(Set<String> origins) {
    this.origins = Set.copyOf(origins);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/auth/")) {
      response.setHeader("Cache-Control", "no-store");
      String origin = request.getHeader("Origin");
      if (origin != null && !origins.contains(origin)) {
        forbidden(response);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  public static void forbidden(HttpServletResponse response) throws IOException {
    response.setStatus(403);
    response.setContentType("application/json");
    response
        .getWriter()
        .write("{\"message\":\"Forbidden request\",\"status\":403,\"fieldErrors\":{}}");
  }
}
