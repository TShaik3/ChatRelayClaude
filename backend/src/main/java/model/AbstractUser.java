package model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractUser {

    // Plain int + count++ raced under concurrent user creation (two request threads could read
    // the same value and mint duplicate ids) -- must be atomic.
    private static final AtomicInteger count = new AtomicInteger(0);

    private final String id;
    private String firstName;
    private String lastName;
    private String username;
    private String password;
    private boolean isDisabled;
    private boolean isAdmin;
    private final List<Chat> chats;

    /** New user, id auto-generated. */
    public AbstractUser(String username, String password, String firstName, String lastName,
                         boolean isDisabled, boolean isAdmin) {
        this.id = String.valueOf(count.getAndIncrement());
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isDisabled = isDisabled;
        this.isAdmin = isAdmin;
        this.chats = new ArrayList<>();
    }

    /** Existing user loaded from persistent storage, id already known. */
    public AbstractUser(String username, String password, String id, String firstName, String lastName,
                         boolean isDisabled, boolean isAdmin) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isDisabled = isDisabled;
        this.isAdmin = isAdmin;
        this.chats = new ArrayList<>();
    }

    /** Reconstructed client-side from a server broadcast; no password known. */
    public AbstractUser(boolean frontEndUser, String id, String username, String firstName, String lastName,
                         boolean isDisabled, boolean isAdmin) {
        this.id = id;
        this.username = username;
        this.password = null;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isDisabled = isDisabled;
        this.isAdmin = isAdmin;
        this.chats = new ArrayList<>();
    }

    public void addChat(Chat chat) {
        if (!chats.contains(chat)) {
            chats.add(chat);
        }
    }

    public void removeChat(Chat chat) {
        chats.remove(chat);
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUserName() {
        return username;
    }

    public String getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public List<Chat> getChats() {
        return chats;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public boolean isDisabled() {
        return isDisabled;
    }

    public void updateIsDisabled(boolean isDisabled) {
        this.isDisabled = isDisabled;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Promotes/demotes admin status. Note this does not change the object's concrete Java class
     * (User vs. ITAdmin) -- that distinction was only ever cosmetic at construction time, since
     * both classes are plain siblings of AbstractUser with identical behavior and isAdmin() just
     * reads this field. Nothing in the codebase does an instanceof check on either, so a promoted
     * User instance behaving as an admin via this flag is exactly equivalent to it having been
     * constructed as an ITAdmin in the first place.
     */
    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    /** Advances the shared id counter so ids loaded from storage are never reused. */
    public static void restoreCount(int highestSeen) {
        count.accumulateAndGet(highestSeen + 1, Math::max);
    }

    /**
     * Identity is the persistent id, not the Java object reference -- required now that
     * DBManager rebuilds a fresh instance from the database on every read instead of returning
     * a cached object from an in-memory map.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractUser other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
