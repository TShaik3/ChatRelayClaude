package packet;

public enum ActionType {
    // Client -> Server requests
    LOGIN,
    LOGOUT,
    SEND_MESSAGE,
    GET_ALL_CHATS,
    GET_ALL_USERS,
    GET_ALL_MESSAGES,
    CREATE_CHAT,
    CREATE_USER,
    UPDATE_USER,
    ADD_USER_TO_CHAT,
    REMOVE_USER_FROM_CHAT,
    RENAME_CHAT,

    // Server -> Client broadcasts
    NEW_MESSAGE_BROADCAST,
    NEW_CHAT_BROADCAST,
    NEW_USER_BROADCAST,
    UPDATED_USER_BROADCAST,
    ADD_USER_TO_CHAT_BROADCAST,
    REMOVE_USER_FROM_CHAT_BROADCAST,
    RENAME_CHAT_BROADCAST,

    // Generic replies
    SUCCESS,
    ERROR
}
