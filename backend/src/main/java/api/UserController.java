package api;

import dto.UserDto;
import model.AbstractUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
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
