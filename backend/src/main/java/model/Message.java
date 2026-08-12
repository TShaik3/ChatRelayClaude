package model;

import packet.Packet;

import java.util.concurrent.atomic.AtomicInteger;

public class Message {

    // Plain int + count++ raced under concurrent message creation -- must be atomic.
    private static final AtomicInteger count = new AtomicInteger(0);

    private final String id;
    private final long createdAt;
    private final String content;
    private final AbstractUser author;
    private final Chat chat;

    /** New message, id and timestamp auto-generated. */
    public Message(String content, AbstractUser author, Chat chat) {
        this.id = String.valueOf(count.getAndIncrement());
        this.createdAt = System.currentTimeMillis() / 1000L;
        this.content = content;
        this.author = author;
        this.chat = chat;
    }

    /** Existing message loaded from persistent storage. */
    public Message(String id, long createdAt, String content, AbstractUser author, Chat chat) {
        this.id = id;
        this.createdAt = createdAt;
        this.content = content;
        this.author = author;
        this.chat = chat;
    }

    public static void restoreCount(int highestSeen) {
        count.accumulateAndGet(highestSeen + 1, Math::max);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public Chat getChat() {
        return chat;
    }

    public AbstractUser getSender() {
        return author;
    }

    /** Persistent/wire representation: id/createdAt/content/authorId/chatId */
    @Override
    public String toString() {
        return id + "/" + createdAt + "/" + Packet.sanitize(content) + "/" + author.getId() + "/" + chat.getId();
    }

    /**
     * Identity is the persistent id, not the Java object reference -- required now that
     * DBManager rebuilds a fresh instance from the database on every read instead of returning
     * a cached object from an in-memory map.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
