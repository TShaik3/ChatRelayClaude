package model;

import packet.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Chat {

    // Plain int + count++ raced under concurrent chat creation -- must be atomic.
    private static final AtomicInteger count = new AtomicInteger(0);

    private final String id;
    private final List<AbstractUser> chatters;
    private final List<Message> messages;
    private final AbstractUser owner;
    private String roomName;
    private boolean isPrivate;

    /** New chat, id auto-generated. */
    public Chat(AbstractUser chatOwner, String name, List<AbstractUser> chatters, boolean isPrivate) {
        this.id = String.valueOf(count.getAndIncrement());
        this.owner = chatOwner;
        this.roomName = name;
        this.chatters = new ArrayList<>();
        this.isPrivate = isPrivate;
        this.messages = new ArrayList<>();
        for (AbstractUser user : chatters) {
            addChatter(user);
        }
    }

    /** Existing chat loaded from persistent storage, id already known. */
    public Chat(AbstractUser chatOwner, String name, String id, List<AbstractUser> chatters, boolean isPrivate) {
        this.id = id;
        this.owner = chatOwner;
        this.roomName = name;
        this.chatters = new ArrayList<>();
        this.isPrivate = isPrivate;
        this.messages = new ArrayList<>();
        for (AbstractUser user : chatters) {
            addChatter(user);
        }
    }

    public static void restoreCount(int highestSeen) {
        count.accumulateAndGet(highestSeen + 1, Math::max);
    }

    public void addChatter(AbstractUser user) {
        if (!chatters.contains(user)) {
            chatters.add(user);
            user.addChat(this);
        }
    }

    public void removeChatter(AbstractUser user) {
        chatters.remove(user);
        user.removeChat(this);
    }

    public void addMessage(Message msg) {
        messages.add(msg);
    }

    public void changePrivacy(boolean newState) {
        this.isPrivate = newState;
    }

    public String getId() {
        return id;
    }

    public String getRoomName() {
        return roomName;
    }

    public AbstractUser getOwner() {
        return owner;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public List<AbstractUser> getChatters() {
        return chatters;
    }

    public ArrayList<String> getChattersIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (AbstractUser user : chatters) {
            ids.add(user.getId());
        }
        return ids;
    }

    public void setRoomName(String newName) {
        this.roomName = newName;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    /** Persistent/wire representation: id/ownerId/roomName/isPrivate/userId1,userId2,userId3 */
    @Override
    public String toString() {
        StringBuilder chatterIds = new StringBuilder();
        for (int i = 0; i < chatters.size(); i++) {
            chatterIds.append(chatters.get(i).getId());
            if (i < chatters.size() - 1) {
                chatterIds.append(",");
            }
        }
        return id + "/" + owner.getId() + "/" + Packet.sanitize(roomName) + "/" + isPrivate + "/" + chatterIds;
    }

    /**
     * Identity is the persistent id, not the Java object reference -- required now that
     * DBManager rebuilds a fresh instance from the database on every read instead of returning
     * a cached object from an in-memory map.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chat other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
