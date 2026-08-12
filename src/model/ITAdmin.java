package model;

public class ITAdmin extends AbstractUser {

    public ITAdmin(String username, String password, String firstName, String lastName,
                   boolean isDisabled, boolean isAdmin) {
        super(username, password, firstName, lastName, isDisabled, isAdmin);
    }

    public ITAdmin(String username, String password, String id, String firstName, String lastName,
                   boolean isDisabled, boolean isAdmin) {
        super(username, password, id, firstName, lastName, isDisabled, isAdmin);
    }

    public ITAdmin(boolean frontEndUser, String id, String username, String firstName, String lastName,
                   boolean isDisabled, boolean isAdmin) {
        super(frontEndUser, id, username, firstName, lastName, isDisabled, isAdmin);
    }
}
