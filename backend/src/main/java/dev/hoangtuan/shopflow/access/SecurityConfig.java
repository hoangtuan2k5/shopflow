package dev.hoangtuan.shopflow.access;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
class SecurityConfig {

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
   * SF-72 chỉ bổ sung khả năng nhận diện người dùng; mọi endpoint vẫn mở như trước để không thay
   * đổi hành vi của tám luồng nghiệp vụ đã hoàn thành. Việc chặn theo vai trò và bật CSRF thuộc
   * SF-73 — CSRF chưa có gì để bảo vệ chừng nào chưa endpoint nào yêu cầu phiên.
   */
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
    return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .csrf(csrf -> csrf.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .build();
  }
}
