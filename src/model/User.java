package model;

public class User extends AbstractUser {

    public User(String username, String password, String firstName, String lastName,
                boolean isDisabled, boolean isAdmin) {
        super(username, password, firstName, lastName, isDisabled, isAdmin);
    }

    public User(String username, String password, String id, String firstName, String lastName,
                boolean isDisabled, boolean isAdmin) {
        super(username, password, id, firstName, lastName, isDisabled, isAdmin);
    }

    public User(boolean frontEndUser, String id, String username, String firstName, String lastName,
                boolean isDisabled, boolean isAdmin) {
        super(frontEndUser, id, username, firstName, lastName, isDisabled, isAdmin);
    }
}
