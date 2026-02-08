import java.io.Serializable;

/**
 * This class represents a message in the game.
 * Messages are passed between client and server with different types.
 * Supported message types include CONNECTED, CHAT, DRAW, GUESS, CLEAR, ROUND_START, ROUND_END, TERMINATE, DRAWER_ASSIGNED, USERNAME
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    // Message type constants
    public static final String CONNECTED = "CONNECTED";
    public static final String CHAT = "CHAT";
    public static final String DRAW = "DRAW";
    public static final String GUESS = "GUESS";
    public static final String CLEAR = "CLEAR";
    public static final String ROUND_START = "ROUND_START";
    public static final String ROUND_END = "ROUND_END";
    public static final String TERMINATE = "TERMINATE";
    public static final String DRAWER_ASSIGNED = "DRAWER_ASSIGNED";
    public static final String START_GAME = "START_GAME";
    public static final String LEADER = "LEADER";
    public static final String WORD_OPTIONS = "WORD_OPTIONS";
    public static final String WORD_SELECTED = "WORD_SELECTED";
    public static final String LEADERBOARD = "LEADERBOARD";
    public static final String SCORE = "SCORE";
    public static final String ROUND_UPDATE = "ROUND_UPDATE";
    public static final String GAME_OVER = "GAME_OVER";

    public static final String USERNAME = "USERNAME";

    private String messageType;
    private String messageContents;

    /**
     * Creates a new message with type and contents.
     *
     * @param messageType     the type of message
     * @param messageContents the contents of the message
     */
    public Message(String messageType, String messageContents) {
        this.messageType = messageType;
        this.messageContents = messageContents;
    }

    /**
     * Creates a message from a formatted string.
     * Parses "TYPE:contents" format.
     *
     * @param formattedMessage the formatted message string
     * @return parsed Message object
     */
    public static Message fromString(String formattedMessage) {
        if (formattedMessage == null || formattedMessage.isEmpty()) {
            return null;
        }

        if (!formattedMessage.contains(":")) {
            return new Message(formattedMessage, "");
        }

        int firstColon = formattedMessage.indexOf(':');
        String type = formattedMessage.substring(0, firstColon);
        String contents = formattedMessage.substring(firstColon + 1);

        return new Message(type, contents);
    }

    /**
     * Converts message to formatted string for transmission.
     *
     * @return formatted string "TYPE:contents"
     */
    @Override
    public String toString() {
        if (messageContents.isEmpty()) {
            return messageType;
        }
        return messageType + ":" + messageContents;
    }

    /**
     * Gets the message type.
     *
     * @return the message type
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * Gets the raw message contents.
     *
     * @return the message contents
     */
    public String getMessageContents() {
        return messageContents;
    }

    /**
     * Parses message for round number
     * @return Integer
     */
    public int parseRoundUpdateMessage() {
        return Integer.parseInt(messageContents);
    }

    /**
     * Parses a DRAW message and returns the drawing data.
     * Format: "DRAW:x,y,color,size"
     *
     * @return DrawData object containing parsed drawing information
     */
    public DrawData parseDrawMessage() {
        if (!messageType.equals(DRAW)) {
            throw new IllegalStateException("Cannot parse non-DRAW message as DrawData");
        }

        String[] parts = messageContents.split(",");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid DRAW message format");
        }

        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            String color = parts[2];
            double size = Double.parseDouble(parts[3]);

            return new DrawData(x, y, color, size);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in DRAW message", e);
        }
    }

    /**
     * Parses a GUESS message and returns the guess data.
     * Format: "GUESS:username:word"
     *
     * @return GuessData object containing parsed guess information
     */
    public GuessData parseGuessMessage() {
        if (!messageType.equals(GUESS)) {
            throw new IllegalStateException("Cannot parse non-GUESS message as GuessData");
        }

        String[] parts = messageContents.split(":", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid GUESS message format");
        }

        String username = parts[0];
        String guess = parts[1];

        return new GuessData(username, guess);
    }

    /**
     * Parses a CHAT message and returns the chat data.
     * Format: "CHAT:username:message"
     *
     * @return ChatData object containing parsed chat information
     */
    public ChatData parseChatMessage() {
        if (!messageType.equals(CHAT)) {
            throw new IllegalStateException("Cannot parse non-CHAT message as ChatData");
        }

        String[] parts = messageContents.split(":", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid CHAT message format");
        }

        String username = parts[0];
        String chatMessage = parts[1];

        return new ChatData(username, chatMessage);
    }

    /**
     * Parses a CONNECTED message and returns the client ID.
     * Format: "CONNECTED:clientID"
     *
     * @return the client ID
     */
    public int parseConnectedMessage() {
        if (!messageType.equals(CONNECTED)) {
            throw new IllegalStateException("Cannot parse non-CONNECTED message");
        }

        try {
            return Integer.parseInt(messageContents);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid client ID format", e);
        }
    }

    /**
     * Parses a ROUND_START message and returns round data.
     * Format: "ROUND_START:word:duration"
     *
     * @return RoundStartData object containing parsed round information
     */
    public RoundStartData parseRoundStartMessage() {
        if (!messageType.equals(ROUND_START)) {
            throw new IllegalStateException("Cannot parse non-ROUND_START message");
        }

        String[] parts = messageContents.split(":", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid ROUND_START message format");
        }

        String word = parts[0];
        int duration = Integer.parseInt(parts[1]);

        return new RoundStartData(word, duration);
    }

    /**
     * parse score message helper
     * @return Integer
     */
    public int parseScoreMessage() {
        return Integer.parseInt(messageContents);
    }

    /**
     * Creates a CONNECTED message.
     *
     * @param clientID the client ID
     * @return Message object
     */
    public static Message createConnectedMessage(int clientID) {
        return new Message(CONNECTED, String.valueOf(clientID));
    }

    /**
     * Creates a SCORE message
     * @param score int score
     * @return message object
     */
    public static Message createScoreMessage(int score) {
        return new Message(SCORE, String.valueOf(score));
    }

    /**
     * Creates a ROUND_UPDATE message
     * @param currentRound int round #
     * @return message object
     */
    public static Message createRoundUpdateMessage(int currentRound) {
        return new Message(ROUND_UPDATE, String.valueOf(currentRound));
    }

    /**
     * Creates a GAME_OVER message
     * @param leaderboardData info for leaderboard
     * @return message object
     */
    public static Message createGameOverMessage(String leaderboardData) {
        return new Message(GAME_OVER, leaderboardData);
    }

    /**
     * Creates a CHAT message.
     *
     * @param username    the username
     * @param chatMessage the chat message
     * @return Message object
     */
    public static Message createChatMessage(String username, String chatMessage) {
        return new Message(CHAT, username + ":" + chatMessage);
    }

    /**
     * Creates a WORD_OPTIONS message
     * @param words String[] of words to choose from
     * @return message object
     */
    public static Message createWordOptionsMessage(String[] words){
        return new Message(WORD_OPTIONS, String.join(",", words));
    }

    /**
     * Creates a WORD_SELECTED message
     * @param word selected word
     * @return Message object
     */
    public static Message createWordSelectedMessage(String word) {
        return new Message(WORD_SELECTED, word);
    }

    /**
     * Creates a DRAW message.
     *
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param color the color
     * @param size  the brush size
     * @return Message object
     */
    public static Message createDrawMessage(double x, double y, String color, double size) {
        return new Message(DRAW, x + "," + y + "," + color + "," + size);
    }

    /**
     * Creates a GUESS message.
     *
     * @param username the username
     * @param guess    the guessed word
     * @return Message object
     */
    public static Message createGuessMessage(String username, String guess) {
        return new Message(GUESS, username + ":" + guess);
    }

    /**
     * Creates a LEADERBOARD message
     * @param leaderboardString string of leaderboard info
     * @return Message object
     */
    public static Message createLeaderboardMessage(String leaderboardString) {
        return new Message(LEADERBOARD, leaderboardString);
    }

    /**
     * Creates a CLEAR message.
     *
     * @return Message object
     */
    public static Message createClearMessage() {
        return new Message(CLEAR, "");
    }

    /**
     * Creates a ROUND_START message.
     *
     * @param word     the word to draw
     * @param duration the round duration in seconds
     * @return Message object
     */
    public static Message createRoundStartMessage(String word, int duration) {
        return new Message(ROUND_START, word + ":" + duration);
    }

    /**
     * Creates a TERMINATE message.
     *
     * @return Message object
     */
    public static Message createTerminateMessage() {
        return new Message(TERMINATE, "");
    }

    /**
     * Creates a DRAWER_ASSIGNED message.
     *
     * @return Message object
     */
    public static Message createDrawerAssignedMessage() {
        return new Message(DRAWER_ASSIGNED, "");
    }

    /**
     * Creates a USERNAME message.
     *
     * @param username the username
     * @return Message object
     */
    public static Message createUsernameMessage(String username) {
        return new Message(USERNAME, username);
    }

    /**
     * Creates a LEADER message
     */
    public static Message createLeaderMessage() {
        return new Message(LEADER, "");
    }

    /**
     * Creates a new START_GAME message
     */
    public static Message createStartGameMessage() {
        return new Message(START_GAME, "");
    }

    /**
     * Inner class representing drawing data.
     */
    public static class DrawData {
        private double x;
        private double y;
        private String color;
        private double size;

        /**
         * Creates drawing data.
         * @param x the x coordinate
         * @param y the y coordinate
         * @param color the color
         * @param size the brush size
         */
        public DrawData(double x, double y, String color, double size) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.size = size;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public String getColor() {
            return color;
        }

        public double getSize() {
            return size;
        }
    }

    /**
     * Inner class representing guess data.
     */
    public static class GuessData {
        private String username;
        private String guess;

        /**
         * Creates guess data.
         * @param username the username
         * @param guess the guessed word
         */
        public GuessData(String username, String guess) {
            this.username = username;
            this.guess = guess;
        }

        public String getUsername() {
            return username;
        }

        public String getGuess() {
            return guess;
        }
    }

    /**
     * Inner class representing chat data.
     */
    public static class ChatData {
        private String username;
        private String message;

        /**
         * Creates chat data.
         *
         * @param username the username
         * @param message the chat message
         */
        public ChatData(String username, String message) {
            this.username = username;
            this.message = message;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Inner class representing round start data.
     */
    public static class RoundStartData {
        private String word;
        private int duration;

        /**
         * Creates round start data.
         * @param word the word to draw
         * @param duration the round duration in seconds
         */
        public RoundStartData(String word, int duration) {
            this.word = word;
            this.duration = duration;
        }

        public String getWord() {
            return word;
        }

        public int getDuration() {
            return duration;
        }
    }
}