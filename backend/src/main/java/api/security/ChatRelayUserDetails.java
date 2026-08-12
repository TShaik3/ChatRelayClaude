package api.security;

import model.AbstractUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Adapts AbstractUser to Spring Security's UserDetails, so DBManager stays framework-agnostic. */
public class ChatRelayUserDetails implements UserDetails {

    private final AbstractUser user;

    public ChatRelayUserDetails(AbstractUser user) {
        this.user = user;
    }

    public AbstractUser getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.isAdmin()
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUserName();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Backs the "account disabled" login rejection (SRV-3) via Spring Security's own account checks. */
    @Override
    public boolean isEnabled() {
        return !user.isDisabled();
    }
}
