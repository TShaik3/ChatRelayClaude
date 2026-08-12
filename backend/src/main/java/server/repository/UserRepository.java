package server.repository;

import model.AbstractUser;
import model.ITAdmin;
import model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

/** JdbcTemplate-backed persistence for users, replacing DBManager's old Users.txt file. */
public class UserRepository {

    private static final RowMapper<AbstractUser> ROW_MAPPER = (rs, rowNum) -> {
        String id = String.valueOf(rs.getInt("id"));
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        String firstName = rs.getString("first_name");
        String lastName = rs.getString("last_name");
        boolean isDisabled = rs.getBoolean("is_disabled");
        boolean isAdmin = rs.getBoolean("is_admin");
        return isAdmin
                ? new ITAdmin(username, passwordHash, id, firstName, lastName, isDisabled, true)
                : new User(username, passwordHash, id, firstName, lastName, isDisabled, false);
    };

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AbstractUser findById(String id) {
        Integer parsedId = tryParseId(id);
        if (parsedId == null) {
            return null;
        }
        List<AbstractUser> results = jdbc.query(
                "SELECT id, username, password_hash, first_name, last_name, is_disabled, is_admin FROM users WHERE id = ?",
                ROW_MAPPER, parsedId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Callers throughout Server/DBManager treat any unrecognized id -- including "" (the
     * sentinel several client calls use for "no other users") or arbitrary garbage from a
     * malicious/malformed request -- as simply "not found", the way a Map.get(unknownKey) used
     * to under the old in-memory implementation. A non-numeric id must fail the same way rather
     * than throwing NumberFormatException.
     */
    static Integer tryParseId(String id) {
        try {
            return Integer.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public AbstractUser findByUsername(String username) {
        List<AbstractUser> results = jdbc.query(
                "SELECT id, username, password_hash, first_name, last_name, is_disabled, is_admin FROM users WHERE username = ?",
                ROW_MAPPER, username);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<AbstractUser> findAll() {
        return jdbc.query(
                "SELECT id, username, password_hash, first_name, last_name, is_disabled, is_admin FROM users ORDER BY id",
                ROW_MAPPER);
    }

    public void insert(AbstractUser user) {
        jdbc.update("INSERT INTO users (id, username, password_hash, first_name, last_name, is_disabled, is_admin) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Integer.parseInt(user.getId()), user.getUserName(), user.getPassword(), user.getFirstName(),
                user.getLastName(), user.isDisabled(), user.isAdmin());
    }

    public void update(AbstractUser user) {
        jdbc.update("UPDATE users SET username = ?, password_hash = ?, first_name = ?, last_name = ?, " +
                        "is_disabled = ?, is_admin = ? WHERE id = ?",
                user.getUserName(), user.getPassword(), user.getFirstName(), user.getLastName(),
                user.isDisabled(), user.isAdmin(), Integer.parseInt(user.getId()));
    }

    /** Highest id currently stored, or -1 if the table is empty -- used to bootstrap AbstractUser's id counter. */
    public int maxId() {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(id), -1) FROM users", Integer.class);
        return max;
    }
}
