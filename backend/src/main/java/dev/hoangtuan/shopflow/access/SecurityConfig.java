package dev.hoangtuan.shopflow.access;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
class SecurityConfig {

  private static final String WAREHOUSE = Role.WAREHOUSE.name();
  private static final String SHOP_OWNER = Role.SHOP_OWNER.name();

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  AuthenticationManager authenticationManager(
      AccountUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  /**
   * Ma trận quyền đầy đủ nằm trong {@code docs/api-authorization-spec.md}. Quy tắc ở đây đóng mặc
   * định: {@code anyRequest().authenticated()} là lưới cuối, nên một endpoint mới quên khai báo sẽ
   * bị chặn chứ không lọt.
   *
   * <p>Riêng {@code PATCH /returns/{id}} chỉ chặn được ở mức vai trò tại đây; việc kho không được
   * duyệt hay từ chối đổi trả phụ thuộc vào nội dung request nên nằm trong {@link
   * dev.hoangtuan.shopflow.customerreturn.ReturnController}.
   */
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
    return http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/products", "/products/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/orders")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/orders/*/payments")
                    .permitAll()
                    .requestMatchers("/auth/login", "/auth/logout", "/auth/session")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/*")
                    .permitAll()
                    // Ở staging springdoc bị tắt bằng property, xem application-staging.yml.
                    .requestMatchers(
                        "/v3/api-docs", "/v3/api-docs/*", "/swagger-ui.html", "/swagger-ui/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/returns/orders")
                    .hasRole(SHOP_OWNER)
                    .requestMatchers(HttpMethod.POST, "/returns")
                    .hasRole(SHOP_OWNER)
                    .requestMatchers("/inventory", "/inventory/*/**")
                    .hasAnyRole(WAREHOUSE, SHOP_OWNER)
                    .requestMatchers(HttpMethod.POST, "/receivings")
                    .hasAnyRole(WAREHOUSE, SHOP_OWNER)
                    .requestMatchers(HttpMethod.GET, "/deliveries")
                    .hasAnyRole(WAREHOUSE, SHOP_OWNER)
                    .requestMatchers(HttpMethod.PATCH, "/orders/*/delivery")
                    .hasAnyRole(WAREHOUSE, SHOP_OWNER)
                    .requestMatchers(HttpMethod.GET, "/returns")
                    .hasAnyRole(WAREHOUSE, SHOP_OWNER)
                    .requestMatchers(HttpMethod.PATCH, "/returns/*")
                    .hasAnyRole(WAREHOUSE, SHOP_OWNER)
                    .anyRequest()
                    .authenticated())
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        // Mặc định của Spring khi không khai báo cơ chế đăng nhập nào là trả 403 cho cả người
        // chưa đăng nhập. Contract phân biệt: chưa đăng nhập là 401, sai vai trò mới là 403.
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        // Cookie đọc được bằng JavaScript là chủ ý: axios tự đọc XSRF-TOKEN và gửi lại
        // X-XSRF-TOKEN. Token này chống CSRF, không phải bí mật cần giấu khỏi trang của mình.
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    // Người gọi chưa có phiên thì chưa có gì để giả mạo.
                    .ignoringRequestMatchers(
                        PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.POST, "/auth/login")))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .build();
  }
}
