package packet;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.ArrayList;

/**
 * A single message exchanged between Client and Server over the socket's
 * ObjectOutputStream/ObjectInputStream. actionArgs holds the payload as
 * "/"-delimited fields; use sanitize()/unsanitize() on any field value that
 * might itself contain a "/" before joining/splitting it.
 */
public class Packet implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String ESCAPED_SLASH = "498928918204";

    private static int count = 0;

    private final String id;
    private final ActionType acType;
    private final Status status;
    private final ArrayList<String> actionArgs;
    private final LocalTime timeCreated;
    private final String senderId;

    public Packet(Status status, ActionType acType, ArrayList<String> actionArguments, String senderId) {
        this.id = String.valueOf(count++);
        this.status = status;
        this.acType = acType;
        this.actionArgs = actionArguments != null ? actionArguments : new ArrayList<>();
        this.timeCreated = LocalTime.now();
        this.senderId = senderId;
    }

    public ArrayList<String> getActionArguments() {
        return actionArgs;
    }

    public String getId() {
        return id;
    }

    public LocalTime getTimeCreated() {
        return timeCreated;
    }

    public String getSenderId() {
        return senderId;
    }

    public ActionType getActionType() {
        return acType;
    }

    public Status getStatus() {
        return status;
    }

    public static String sanitize(String input) {
        if (input == null) return null;
        return input.replace("/", ESCAPED_SLASH);
    }

    public static String unsanitize(String input) {
        if (input == null) return null;
        return input.replace(ESCAPED_SLASH, "/");
    }

    @Override
    public String toString() {
        return "Packet{id=" + id + ", acType=" + acType + ", status=" + status
                + ", senderId=" + senderId + ", actionArgs=" + actionArgs + "}";
    }
}
