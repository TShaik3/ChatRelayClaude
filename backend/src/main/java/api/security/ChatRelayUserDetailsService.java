package api.security;

import model.AbstractUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import server.DBManager;

@Service
public class ChatRelayUserDetailsService implements UserDetailsService {

    private final DBManager dbManager;

    public ChatRelayUserDetailsService(DBManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AbstractUser user = dbManager.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("No such user: " + username);
        }
        return new ChatRelayUserDetails(user);
    }
}
