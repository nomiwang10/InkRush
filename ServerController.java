import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//import for the server GUI
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.application.Platform;
import org.w3c.dom.Text;

//Import for timer creation
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Controller for InkRush game Server.
 * Manages up to 5 client connections with dedicated display areas for each client to show incoming game states.
 * Uses executor service for multithreaded client handling with JavaFX Task patter.
 * All GUI update are handled through the JavaFX Application Thread. The sockservers are threads for each client, performing tasks
 * and communicating back with the JFXAT to update the server GUI
 */
public class ServerController {
    private static final int PORT = 23596;
    private static final int MAX_CLIENTS = 5;

    @FXML
    private TextArea displayField1;

    @FXML
    private TextArea displayField2;

    @FXML
    private TextArea displayField3;

    @FXML
    private TextArea displayField4;

    @FXML
    private TextArea displayField5;

    private TextArea[] displayFields;
    private ExecutorService executor; // will run players
    private ServerSocket server; // server socket
    private SockServer[] sockServer; // Array of objects to be threaded
    private int counter = 1; // counter of number of connections
    private int nClientsActive = 0;
    private GameLogic gameLogic;
    private WordBank wordBank;
    private volatile int currentDrawerID = -1;
    private volatile int leaderID = -1; // track who is the leader
    private boolean gameStarted = false; // track game state
    private Timeline gameLoop;

    /**
     * Initialize the controller after FXML is loaded.
     * Sets up the display field array and ExecutorService
     */
    @FXML
    public void initialize() {
        displayFields = new TextArea[]{
                null, // index 0
                displayField1,
                displayField2,
                displayField3,
                displayField4,
                displayField5
        };

        // Initialize display fields
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            displayFields[i].setEditable(false);
            displayFields[i].setText("Waiting for client " + i + " . . . \n");
        }

        sockServer = new SockServer[MAX_CLIENTS + 1];
        executor = Executors.newFixedThreadPool(MAX_CLIENTS);
    }

    /**
     * Called by ServerApp after FXML is loaded to inject the WordBank dependency.
     * This is the official way to start GameLogic now.
     * @param bank the initialized WordBank object
     */
    public void setWordBankDependency(WordBank bank) {
        this.wordBank = bank;
        // GameLogic can only be initialized AFTER the WordBank is ready.
        this.gameLogic = new GameLogic(this.wordBank);
    }


    /**
     * Starts the server in a background thread.
     * Accept up to 5 client connections.
     */
    public void runServer() {
        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    server = new ServerSocket(PORT, MAX_CLIENTS);
                    displayMessageToAll("[Server] Server started on port " + PORT + "\n");

                    //Only create MAX_CLIENTS clients
                    while (counter <= MAX_CLIENTS) {
                        try {
                            sockServer[counter] = new SockServer(counter, displayFields[counter]);
                            sockServer[counter].waitForConnection();

                            synchronized (ServerController.this) {
                                nClientsActive++;

                                if (leaderID == -1) {
                                    leaderID = counter;
                                }
                            }

                            executor.execute(sockServer[counter]);
                        } catch (EOFException e) {
                            displayMessageToAll("[SERVER] Connection Terminated \n");
                        } finally {
                            counter++;
                        }
                    }

                    displayMessageToAll("[Server] Server full - " + MAX_CLIENTS + " clients connected.");
                } catch (IOException ioException) {
                    displayMessageToAll("[Server] Error: " + ioException.getMessage());
                }
            }
        };

        executor.execute(serverTask);
    }

    /**
     * Broadcasts message only to drawer and players who already guessed.
     *
     * @param message Message to broadcast
     */
    private void broadcastToGuessers(Message message) {
        int drawerID = gameLogic.getCurrentDrawerID();

        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (sockServer[i] != null && sockServer[i].alive) {
                if (i == drawerID || gameLogic.hasGuessedCorrectly(i)) {
                    sockServer[i].sendData(message);
                }
            }
        }
    }

    /**
     * Broadcasts a message to all active clients.
     * Thread-safe method for sending to all clients.
     *
     * @param message Message to be broadcasted
     */
    private void broadcastMessage(Message message) {
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (sockServer[i] != null && sockServer[i].alive) {
                sockServer[i].sendData(message);
            }
        }
    }

    /**
     * Broadcasts to all clients except one.
     * Used to display drawing on guesser screens.
     *
     * @param message   String to be broadcasted
     * @param excludeID Index of client not to send info to
     */
    private void broadcastExcept(Message message, int excludeID) {
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (i != excludeID && sockServer[i] != null && sockServer[i].alive) {
                sockServer[i].sendData(message);
            }
        }
    }

    /**
     * Displays a message in specific client's TextArea.
     * Thread-safe GUI update using Platform.runLater().
     *
     * @param clientID client display field to update (1 to MAX_CLIENTS)
     * @param message
     */
    private void displayMessage(final int clientID, final String message) {
        if (clientID < 1 || clientID > MAX_CLIENTS) {
            return;
        }

        Platform.runLater(() -> {
            displayFields[clientID].appendText(message);
        });
    }

    /**
     * Displays a message to all client fields in server gui
     *
     * @param message String to be displayed
     */
    private void displayMessageToAll(final String message) {
        Platform.runLater(() -> {
            for (int i = 1; i <= MAX_CLIENTS; i++) {
                displayFields[i].appendText(message);
            }
        });
    }

    /**
     * Begins phase 2 of the game flow: selecting the drawer and having them choose a word
     */
    private void startPhase2_WordSelection() {
        // Increment Round Tracking
        gameLogic.incrementRound();

        // Broadcast Round Number to Clients
        broadcastMessage(Message.createRoundUpdateMessage(gameLogic.getCurrentRound()));

        broadcastMessage(Message.createClearMessage());

        // startNewRound() returns {DrawerID, W1, W2, W3}
        String[] roundInfo = gameLogic.startNewRound();

        if(roundInfo == null || roundInfo.length < 4) return; // Error or not enough players

        currentDrawerID = Integer.parseInt(roundInfo[0]); // Get the assigned drawer ID

        String drawerName = gameLogic.getPlayerUsername(currentDrawerID);

        // Get the word options array: {W1, W2, W3}
        String[] options = new String[3];
        System.arraycopy(roundInfo, 1, options, 0, 3);

        // notify the Drawer with options
        if (sockServer[currentDrawerID] != null) {
            Message optionsMsg = Message.createWordOptionsMessage(options);
            sockServer[currentDrawerID].sendData(optionsMsg);

            // Also tell them they are the drawer (unlocks their pen)
            sockServer[currentDrawerID].sendData(Message.createDrawerAssignedMessage());
        }

        // notify everyone else to wait
        broadcastExcept(Message.createChatMessage("SERVER", "Waiting for " + drawerName + " to choose a word..."), currentDrawerID);
        displayMessageToAll("[Phase 2] Waiting for Client " + currentDrawerID + " to choose a word.\n");
    }

    /**
     * Begins phase 3 of the game flow: transitioning rom selecting word to guessing state
     */
    private void startPhase3_RoundStart() {
        String word = gameLogic.getCurrentWord();
        String hint = gameLogic.getWordHint();

        gameLogic.resetTimer();

        // 1. Send the ACTUAL WORD to the Drawer
        // format: ROUND_START:apple:60
        if (sockServer[currentDrawerID] != null) {
            Message drawerMsg = Message.createRoundStartMessage(word, 60);
            sockServer[currentDrawerID].sendData(drawerMsg);
        }

        // 2. Send the HINT to everyone else
        // format: ROUND_START:_ _ _ _ _:60
        Message guesserMsg = Message.createRoundStartMessage(hint, 60);
        broadcastExcept(guesserMsg, currentDrawerID);

        // 3. Log to Server GUI
        displayMessageToAll("[Phase 3] Round Started! Word: " + word + "\n");

        //4 Start Game Loop (Check time every 1 second)
        startGameLoopMonitor();
    }

    /**
     * Monitors the game state every 1 second using JavaFX Timeline.
     * Checks for time expiration or if everyone has guessed.
     */
    private void startGameLoopMonitor() {
        // Stop any existing loop to be safe
        if (gameLoop != null) {
            gameLoop.stop();
        }

        // Create a loop that runs every 1 second
        gameLoop = new Timeline(new KeyFrame(Duration.seconds(1), event -> {

            // Check 1: Is Time Up?
            if (gameLogic.isTimeUp()) {
                endRoundAndRotate("Time is up!");
                return;
            }

            // Check 2: Did everyone (except drawer) guess?
            // only check this if there are at least 2 players
            if (gameLogic.getPlayerCount() > 1 &&
                    gameLogic.getGuessCount() >= gameLogic.getPlayerCount() - 1) {

                endRoundAndRotate("Everyone guessed correctly!");
            }
        }));

        gameLoop.setCycleCount(Animation.INDEFINITE);
        gameLoop.play();
    }

    /**
     * Ends the round, shows scores, and schedules the next drawer.
     */
    private void endRoundAndRotate(String reason) {
        if (!gameLogic.isRoundActive()) return;

        // 1. Stop the loop
        if (gameLoop != null) {
            gameLoop.stop();
        }

        // 2. Logic Cleanup
        String currentWord = gameLogic.getCurrentWord();
        gameLogic.endRound();

        // 3. Notify Clients
        displayMessageToAll("[Server] Round Over: " + reason + "\n");

        // Send Chat Notification
        Message endMsg = Message.createChatMessage("SERVER", reason + " Word was: " + currentWord);
        broadcastMessage(endMsg);

        // Send Leaderboard Update
        broadcastLeaderboard();

        // 4. check if gae over / intermission
        if (gameLogic.isGameComplete()) {
            handleGameOver(); // Helper method defined below
        }else{
            PauseTransition intermission = new PauseTransition(Duration.seconds(5));
            intermission.setOnFinished(event -> {startPhase2_WordSelection();});
            intermission.play();
        }
    }

    /**
     * handleGameOver method
     * Announces the winner and then calls method to transition to phase 1
     */
    private void handleGameOver() {
        displayMessageToAll("[Server] Game Over! Calculating final scores...\n");

        // Stop the game loop if running
        if (gameLoop != null) {
            gameLoop.stop();
        }

        StringBuilder sb = new StringBuilder();
        List<GameLogic.PlayerInfo> leaders = gameLogic.getLeaderboard();

        for (GameLogic.PlayerInfo p : leaders) {
            if (sb.length() > 0) sb.append(":");
            sb.append(p.getUsername()).append(":").append(p.getScore());
        }
        String finalLeaderboardData = sb.toString();

        broadcastMessage(Message.createGameOverMessage(finalLeaderboardData));
        displayMessageToAll("[Server] Game Over message sent. Data: " + finalLeaderboardData + "\n");

        PauseTransition gameOverDelay = new PauseTransition(Duration.seconds(10));
        gameOverDelay.setOnFinished(event -> {
            resetGameToLobby();
        });
        gameOverDelay.play();
    }

    /**
     * resetGameToLobby method
     * resets the game state for users to go back to phase 1, the pre game.
     */
    private void resetGameToLobby() {
        // 1. Reset Logic
        gameLogic.resetGame();
        gameStarted = false;
        currentDrawerID = -1;

        displayMessageToAll("[Server] Resetting to lobby...\n");

        // 2. Clear Clients
        broadcastMessage(Message.createClearMessage());
        broadcastMessage(Message.createChatMessage("SERVER", "Lobby has been reset. Waiting for leader..."));

        // 3. Reset Scores to 0 for all clients
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (sockServer[i] != null && sockServer[i].alive) {
                sockServer[i].sendData(Message.createScoreMessage(0));
            }
        }
        broadcastMessage(Message.createLeaderboardMessage(""));

        // 4. Re-enable the "Start Game" button for the leader
        synchronized(this) {
            if (leaderID != -1 && sockServer[leaderID] != null) {
                sockServer[leaderID].sendData(Message.createLeaderMessage());
                displayMessage(leaderID, "You can start a new game now.\n");
            }
        }

        displayMessageToAll("[Server] Lobby reset complete. Waiting for leader to start new game.\n");
    }

    /**
     * formatting the leaderboard data and broadcasting it.
     */
    private void broadcastLeaderboard() {
        // Get sorted list from GameLogic
        var leaders = gameLogic.getLeaderboard();

        // Build string: "Name1,Score1,Name2,Score2..."
        StringBuilder sb = new StringBuilder();
        for (var p : leaders) {
            if (sb.length() > 0) sb.append(",");
            sb.append(p.getUsername()).append(",").append(p.getScore());
        }

        // Send using LEADERBOARD message type
        broadcastMessage(Message.createLeaderboardMessage(sb.toString()));
    }

    /* This new Inner Class implements Runnable and objects instantiated from this
     * class will become server threads each serving a different client
     */

    /**
     * SockServer inner class implements Runnable for handling a single client connection.
     * Each client gets its own dedicated TextArea for displaying activity.
     * Workflow:
     * 1. Client connects, waitForConnection completes
     * 2. ExecutorService while run() method runs in background thread
     * 3. getStreams sets up the I/O with server/client
     * 4. processConnection() loop waiting for receiving messages
     * 5. Client disconnects, closeConnection() cleans up
     */
    private class SockServer implements Runnable {
        private ObjectOutputStream output; // output stream to client
        private ObjectInputStream input; // input stream from client
        private Socket connection; // connection to client
        private final int myConID;
        private final TextArea myDisplay;
        private volatile boolean alive = false;

        /**
         * Creates the handler for specific client
         *
         * @param counterIn    connection id of client
         * @param displayField TextArea associated with client
         */
        public SockServer(int counterIn, TextArea displayField) {
            myConID = counterIn;
            myDisplay = displayField;
        }

        /**
         * Main execution method running in background thread.
         * Handles entire client connection workflow.
         */
        public void run() {
            alive = true;
            try {
                getStreams(); // get input & output streams
                processConnection(); // process connection
            } // end try
            catch (EOFException eofException) {
                displayMessage(myConID, "Client " + myConID + " terminated connection");
            } catch (Exception e) {
                displayMessage(myConID, "Error:  " + e.getMessage() + "\n");
            } finally {
                synchronized (ServerController.this) {
                    nClientsActive--;
                }
                closeConnection(); //  close connection
            }
        }

        /**
         * Wait for client to connect.
         * Called before task execution.
         *
         * @throws IOException if connection fails
         */
        private void waitForConnection() throws IOException {
            displayMessage(myConID, "Waiting for Client " + myConID + " . . .\n");
            connection = server.accept(); // allow server to accept connection
            displayMessage(myConID, "Client " + myConID + " connected from: " +
                    connection.getInetAddress().getHostName());
        }

        /**
         * Sets up input and output streams for communication
         *
         * @throws IOException if stream creation fails
         */
        private void getStreams() throws IOException {
            // set up output stream for objects
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush(); // flush output buffer to send header information

            // set up input stream for objects
            input = new ObjectInputStream(connection.getInputStream());

            displayMessage(myConID, "Streams ready for Client " + myConID + " . . .\n");
        }

        /**
         * Main message processing loop.
         * Runs continuously, blocking at readObject() until messages arrive
         * Message flow:
         * 1. Client draws and sends info message
         * 2. this method retrieves it and logs to display field
         * 3. handleGameMessage() processes it
         * 4. broadcastExcept() sends to other clients
         * 5. Loop continues, waiting for next message
         *
         * @throws IOException if communication fails
         */
        private void processConnection() throws IOException {
            //Send CONNECTED Message to client
            sendData(Message.createConnectedMessage(myConID));
            synchronized (ServerController.this) {
                if (myConID == leaderID) {
                    sendData(Message.createLeaderMessage());
                    displayMessage(myConID, "Client " + myConID + " assigned as leader");
                }
            }

            while (alive) {
                try {
                    //Blocks here waiting for client message
                    Message message = (Message) input.readObject();

                    // Check for termination
                    if (message.getMessageType().equals(Message.TERMINATE)) {
                        displayMessage(myConID, "Client " + myConID + " terminated connection");
                        break;
                    }

                    // Log received message to this client's display
                    displayMessage(myConID, "RECV: " + message + "\n");

                    // Process the game message
                    handleGameMessage(message);

                } catch (ClassNotFoundException e) {
                    displayMessage(myConID, "ERROR: Unknown object type \n");
                }
            }
        }

        /**
         * Handles different type of game messages.
         * Routes messages accordingly based on type.
         * Message Protocol handled by Message class
         *
         * @param message String message to handle
         */
        private void handleGameMessage(Message message) {
            String messageType = message.getMessageType();

            //Chat message, broadcast to all
            if (messageType.equals(Message.CHAT)) {
                Message.ChatData chatData = message.parseChatMessage();
                displayMessage(myConID, "Broadcasting chat from " + chatData.getUsername() + "\n");
                broadcastMessage(message);
            } else if (messageType.equals(Message.DRAW)) {
                if (myConID != currentDrawerID) {
                    displayMessage(myConID, "REJECTED: Client " + myConID + " is not the drawer!\n");
                    return;
                }
                displayMessage(myConID, "Broadcasting drawing point\n");
                broadcastExcept(message, myConID);

            } else if (messageType.equals(Message.START_GAME)) {
                if(gameStarted) return;

                if (myConID == leaderID) {
                    displayMessageToAll("[Server] Leader started the game!\n");
                    gameStarted = true;

                    startPhase2_WordSelection();
                }
            } else if (messageType.equals(Message.WORD_SELECTED)) {
                // Only the drawer can select a word
                if (myConID != currentDrawerID) return;

                String chosenWord = message.getMessageContents();

                Platform.runLater(()->{
                    // IMPORTANT: Use the new validation method. This sets the word and handles drawer rotation.
                    if (gameLogic.validateAndSetWord(chosenWord)) {
                        // Start Phase 3 ONLY if the chosen word was valid
                        broadcastMessage(Message.createClearMessage());
                        startPhase3_RoundStart();
                    } else {
                        displayMessage(myConID, "ERROR: Invalid word selection received.\n");
                    }
                });
            } else if (messageType.equals(Message.GUESS)) {
                Message.GuessData guessData = message.parseGuessMessage();
                String username = guessData.getUsername();
                String guess = guessData.getGuess();

                displayMessage(myConID, username + ": " + guess + "\n");

                // Drawer can always chat
                if (myConID == gameLogic.getCurrentDrawerID()) {
                    Message drawerChat = Message.createChatMessage(username, guess);
                    broadcastMessage(drawerChat);
                    return;
                }

                // Already guessed - hide from non-guessers
                if (gameLogic.hasGuessedCorrectly(myConID)) {
                    broadcastToGuessers(Message.createChatMessage(username, guess));
                    return;
                }

                // Check if correct
                if (gameLogic.checkGuess(guess)) {
                    int points = gameLogic.awardPoints(myConID);
                    int totalScore = gameLogic.getPlayerScore(myConID);

                    Message scoreMsg = Message.createScoreMessage(totalScore);
                    sockServer[myConID].sendData(scoreMsg);

                    int drawerID = currentDrawerID;

                    // Safety check: ensure drawer is still connected before sending score
                    if (drawerID != -1 && sockServer[drawerID] != null && sockServer[drawerID].alive) {
                        int drawerTotal = gameLogic.getPlayerScore(drawerID);
                        sockServer[drawerID].sendData(Message.createScoreMessage(drawerTotal));
                    }

                    Message toGuesser = Message.createChatMessage("SERVER",
                            "You guessed the word! +" + points + " points");
                    sockServer[myConID].sendData(toGuesser);

                    Message toOthers = Message.createChatMessage("SERVER",
                            username + " guessed the word!");
                    broadcastExcept(toOthers, myConID);

                    broadcastLeaderboard();

                    if (gameLogic.getGuessCount() >= gameLogic.getPlayerCount() - 1) {
                        Platform.runLater(() -> endRoundAndRotate("Everyone guessed correctly!"));
                    }
                } else {
                    Message wrongGuess = Message.createChatMessage(username, guess);
                    broadcastMessage(wrongGuess);
                }
            } else if (messageType.equals(Message.CLEAR)) {
                if (myConID != currentDrawerID) {
                    displayMessage(myConID, "REJECTED: Client " + myConID + " is not the drawer!\n");
                    return;
                }
                displayMessage(myConID, "Broadcasting canvas clear\n");
                broadcastMessage(message);

            } else if (messageType.equals(Message.USERNAME)) {
                String username = message.getMessageContents();
                gameLogic.addPlayer(myConID, username);
                displayMessage(myConID, "Player registered: " + username + "\n");

                // Only start a round if no round is currently active
                // The new game flow requires the LEADER to send a START_GAME message,
                // so we only handle players joining mid-round here.
                if (gameLogic.isRoundActive()) {
                    // Player joined mid-round, send them current round info
                    String word = gameLogic.getWordHint();
                    Message roundMsg = Message.createRoundStartMessage(word, gameLogic.getTimeRemaining());
                    sockServer[myConID].sendData(roundMsg);

                    displayMessage(myConID, username + " joined ongoing round as guesser\n");
                }
            }
        }

        /**
         * Closes connection and cleans up resources
         */
        private void closeConnection() {
            displayMessage(myConID, "\nTerminating connection " + myConID + "\n");

            gameLogic.removePlayer(myConID);

            // mark this thread as dead immediately so the leader search loop skips it
            alive = false;

            // new leader assignment logic
            synchronized (ServerController.this) {
                if (myConID == leaderID) {
                    leaderID = -1;

                    if (nClientsActive > 0) {
                        displayMessageToAll("[Server] Leader disconnected. Looking for new leader...\n");

                        // finds the next available active client
                        for (int i = 1; i <= MAX_CLIENTS; i++) {
                            if (sockServer[i] != null && sockServer[i].alive && i != myConID) {
                                leaderID = i; // finds the new leader

                                // notifies the new leader
                                sockServer[i].sendData(Message.createLeaderMessage());
                                displayMessageToAll("[Server] Client " + i + " is the new Leader\n");
                                break;
                            }
                        }
                    } else {
                        // no players left
                        displayMessageToAll("[Server] No players remaining. Resetting leader.\n");
                    }
                }
            }

            displayMessage(myConID, "\nNumber of connections = " + nClientsActive + "\n");

            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (IOException e) {
                displayMessage(myConID, "Error closing connection " + e.getMessage() + "\n");
            }
        }

        /**
         * Sends data to this specific client.
         * Synchronized to prevent concurrent write conflicts
         *
         * @param message String message to be sent
         */
        private void sendData(Message message) {
            try // send object to client
            {
                synchronized (output) {
                    output.writeObject(message);
                    output.flush();
                }
                displayMessage(myConID, "SENT: " + message.toString() + "\n");
            } catch (IOException ioException) {
                displayMessage(myConID, "ERROR sending: " + ioException.getMessage() + "\n");
            }
        }
    }
}