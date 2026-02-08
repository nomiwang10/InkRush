import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * GameLogic class manages the core game mechanics for the game.
 * Handles word selection, guess validation, scoring, rounds, and timing.
 * The way the Game Flow works is as follows:
 * 1- Round starts and drawer gets selected as well as word getting chosen
 * 2- Timer starts tracking round duration
 * 3- Players submit guesses
 * 4- GameLogic validates the guesses against the current word, correct guess gets awarded points
 * 6- Round ends after time expires or correct guess
 * 7- Next drawer gets selected and the whole flow repeats
 */
public class GameLogic {
    private static final int ROUND_DURATION_SECONDS = 60;

    private String currentWord;
    private int currentDrawerID;
    private long roundStartTime;
    private boolean roundActive;

    // player tracking
    private Map<Integer, PlayerInfo> players;
    private List<Integer> playerOrder;
    private int currentDrawerIndex;

    // track who has guessed correctly this round
    private List<Integer> correctGuessers;
    private int guessCount;

    // Word Bank Dependency
    private WordBank wordBank; // Dependency Injection
    private String[] currentWordOptions; // store the 3 choices
    private Map<String, Integer> wordUsageCount;


    // Round logic vars
    private int currentRound = 0;
    private static final int MAX_ROUNDS = 5;

    /**
     * Creates a new GameLogic instance.
     * Initializes player tracking and game state.
     * @param wordBank the initialized WordBank instance for fetching words
     */
    public GameLogic(WordBank wordBank) {
        this.wordBank = wordBank;

        players = new HashMap<>();
        playerOrder = new ArrayList<>();
        correctGuessers = new ArrayList<>();
        roundActive = false;
        currentDrawerIndex = 0;
        guessCount = 0;
        currentWordOptions = null; // Initialize options field
        wordUsageCount = new HashMap<>();
    }
    /**
     * Registers a new player in the game.
     * @param clientID the client's unique ID
     * @param username the player's username
     */
    public void addPlayer(int clientID, String username) {
        PlayerInfo player = new PlayerInfo(clientID, username);
        players.put(clientID, player);
        playerOrder.add(clientID);
    }

    /**
     * Removes a player from the game
     * @param clientID the client ID to remove
     */
    public void removePlayer(int clientID) {
        players.remove(clientID);
        playerOrder.remove(Integer.valueOf(clientID));

        if (!playerOrder.isEmpty() && currentDrawerIndex >= playerOrder.size()) {
            currentDrawerIndex = 0;
        }
    }

    /**
     * Starts a new round flow by selecting the next drawer and retrieving 3 random word options.
     * @return a String array containing {DrawerID, WordOption1, WordOption2, WordOption3}
     * or null if not enough players.
     */
    public String[] startNewRound() {
        if (playerOrder.isEmpty()) {
            return null;
        }

        if (currentDrawerIndex >= playerOrder.size()) {
            currentDrawerIndex = 0;
        }

        // select next drawer (rotates through all players)
        currentDrawerID = playerOrder.get(currentDrawerIndex);

        // Create a list of words that have reached the usage limit
        List<String> wordsToExclude = new ArrayList<>();
        int USAGE_LIMIT = 1;

        for (Map.Entry<String, Integer> entry : wordUsageCount.entrySet()) {
            if (entry.getValue() >= USAGE_LIMIT) {
                wordsToExclude.add(entry.getKey());
            }
        }

        // Get the three word choices from the database
        List<String> chosenList = wordBank.getThreeWords(wordsToExclude);

        if (chosenList.size() < 3) {
            System.out.println("[GameLogic] Word pool exhausted. Resetting usage history.");

            // Clear the history map
            wordUsageCount.clear();

            // Clear the local exclusion list we just built
            wordsToExclude.clear();

            // Fetch again with a fresh slate
            chosenList = wordBank.getThreeWords(wordsToExclude);
        }

        currentWordOptions = chosenList.toArray(new String[0]);

        // NOTE: The currentWord is NOT set here. It is set by the drawer's choice.

        // start timer (Round is active once a word is selected and validated)
        roundStartTime = System.currentTimeMillis();
        roundActive = true; // Mark round active to prevent mid-round game starts

        // reset guess tracking for new round
        correctGuessers.clear();
        guessCount = 0;

        // Package and return the necessary information for the server
        String[] roundInfo = new String[4];
        roundInfo[0] = String.valueOf(currentDrawerID);
        System.arraycopy(currentWordOptions, 0, roundInfo, 1, 3);

        return roundInfo;
    }

    /**
     * updates the round
     */
    public void incrementRound() {
        currentRound++;
    }

    /**
     * Round getter
     * @return int current round
     */
    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * max rounds getter
     * @return int max rounds
     */
    public int getMaxRounds() {
        return MAX_ROUNDS;
    }

    /**
     * Checks if the game has reached the round limit.
     */
    public boolean isGameComplete() {
        return currentRound >= MAX_ROUNDS;
    }

    /**
     * Resets the game state for a fresh start.
     */
    public void resetGame() {
        currentRound = 0;
        resetScores();
        endRound(); // Ensure active flags are cleared
    }

    /**
     * Determines the winner based on the highest score.
     * @return String description of the winner
     */
    public String getWinnerDescription() {
        List<PlayerInfo> leaderboard = getLeaderboard();
        if (leaderboard.isEmpty()) return "No players.";

        PlayerInfo winner = leaderboard.get(0);
        return winner.getUsername() + " with " + winner.getScore() + " points!";
    }

    /**
     * Adds specific points to a specific player.
     * Used by ServerController for time-based scoring.
     * @param clientID the client ID to update
     * @param pointsToAdd amount of points to add
     */
    public void addScore(int clientID, int pointsToAdd) {
        if (players.containsKey(clientID)) {
            PlayerInfo p = players.get(clientID);
            p.addScore(pointsToAdd);
        }
    }

    /**
     * Validates that the chosen word is one of the available options and sets it as the current word.
     * This method also handles drawer rotation for the NEXT round.
     * @param word the word chosen by the drawer
     * @return true if the word was valid and set, false otherwise.
     */
    public boolean validateAndSetWord(String word) {
        if (currentWordOptions == null || currentWordOptions.length == 0) {
            return false;
        }

        // Check if the chosen word is actually one of the three options offered
        for (String option : currentWordOptions) {
            if (option.equalsIgnoreCase(word.trim())) {
                this.currentWord = word.trim(); // Set the final word
                //track how many times the same word has been used
                int currentCount = wordUsageCount.getOrDefault(this.currentWord, 0);
                wordUsageCount.put(this.currentWord, currentCount + 1);
                this.currentWordOptions = null; // Clear options

                // Rotation must happen AFTER the word is chosen for the current round
                currentDrawerIndex = (currentDrawerIndex + 1) % playerOrder.size();
                return true;
            }
        }
        return false;
    }
    /**
     * Checks if a guess is correct.
     * This is going to be case-insensitive comparison with the current word.
     * @param guess the player's guess
     * @return true if guess matches current word, false otherwise
     */
    public boolean checkGuess(String guess) {
        if (!roundActive || currentWord == null) {
            return false;
        }
        return currentWord.equalsIgnoreCase(guess.trim());
    }

    /**
     * Awards points to a player and the drawer based on the time remaining.
     * The faster the guess, the higher the points for both.
     * @param guesserID the ID of player who guessed correctly
     * @return the points awarded
     */
    public int awardPoints(int guesserID) {
        // check if this player already guessed correctly
        if (correctGuessers.contains(guesserID)) {
            return 0;
        }
        // check if guesser is the drawer (can't guess your own word!)
        if (guesserID == currentDrawerID) {
            return 0;
        }

        PlayerInfo guesser = players.get(guesserID);
        PlayerInfo drawer = players.get(currentDrawerID);

        if (guesser == null) {
            return 0;
        }

        // Points equal the seconds remaining on the clock
        int pointsAwarded = getTimeRemaining();

        // 1. Award points to the guesser
        guesser.addScore(pointsAwarded);

        // 2. Award the same amount to the drawer
        // This incentivizes the drawer to draw quickly and clearly
        if (drawer != null) {
            drawer.addScore(pointsAwarded);
        }

        // track this guesser
        correctGuessers.add(guesserID);
        guessCount++;

        return pointsAwarded;
    }

    /**
     * Checks if a player has already guessed correctly this round.
     * @param clientID the client ID to check
     * @return true if already guessed correctly, false otherwise
     */
    public boolean hasGuessedCorrectly(int clientID) {
        return correctGuessers.contains(clientID);
    }

    /**
     * Gets the current guess count (how many have guessed correctly)
     * @return number of correct guessers so far
     */
    public int getGuessCount() {
        return guessCount;
    }

    /**
     * Ends the current round.
     * Clears round state and guess tracking.
     * NOTE: The drawer index is advanced in validateAndSetWord() *before* the round starts drawing.
     */
    public void endRound() {
        roundActive = false;
        currentWord = null;
        correctGuessers.clear();
        guessCount = 0;
    }

    /**
     * Gets the ID of who will draw in the next round.
     * Useful for previewing next drawer without ending current round.
     * @return the next drawer's client ID
     */
    public int getNextDrawerID() {
        if (playerOrder.isEmpty()) {
            return -1;
        }
        int nextIndex = (currentDrawerIndex + 1) % playerOrder.size();
        return playerOrder.get(nextIndex);
    }

    /**
     * Gets the time remaining in the current round.
     * @return seconds remaining, of 0 if round not active
     */
    public int getTimeRemaining() {
        if (!roundActive) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedMillis = currentTime - roundStartTime;
        long elapsedSeconds = elapsedMillis / 1000;

        int remaining = ROUND_DURATION_SECONDS - (int) elapsedSeconds;
        return Math.max(0, remaining);
    }

    /**
     * Checks if the round time has expired
     * @return true if time is up, false otherwise
     */
    public boolean  isTimeUp() {
        return roundActive && getTimeRemaining() <= 0;
    }

    /**
     * Gets the current word being drawn
     * @return the current word
     */
    public String getCurrentWord() {
        return currentWord;
    }

    /**
     * Gets a hint for the current word.
     * Letters become "_ " and spaces become "   " (triple space) to visually separate words.
     * @return hint string
     */
    public String getWordHint() {
        if (currentWord == null) {
            return "";
        }

        StringBuilder hint = new StringBuilder();
        for (int i = 0; i < currentWord.length(); i++) {
            char c = currentWord.charAt(i);

            if (c == ' ') {
                // If the character is a space, add a wide gap (3 spaces)
                // This makes "alarm clock" look like "_ _ _ _ _   _ _ _ _ _"
                hint.append("   ");
            } else {
                // If it is a letter, add an underscore and a space
                hint.append("_ ");
            }
        }
        return hint.toString().trim();
    }

    /**
     * Resets the round timer to the full duration.
     * Called when the final word is chosen and drawing begins.
     */
    public void resetTimer() {
        // Reset the start time to the current time to restart the 60-second duration
        roundStartTime = System.currentTimeMillis();
    }

    /**
     * Gets the current drawer's ID
     * @return drawer's client ID
     */
    public int getCurrentDrawerID() {
        return currentDrawerID;
    }

    /**
     * Checks if a round is currently active.
     * @return true if round is active, false otherwise
     */
    public boolean isRoundActive() {
        return roundActive;
    }

    /**
     * Gets a player's current score
     * @param clientID the client ID
     * @return the player's score, or 0 if not found
     */
    public int getPlayerScore(int clientID) {
        PlayerInfo player = players.get(clientID);
        return player != null ? player.getScore() : 0;
    }

    /**
     * Gets a player's username.
     * @param clientID the client ID
     * @return the player's username, or null if not found
     */
    public String getPlayerUsername(int clientID) {
        PlayerInfo player = players.get(clientID);
        return player != null ? player.getUsername() : null;
    }

    /**
     * Gets the leaderboard sorted by score (highest first)
     * @return list of players sorted by score descending
     */
    public List<PlayerInfo> getLeaderboard() {
        List<PlayerInfo> leaderboard = new ArrayList<>(players.values());

        // Sort by score descending
        leaderboard.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));

        return leaderboard;
    }

    /**
     * Gets the number of active players
     * @return number of players
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Resets all player scores to zero.
     */
    public void resetScores() {
        for (PlayerInfo player : players.values()) {
            player.resetScore();
        }
    }

    /**
     * Inner class representing a player's information.
     */
    public static class PlayerInfo {
        private int clientID;
        private String username;
        private int score;

        /**
         * Creates a new player info object
         * @param clientID the client's unique ID
         * @param username the player's username
         */
        public PlayerInfo(int clientID, String username) {
            this.clientID = clientID;
            this.username = username;
            this.score = 0;
        }

        /**
         * Gets the client ID
         * @return the client ID
         */
        public int getClientID() {
            return clientID;
        }

        /**
         * Gets the username
         * @return the username
         */
        public String getUsername() {
            return username;
        }

        /**
         * Gets the current score
         * @return the score
         */
        public int getScore() {
            return score;
        }

        /**
         * Adds points to the player's score
         * @param points the points to add
         */
        public void addScore(int points) {
            score += points;
        }

        /**
         * Resets the score to zero.
         */
        public void resetScore() {
            score = 0;
        }
    }
}