package dto;

import model.AbstractUser;

/**
 * Client-facing shape for a user -- deliberately excludes the password hash, mirroring what
 * AbstractUser.toStringClient() sends over the socket protocol today. Intended for the Phase 3
 * REST/WebSocket layer; Jackson serializes it directly, no wire-format method needed on the
 * model class itself.
 */
public record UserDto(String id, String username, String firstName, String lastName,
                       boolean disabled, boolean admin) {

    public static UserDto from(AbstractUser user) {
        return new UserDto(user.getId(), user.getUserName(), user.getFirstName(), user.getLastName(),
                user.isDisabled(), user.isAdmin());
    }
}
