package dev.hoangtuan.shopflow.security;

import dev.hoangtuan.shopflow.repository.jdbc.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

  private final AccountRepository repository;

  public AccountUserDetailsService(AccountRepository repository) {
    this.repository = repository;
  }

  /**
   * Tài khoản bị khóa được coi như không tồn tại. Nhờ đó ba trường hợp sai định danh, sai mật khẩu
   * và tài khoản bị khóa đi cùng một nhánh xử lý, cùng một exception và cùng thời gian phản hồi —
   * {@code DaoAuthenticationProvider} vẫn chạy một phép so khớp giả khi không tìm thấy tài khoản.
   */
  @Override
  public UserDetails loadUserByUsername(String username) {
    var account = repository.findActiveByUsername(username);
    if (account == null) {
      throw new UsernameNotFoundException("Account not found");
    }
    return new AccountPrincipal(
        account.id(),
        account.username(),
        account.passwordHash(),
        account.displayName(),
        account.role());
  }
}
