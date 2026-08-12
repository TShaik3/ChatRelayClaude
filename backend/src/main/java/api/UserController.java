package api;

import api.security.ChatRelayUserDetails;
import dto.UserDto;
import model.AbstractUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import server.DBManager;

import java.util.List;
import java.util.Map;

/** Replaces GET_ALL_USERS/CREATE_USER/UPDATE_USER from the socket protocol (Server.java). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final DBManager dbManager;
    private final SimpMessagingTemplate messagingTemplate;

    public UserController(DBManager dbManager, SimpMessagingTemplate messagingTemplate) {
        this.dbManager = dbManager;
        this.messagingTemplate = messagingTemplate;
    }

    public record CreateUserRequest(String username, String password, String firstName, String lastName,
                                     boolean disabled, boolean admin) {
    }

    public record UpdateUserRequest(String username, String password, String firstName, String lastName,
                                     boolean disabled, boolean admin) {
    }

    public record UpdateSelfRequest(String username, String password, String firstName, String lastName) {
    }

    /** Any authenticated user, not just admins -- needed for the chat-creation member picker. */
    @GetMapping
    public List<UserDto> getAll() {
        return dbManager.listAllUsers().stream().map(UserDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@RequestBody CreateUserRequest request) {
        AbstractUser user = dbManager.writeNewUser(request.username(), request.password(), request.firstName(),
                request.lastName(), request.disabled(), request.admin());
        UserDto dto = UserDto.from(user);
        messagingTemplate.convertAndSend("/topic/users", Map.of("type", "USER_CREATED", "user", dto));
        return dto;
    }

    /**
     * Self-service profile edit for any authenticated user, distinct from the admin-only
     * {@link #update}. Deliberately has no {@code disabled}/{@code admin} fields on its request
     * body -- rather than accepting and ignoring them, the caller's own current values are passed
     * straight through to {@code updateUserDetails} so self-escalation isn't just rejected, it's
     * structurally impossible. The literal "/me" segment resolves before UpdateMapping's
     * "/{id}" pattern (Spring MVC always prefers an exact path match over a variable one), so this
     * doesn't collide with a user editing themselves via the admin-only endpoint below.
     */
    @PutMapping("/me")
    public UserDto updateSelf(@AuthenticationPrincipal ChatRelayUserDetails principal,
                               @RequestBody UpdateSelfRequest request) {
        AbstractUser self = principal.getUser();
        AbstractUser user = dbManager.updateUserDetails(self.getId(), request.username(), request.firstName(),
                request.lastName(), self.isDisabled(), self.isAdmin(), request.password());
        UserDto dto = UserDto.from(user);
        messagingTemplate.convertAndSend("/topic/users", Map.of("type", "USER_UPDATED", "user", dto));
        return dto;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        AbstractUser user = dbManager.updateUserDetails(id, request.username(), request.firstName(),
                request.lastName(), request.disabled(), request.admin(), request.password());
        UserDto dto = UserDto.from(user);
        messagingTemplate.convertAndSend("/topic/users", Map.of("type", "USER_UPDATED", "user", dto));
        return dto;
    }
}
