package dev.hoangtuan.shopflow.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Instant;

public final class GoogleFlowSession {
  private static final String INTENT = GoogleFlowSession.class.getName() + ".intent";
  private static final String LINK = GoogleFlowSession.class.getName() + ".link";

  private GoogleFlowSession() {}

  public static boolean safeReturnTo(String path) {
    return "/customer".equals(path) || "/customer?checkout=1".equals(path);
  }

  public static void start(HttpServletRequest request, String returnTo, Instant now) {
    HttpSession session = request.getSession();
    session.removeAttribute(LINK);
    session.setAttribute(INTENT, new Intent(returnTo, now.plusSeconds(300)));
  }

  public static String returnTo(HttpServletRequest request, Instant now) {
    HttpSession session = request.getSession(false);
    Object value = session == null ? null : session.getAttribute(INTENT);
    return value instanceof Intent intent && intent.expiresAt().isAfter(now) ? intent.path() : null;
  }

  public static void pending(HttpServletRequest request, PendingLink proof) {
    request.getSession().setAttribute(LINK, proof);
  }

  public static PendingLink pending(HttpServletRequest request, Instant now) {
    HttpSession session = request.getSession(false);
    Object proof = session == null ? null : session.getAttribute(LINK);
    return proof instanceof PendingLink link && link.expiresAt().isAfter(now) ? link : null;
  }

  public static void clearLink(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) session.removeAttribute(LINK);
  }

  private record Intent(String path, Instant expiresAt) implements Serializable {}

  public record PendingLink(
      String subject,
      String email,
      long userId,
      long authVersion,
      String returnTo,
      Instant expiresAt)
      implements Serializable {}
}
