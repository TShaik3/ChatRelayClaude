package client;

import model.AbstractUser;
import model.Chat;
import model.Message;
import packet.ActionType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Swing front end, styled after the "Paper Prototype V2" wireframes: a
 * name + "+" header over Private/Group chat card lists on the left, and a
 * "Chat Name • Members" header over bordered message cards on the right.
 * Client calls update(action) on the EDT whenever new state arrives so the
 * lists/messages stay in sync.
 */
public class GUI {

    private static final Color ACCENT = new Color(0x2F6FED);
    private static final Color CARD_BORDER = new Color(0xDD, 0xDD, 0xDD);
    private static final Color CARD_SELECTED_BG = new Color(0xE3, 0xEE, 0xFF);
    private static final Color MUTED_TEXT = new Color(0x88, 0x88, 0x88);
    private static final Color IT_BADGE = new Color(0xC0, 0x39, 0x2B);
    private static final String IT_BADGE_HEX = "#C0392B";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a");

    private final Client client;
    private JFrame frame;
    private JPanel chatsList;
    private JPanel allUsersList;
    private ScrollableMessagePanel chatMessagesPanel;
    private JScrollPane chatScroll;
    private final HashMap<JButton, Chat> chatMap = new HashMap<>();
    private JButton selectedChatButton;

    private JTextField messageField;
    private JButton sendButton;
    private JButton renameChatButton;
    private JButton downloadChatButton;
    private String messagePlaceholder = "Message";
    private JLabel currentChatLabel;
    private Chat selectedChat;

    public GUI(Client client) {
        this.client = client;
    }

    public void run() {
        frame = new JFrame("ChatRelay");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 640);
        showLoginScreen();
        frame.setVisible(true);
    }

    public JFrame getFrame() {
        return frame;
    }

    // ---- Login pane ----

    private void showLoginScreen() {
        JPanel outer = new JPanel(new java.awt.GridBagLayout());

        JPanel pane = new JPanel(new BorderLayout(20, 0));
        pane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(24, 24, 24, 24)));

        JPanel icon = new JPanel();
        icon.setLayout(new BoxLayout(icon, BoxLayout.Y_AXIS));
        icon.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1, true));
        icon.setPreferredSize(new Dimension(120, 120));
        JLabel iconLabel = new JLabel("<html><div style='text-align:center'>Chat<br>Relay</div></html>");
        iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD, 16f));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setAlignmentX(0.5f);
        icon.add(Box.createVerticalGlue());
        icon.add(iconLabel);
        icon.add(Box.createVerticalGlue());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Sign into your Account");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setAlignmentX(0f);

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        usernameField.setMaximumSize(new Dimension(260, 30));
        passwordField.setMaximumSize(new Dimension(260, 30));
        usernameField.setAlignmentX(0f);
        passwordField.setAlignmentX(0f);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        JButton loginButton = new JButton("Login");
        JButton cancelButton = new JButton("Cancel");
        buttons.add(loginButton);
        buttons.add(cancelButton);
        buttons.setAlignmentX(0f);
        buttons.setMaximumSize(new Dimension(260, 40));

        form.add(title);
        form.add(Box.createVerticalStrut(12));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(6));
        form.add(passwordField);
        form.add(Box.createVerticalStrut(12));
        form.add(buttons);

        pane.add(icon, BorderLayout.WEST);
        pane.add(form, BorderLayout.CENTER);

        loginButton.addActionListener(e ->
                client.login(usernameField.getText().trim(), new String(passwordField.getPassword())));
        passwordField.addActionListener(e ->
                client.login(usernameField.getText().trim(), new String(passwordField.getPassword())));
        cancelButton.addActionListener(e -> System.exit(0));

        outer.add(pane);

        frame.getContentPane().removeAll();
        frame.getContentPane().add(outer, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
    }

    // ---- Main screen ----

    private void showMainScreen() {
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebar(), buildChatArea());
        splitPane.setDividerLocation(260);

        frame.getContentPane().add(splitPane, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();

        refreshChatLists();
        refreshAllUsersPanel();
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(260, 0));

        AbstractUser me = client.getThisUser();
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel nameBlock = new JPanel();
        nameBlock.setLayout(new BoxLayout(nameBlock, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(me != null ? me.getFirstName() + " " + me.getLastName() : "");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setAlignmentX(0f);
        nameBlock.add(nameLabel);
        if (client.getAdminStatus()) {
            JLabel badge = new JLabel("IT View");
            badge.setForeground(IT_BADGE);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));
            badge.setAlignmentX(0f);
            nameBlock.add(badge);
        }

        JButton newChatButton = new JButton("+");
        newChatButton.setToolTipText("New chat");
        newChatButton.setForeground(ACCENT);
        newChatButton.setFont(newChatButton.getFont().deriveFont(Font.BOLD, 14f));
        newChatButton.addActionListener(e -> showCreateChatDialog());
        Dimension newChatButtonSize = new Dimension(30, 30);
        newChatButton.setPreferredSize(newChatButtonSize);
        newChatButton.setMinimumSize(newChatButtonSize);
        newChatButton.setMaximumSize(newChatButtonSize);

        // A raw BorderLayout.EAST slot stretches to fill its row's height, and JPanel/BorderLayout
        // both report an unbounded maximumSize by default -- so without the two fixes below, this
        // header row (and the button in it) grows every time the window is resized taller.
        JPanel newChatButtonWrapper = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        newChatButtonWrapper.add(newChatButton);

        header.add(nameBlock, BorderLayout.WEST);
        header.add(newChatButtonWrapper, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        sidebar.add(header);
        sidebar.add(new javax.swing.JSeparator());

        sidebar.add(sectionLabel("Chats"));
        chatsList = cardListPanel();
        JScrollPane chatsScroll = new JScrollPane(chatsList);
        chatsScroll.setBorder(BorderFactory.createEmptyBorder());
        sidebar.add(chatsScroll);

        if (client.getAdminStatus()) {
            JLabel allUsersTitle = sectionLabel("All Users");
            allUsersTitle.setForeground(IT_BADGE);
            sidebar.add(allUsersTitle);
            allUsersList = cardListPanel();
            JScrollPane allUsersScroll = new JScrollPane(allUsersList);
            allUsersScroll.setBorder(BorderFactory.createEmptyBorder());
            sidebar.add(allUsersScroll);
        }

        JPanel toolbar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 6));
        if (client.getAdminStatus()) {
            JButton newUserButton = new JButton("Create User");
            newUserButton.addActionListener(e -> showCreateUserDialog());
            toolbar.add(newUserButton);
        }
        JButton logoutButton = new JButton("Log out");
        logoutButton.addActionListener(e -> client.logout());
        toolbar.add(logoutButton);
        // Every Swing component reports an unbounded maximumSize by default unless capped (see
        // the header fix above) -- without this, toolbar competes with chatsScroll for leftover
        // vertical space, grows tall on its own, and FlowLayout centers its buttons within that
        // now-oversized row. Fewer chats means less of that space goes to chatsScroll and more
        // ends up here, which is exactly why it got worse for a non-admin with few chats.
        toolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolbar.getPreferredSize().height));
        sidebar.add(toolbar);

        return sidebar;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(new EmptyBorder(8, 10, 4, 10));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private JPanel cardListPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JPanel buildChatArea() {
        JPanel chatArea = new JPanel(new BorderLayout());
        currentChatLabel = new JLabel("Select a chat");
        currentChatLabel.setFont(currentChatLabel.getFont().deriveFont(Font.BOLD, 15f));
        currentChatLabel.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel chatHeaderToolbar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 6));
        renameChatButton = new JButton("Rename");
        renameChatButton.addActionListener(e -> showRenameChatDialog());
        chatHeaderToolbar.add(renameChatButton);
        if (client.getAdminStatus()) {
            downloadChatButton = new JButton("Download");
            downloadChatButton.addActionListener(e -> showDownloadChatDialog());
            chatHeaderToolbar.add(downloadChatButton);
        }

        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.add(currentChatLabel, BorderLayout.WEST);
        chatHeader.add(chatHeaderToolbar, BorderLayout.EAST);
        chatHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatHeader.getPreferredSize().height));
        setChatHeaderButtonsEnabled(false);

        chatMessagesPanel = new ScrollableMessagePanel();
        chatMessagesPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        chatScroll = new JScrollPane(chatMessagesPanel);
        chatScroll.getVerticalScrollBar().setUnitIncrement(ScrollableMessagePanel.UNIT_INCREMENT);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        messageField = new JTextField();
        installPlaceholderBehavior(messageField);
        setMessagePlaceholder("Message");
        sendButton = new JButton("Send");
        sendButton.setForeground(ACCENT);
        sendButton.setFont(sendButton.getFont().deriveFont(Font.BOLD));
        sendButton.addActionListener(e -> sendCurrentMessage());
        messageField.addActionListener(e -> sendCurrentMessage());
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatArea.add(chatHeader, BorderLayout.NORTH);
        chatArea.add(chatScroll, BorderLayout.CENTER);
        chatArea.add(inputPanel, BorderLayout.SOUTH);
        return chatArea;
    }

    /** Installs one persistent focus listener that always reads the current messagePlaceholder. */
    private void installPlaceholderBehavior(JTextField field) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(messagePlaceholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setForeground(MUTED_TEXT);
                    field.setText(messagePlaceholder);
                }
            }
        });
    }

    private void setMessagePlaceholder(String placeholder) {
        messagePlaceholder = placeholder;
        if (!messageField.isFocusOwner()) {
            messageField.setForeground(MUTED_TEXT);
            messageField.setText(placeholder);
        }
    }

    private void sendCurrentMessage() {
        String text = messageField.getText().trim();
        if (!text.isEmpty() && !text.equals(messagePlaceholder) && selectedChat != null) {
            client.sendMessage(selectedChat.getId(), text);
            messageField.setText("");
        }
    }

    // ---- Create Chat dialog ----

    private void showCreateChatDialog() {
        JTextField nameField = new JTextField();

        JPanel memberPicker = new JPanel();
        memberPicker.setLayout(new BoxLayout(memberPicker, BoxLayout.Y_AXIS));
        LinkedHashMap<JCheckBox, AbstractUser> checkboxes = new LinkedHashMap<>();
        String myId = client.getThisUserId();
        for (AbstractUser user : client.getUsers()) {
            if (user.getId().equals(myId)) continue;
            JCheckBox box = new JCheckBox(user.getFirstName() + " " + user.getLastName() + " (@" + user.getUserName() + ")");
            box.setAlignmentX(0f);
            checkboxes.put(box, user);
            memberPicker.add(box);
        }
        JScrollPane memberScroll = new JScrollPane(memberPicker);
        memberScroll.setPreferredSize(new Dimension(280, 160));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Create Chat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(0.5f);
        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setAlignmentX(0f);
        nameField.setAlignmentX(0f);
        JLabel groupLabel = new JLabel("Add to Group:");
        groupLabel.setAlignmentX(0f);
        memberScroll.setAlignmentX(0f);

        panel.add(title);
        panel.add(Box.createVerticalStrut(12));
        panel.add(nameLabel);
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(8));
        panel.add(groupLabel);
        panel.add(memberScroll);

        int result = JOptionPane.showConfirmDialog(frame, panel, "New Chat",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        List<String> selectedIds = checkboxes.entrySet().stream()
                .filter(entry -> entry.getKey().isSelected())
                .map(entry -> entry.getValue().getId())
                .collect(Collectors.toList());
        boolean isPrivate = selectedIds.size() == 1;
        client.createChat(selectedIds.toArray(new String[0]), nameField.getText().trim(), isPrivate);
    }

    private void setChatHeaderButtonsEnabled(boolean enabled) {
        renameChatButton.setEnabled(enabled);
        if (downloadChatButton != null) {
            downloadChatButton.setEnabled(enabled);
        }
    }

    private void showRenameChatDialog() {
        if (selectedChat == null) return;
        JTextField nameField = new JTextField(selectedChat.getRoomName());
        int result = JOptionPane.showConfirmDialog(frame, nameField, "Rename Chat", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String newName = nameField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(selectedChat.getRoomName())) {
                client.renameChat(selectedChat.getId(), newName);
            }
        }
    }

    private void showDownloadChatDialog() {
        if (selectedChat == null) return;
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File("chat-" + selectedChat.getId() + "-export.txt"));
        int result = chooser.showSaveDialog(frame);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            client.saveChatToTxt(selectedChat, chooser.getSelectedFile());
            JOptionPane.showMessageDialog(frame, "Chat exported to " + chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void showCreateUserDialog() {
        JTextField usernameField = new JTextField();
        JTextField passwordField = new JTextField();
        JTextField firstField = new JTextField();
        JTextField lastField = new JTextField();
        JCheckBox adminBox = new JCheckBox("IT Admin");

        JPanel panel = new JPanel(new GridLayout(5, 2, 5, 5));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("First name:"));
        panel.add(firstField);
        panel.add(new JLabel("Last name:"));
        panel.add(lastField);
        panel.add(adminBox);

        int result = JOptionPane.showConfirmDialog(frame, panel, "New User", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            client.createUser(usernameField.getText().trim(), passwordField.getText().trim(),
                    firstField.getText().trim(), lastField.getText().trim(), adminBox.isSelected());
        }
    }

    // ---- Chat list / cards ----

    private void refreshChatLists() {
        chatsList.removeAll();
        chatMap.clear();
        selectedChatButton = null;

        List<Chat> chats = new ArrayList<>(client.getChats());
        chats.sort((a, b) -> Long.compare(lastMessageTime(b), lastMessageTime(a)));

        for (Chat chat : chats) {
            JButton card = chatCard(chat);
            chatMap.put(card, chat);
            if (selectedChat != null && chat.getId().equals(selectedChat.getId())) {
                selectedChatButton = card;
                card.setOpaque(true);
                card.setBackground(CARD_SELECTED_BG);
            }
            chatsList.add(card);
        }

        chatsList.revalidate();
        chatsList.repaint();
    }

    private JButton chatCard(Chat chat) {
        String title = displayTitleFor(chat);
        String time = lastMessageTime(chat) > 0 ? TIME_FORMAT.format(
                toInstant(lastMessageTime(chat)).atZone(ZoneId.systemDefault())) : "";
        String members = otherMemberNames(chat);
        // Admin viewing a chat they don't belong to (moderation): name in the IT accent color
        // instead of default black, so view-only chats stand out from the ones they're actually in.
        String titleColor = isMember(chat) ? "#000000" : IT_BADGE_HEX;

        // Private vs. group chats no longer live in separate sidebar sections, so tag group
        // chats explicitly -- private ones are usually self-evident since their title is already
        // the other person's name (see displayTitleFor), but that heuristic doesn't apply here.
        String typeTag = chat.isPrivate() ? "" : " <font color='#888888' size='2'>&middot; Group</font>";

        StringBuilder html = new StringBuilder("<html><table width='210'><tr>")
                .append("<td><b><font color='").append(titleColor).append("'>").append(escape(title)).append("</font></b>")
                .append(typeTag).append("</td>")
                .append("<td align='right'><font color='#888888' size='2'>").append(escape(time)).append("</font></td>")
                .append("</tr></table>");
        if (!members.isEmpty()) {
            html.append("<font color='#888888' size='2'>").append(escape(members)).append("</font>");
        }
        html.append("</html>");

        JButton button = new JButton(html.toString());
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(0f);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                new EmptyBorder(6, 10, 6, 10)));
        button.addActionListener(e -> selectChat(chat, button));
        return button;
    }

    /** Normalized epoch seconds, so sorting by "most recent" is correct even with mixed-unit legacy data. */
    private long lastMessageTime(Chat chat) {
        List<Message> messages = chat.getMessages();
        return messages.isEmpty() ? 0 : toInstant(messages.get(messages.size() - 1).getCreatedAt()).getEpochSecond();
    }

    /**
     * Message.getCreatedAt() is documented as epoch seconds, but some pre-existing data in
     * Messages.txt was written by an earlier version that used epoch milliseconds (13 digits vs.
     * this app's 10). Rendering those as seconds directly would show a garbage far-future date, so
     * detect the magnitude and normalize before formatting. 100_000_000_000 seconds is year ~5138 --
     * safely above any real second-based timestamp and safely below any millisecond-based one.
     */
    private Instant toInstant(long createdAt) {
        long seconds = createdAt > 100_000_000_000L ? createdAt / 1000 : createdAt;
        return Instant.ofEpochSecond(seconds);
    }

    /**
     * Private (2-person) chats display the other person's name, matching the prototype --
     * but only when the viewer is actually one of the two, otherwise "the other person
     * relative to me" is meaningless (this is what makes an admin's moderation view of a
     * private chat between two other users show correctly instead of picking one at random).
     */
    private String displayTitleFor(Chat chat) {
        if (chat.isPrivate() && chat.getChatters().size() == 2 && isMember(chat)) {
            AbstractUser other = otherMember(chat);
            if (other != null) {
                return other.getFirstName() + " " + other.getLastName();
            }
        }
        return chat.getRoomName();
    }

    private boolean isMember(Chat chat) {
        String myId = client.getThisUserId();
        return chat.getChatters().stream().anyMatch(u -> u.getId().equals(myId));
    }

    private AbstractUser otherMember(Chat chat) {
        String myId = client.getThisUserId();
        return chat.getChatters().stream().filter(u -> !u.getId().equals(myId)).findFirst().orElse(null);
    }

    private String otherMemberNames(Chat chat) {
        String myId = client.getThisUserId();
        return chat.getChatters().stream()
                .filter(u -> !u.getId().equals(myId))
                .map(AbstractUser::getFirstName)
                .collect(Collectors.joining(", "));
    }

    private String allMemberNames(Chat chat) {
        return chat.getChatters().stream()
                .map(AbstractUser::getFirstName)
                .collect(Collectors.joining(", "));
    }

    private void selectChat(Chat chat, JButton button) {
        selectedChat = chat;
        if (selectedChatButton != null) {
            selectedChatButton.setOpaque(false);
            selectedChatButton.setBackground(null);
        }
        selectedChatButton = button;
        button.setOpaque(true);
        button.setBackground(CARD_SELECTED_BG);

        boolean moderatingOnly = !isMember(chat);
        String titleColor = moderatingOnly ? IT_BADGE_HEX : "#000000";
        currentChatLabel.setText("<html><font color='" + titleColor + "'>" + escape(displayTitleFor(chat))
                + "</font> <font color='#888888'>• " + escape(allMemberNames(chat)) + "</font></html>");

        messageField.setEnabled(!moderatingOnly);
        sendButton.setEnabled(!moderatingOnly);
        setMessagePlaceholder(moderatingOnly ? "Viewing as IT Admin — read only" : "Message");

        boolean canRename = client.getAdminStatus()
                || (chat.getOwner() != null && chat.getOwner().getId().equals(client.getThisUserId()));
        renameChatButton.setEnabled(canRename);
        if (downloadChatButton != null) {
            downloadChatButton.setEnabled(true); // admins may export a chat they're only moderating
        }

        renderMessages(chat);
    }

    private void renderMessages(Chat chat) {
        chatMessagesPanel.removeAll();
        for (Message message : chat.getMessages()) {
            chatMessagesPanel.add(messageCard(message));
            chatMessagesPanel.add(Box.createVerticalStrut(6));
        }
        chatMessagesPanel.revalidate();
        chatMessagesPanel.repaint();
        if (chatScroll != null) {
            chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum());
        }
    }

    private JPanel messageCard(Message message) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setAlignmentX(0f);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                new EmptyBorder(6, 8, 6, 8)));

        String author = message.getSender() != null ? message.getSender().getFirstName() : "unknown";
        String time = TIME_FORMAT.format(toInstant(message.getCreatedAt()).atZone(ZoneId.systemDefault()));
        JLabel header = new JLabel("<html><b>" + escape(author) + "</b> <font color='#888888'>• "
                + escape(time) + "</font></html>");

        JTextArea content = new JTextArea(message.getContent());
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setOpaque(false);
        content.setBorder(null);

        card.add(header, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    // ---- All Users (IT admin) ----

    private void refreshAllUsersPanel() {
        if (!client.getAdminStatus() || allUsersList == null) {
            return;
        }
        allUsersList.removeAll();
        for (AbstractUser user : client.getUsers()) {
            allUsersList.add(userDirectoryCard(user));
        }
        allUsersList.revalidate();
        allUsersList.repaint();
    }

    private JButton userDirectoryCard(AbstractUser user) {
        String status = user.isDisabled() ? " <font color='#C0392B'>[disabled]</font>" : "";
        String role = user.isAdmin() ? " <font color='#888888' size='2'>(IT Admin)</font>" : "";
        JButton button = new JButton("<html><b>" + escape(user.getFirstName() + " " + user.getLastName())
                + "</b> <font color='#888888'>(@" + escape(user.getUserName()) + ")</font>" + role + status + "</html>");
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(0f);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                new EmptyBorder(4, 10, 4, 10)));
        button.addActionListener(e -> showEditUserDialog(user));
        return button;
    }

    private void showEditUserDialog(AbstractUser user) {
        JTextField usernameField = new JTextField(user.getUserName());
        JTextField firstField = new JTextField(user.getFirstName());
        JTextField lastField = new JTextField(user.getLastName());
        JPasswordField passwordField = new JPasswordField();
        JCheckBox adminBox = new JCheckBox("IT Admin", user.isAdmin());
        JCheckBox disabledBox = new JCheckBox("Disabled", user.isDisabled());

        JPanel panel = new JPanel(new GridLayout(6, 2, 5, 5));
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("First name:"));
        panel.add(firstField);
        panel.add(new JLabel("Last name:"));
        panel.add(lastField);
        panel.add(new JLabel("New password:"));
        panel.add(passwordField);
        panel.add(new JLabel("(leave blank to keep current password)"));
        panel.add(new JLabel());
        panel.add(adminBox);
        panel.add(disabledBox);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Edit " + user.getFirstName() + " " + user.getLastName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String newUsername = usernameField.getText().trim();
            String newFirst = firstField.getText().trim();
            String newLast = lastField.getText().trim();
            String newPassword = new String(passwordField.getPassword());
            if (!newUsername.isEmpty() && !newFirst.isEmpty() && !newLast.isEmpty()) {
                client.updateUser(user.getId(), newUsername, newFirst, newLast,
                        disabledBox.isSelected(), adminBox.isSelected(), newPassword);
            }
        }
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---- Update hook ----

    public void showMessageDialog(ActionType action) {
        JOptionPane.showMessageDialog(frame, "Server reported an error for action: " + action,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    public void update(ActionType action) {
        if (action == ActionType.ERROR) {
            showMessageDialog(action);
            return;
        }

        if (frame == null) return;

        if (action == ActionType.LOGOUT) {
            selectedChat = null;
            selectedChatButton = null;
            showLoginScreen();
            return;
        }

        boolean onLoginScreen = client.getThisUser() == null;
        if (action == ActionType.LOGIN) {
            if (!onLoginScreen) {
                showMainScreen();
            }
            return;
        }

        if (onLoginScreen) {
            return;
        }

        switch (action) {
            case GET_ALL_CHATS, NEW_CHAT_BROADCAST, ADD_USER_TO_CHAT_BROADCAST,
                    REMOVE_USER_FROM_CHAT_BROADCAST, RENAME_CHAT_BROADCAST -> refreshChatLists();
            case GET_ALL_USERS, NEW_USER_BROADCAST, UPDATED_USER_BROADCAST -> refreshAllUsersPanel();
            case GET_ALL_MESSAGES, NEW_MESSAGE_BROADCAST -> {
                refreshChatLists();
                if (selectedChat != null) {
                    renderMessages(selectedChat);
                }
            }
            default -> { }
        }
    }

    /**
     * A Swing Scrollable view accomplishes two things a plain JPanel can't here:
     *  - getScrollableTracksViewportWidth()=true forces every message card to the same width as
     *    the viewport, so each JTextArea has a known width to word-wrap against and reports a
     *    correct preferred height for *its own* content instead of an arbitrary/uniform one.
     *  - getScrollableUnitIncrement() controls how far one mouse-wheel notch (or scrollbar arrow
     *    click) moves -- Swing's default is a few pixels, which feels glacial through long chats.
     */
    private static final class ScrollableMessagePanel extends JPanel implements Scrollable {
        static final int UNIT_INCREMENT = 60;

        ScrollableMessagePanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return UNIT_INCREMENT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
