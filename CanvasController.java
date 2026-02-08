import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

//using Interpolation to solve the losing packet while drawing issue
import javafx.animation.AnimationTimer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javafx.scene.image.Image;

import javafx.scene.media.AudioClip; // For short sound effects (wav)
import javafx.scene.media.Media;     // For music media
import javafx.scene.media.MediaPlayer; // For music player controls
import java.net.URL;



/**
 * Controller for the InkRush game client.
 * Handles user input, server communication, and GUI updates.
 *
 * NETWORK COMMUNICATION:
 * - Connects to server on initialization using dynamic IP from Lobby
 * - Sends messages via ObjectOutputStream
 * - Receives messages via background thread with ObjectInputStream
 * - All GUI updates use Platform.runLater() for thread safety
 *
 * ERROR HANDLING:
 * - Implements graceful failure for connection timeouts (e.g., Server Full).
 * - Handles UnknownHostException for invalid IP addresses.
 */
public class CanvasController {
    @FXML
    private Button chatButton;
    @FXML
    private TextArea chatTextArea;
    @FXML
    private TextField chatTextInput;
    @FXML
    private Button clearButton;
    @FXML
    private Canvas drawingCanvas;

    @FXML
    private Label nameLabel;
    @FXML
    private Label wordLabel;
    @FXML
    private Label roundLabel;

    // FXML controls
    @FXML private ColorPicker colorPicker;
    @FXML private Slider sizeSlider;
    @FXML private Label sizeValueLabel;

    // Top Bar Controls
    @FXML private Button startGame;
    @FXML private Label timerLabel;
    @FXML private Label scoreLabel;
    @FXML private Label wordToGuessLabel;

    // Word Choice Buttons
    @FXML private Button WordOption1;
    @FXML private Button WordOption2;
    @FXML private Button WordOption3;

    // Leaderboard Labels
    @FXML private Label leaderboardSpot1;
    @FXML private Label leaderboardSpot2;
    @FXML private Label leaderboardSpot3;
    @FXML private Label leaderboardSpot4;
    @FXML private Label leaderboardSpot5;
    @FXML
    private Canvas avatarCanvas;

    //Use dynamic IP logic
    private static final int SERVER_PORT = 23596;
    private String serverIP = "localhost"; // Default, overwritten by Lobby

    private Socket connection;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private ExecutorService executor;
    private String username = "Guest";
    private volatile boolean connected = false;

    // Drawing tracking variables
    private double lastX;
    private double lastY;
    private boolean remoteFirstPoint = true;
    private volatile boolean canDraw = false;

    // Dynamic brush state
    private Color currentColor = Color.BLACK;
    private double currentBrushSize = 4.0;

    //Used for round timer
    private AnimationTimer roundTimer;
    private long timerEndTime;

    // Audio fields
    private MediaPlayer gameMusicPlayer;
    private AudioClip correctSound;

    // Animation Queue: Stores points to be drawn smoothly
    // Wrapper class to track if a point is the start of a new stroke
    private static class PointRequest {
        Message.DrawData data;
        boolean isNewStroke;

        PointRequest(Message.DrawData data, boolean isNewStroke) {
            this.data = data;
            this.isNewStroke = isNewStroke;
        }
    }

    // Queue stores PointRequest instead of raw DrawData
    private Queue<PointRequest> pointQueue = new ConcurrentLinkedQueue<>();
    private AnimationTimer drawingLoop;

    // Time tracking to detect pen lifts (gaps in receiving data)
    private long lastReceivedTime = 0;

    // pointer tracer
    private double currentAnimX;
    private double currentAnimY;

    // How fast the animation catches up
    private static final double SMOOTHING_SPEED = 0.9;


    /**
     * Sets the connection information (Name and IP) from the Lobby.
     * Automatically triggers the connection attempt.
     *
     * @param name The player's username
     * @param ip   The IP address to connect to
     */
    public void setConnectionInfo(String name, String ip, Image avatar) {
        this.username = name;
        this.serverIP = ip;

        if (nameLabel != null) {
            nameLabel.setText(name);
        }

        // Handle the Avatar
        if (avatarCanvas != null && avatar != null) {
            GraphicsContext gc = avatarCanvas.getGraphicsContext2D();

            // Clear the canvas make it white/transparent first
            gc.clearRect(0, 0, avatarCanvas.getWidth(), avatarCanvas.getHeight());

            // Draw the image passed from the Lobby
            gc.drawImage(avatar, 0, 0, avatarCanvas.getWidth(), avatarCanvas.getHeight());
        }

        // Start the connection attempt now that we have the IP
        connectToServer();
    }

    /**
     * This class sets up the audio
     */
    private void setupAudio() {
        try {
            // 1. Load Background Music (MP3)
            URL musicUrl = getClass().getResource("game_background.mp3");
            if (musicUrl != null) {
                Media media = new Media(musicUrl.toExternalForm());
                gameMusicPlayer = new MediaPlayer(media);
                gameMusicPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Loop forever
                gameMusicPlayer.setVolume(0.3); // Set volume to 30%
                gameMusicPlayer.play(); // Start immediately
            } else {
                System.out.println("Warning: game_background.mp3 not found");
            }

            // 2. Load "Correct Guess" Sound Effect (WAV)
            URL soundUrl = getClass().getResource("your_correct.wav");
            if (soundUrl != null) {
                correctSound = new AudioClip(soundUrl.toExternalForm());
            } else {
                System.out.println("Warning: your_correct.wav not found");
            }

        } catch (Exception e) {
            System.out.println("Error loading audio: " + e.getMessage());
        }
    }
    /**
     * Initializes the controller after FXML is loaded.
     * Sets up event handlers for drawing and UI controls.
     */
    @FXML
    public void initialize() {
        executor = Executors.newFixedThreadPool(1);

        // Initialize color + size UI state (if controls are present)
        if (colorPicker != null) {
            colorPicker.setValue(currentColor);
            colorPicker.setOnAction(e -> changeColor());
        }

        if (sizeSlider != null) {
            sizeSlider.setMin(1);
            sizeSlider.setMax(20);
            sizeSlider.setValue(currentBrushSize);
            sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> changeBrushSize());
        }

        if (sizeValueLabel != null) {
            sizeValueLabel.setText("Brush: " + (int) currentBrushSize + "px");
        }

        if (startGame != null) {
            startGame.setDisable(true);
            startGame.setOnAction(e -> onStartGameClicked());
        }

        if (WordOption1 != null) {
            WordOption1.setVisible(false);
            WordOption2.setVisible(false);
            WordOption3.setVisible(false);
        }

        setupDrawing();

        //start the animation loop
        startSmoothDrawingLoop();

        //set up audio
        setupAudio();
    }

    private void onStartGameClicked() {
        if (connected) {
            sendToServer(Message.createStartGameMessage());
            startGame.setDisable(true);
        }
    }

    /**
     * Called when colorPicker changes.
     */
    @FXML
    private void changeColor() {
        if (colorPicker != null) {
            currentColor = colorPicker.getValue();
        }
    }

    /**
     * Called when sizeSlider changes.
     */
    @FXML
    private void changeBrushSize() {
        if (sizeSlider != null) {
            currentBrushSize = sizeSlider.getValue();
            if (sizeValueLabel != null) {
                sizeValueLabel.setText("Brush: " + (int) currentBrushSize + "px");
            }
        }
    }

    /**
     * Sets up drawing for local drawing and broadcasts drawing data to server.
     */
    private void setupDrawing() {
        var gc = drawingCanvas.getGraphicsContext2D();
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        drawingCanvas.setOnMousePressed(event -> {
            if(!canDraw) return;

            lastX = event.getX();
            lastY = event.getY();

            // SEND IMMEDIATELY on click so other clients see the start point instantly
            if(connected) {
                Message drawMessage = Message.createDrawMessage(
                        lastX, lastY, colorToHex(currentColor), currentBrushSize
                );
                sendToServer(drawMessage);
            }

            // Draw locally
            gc.setLineWidth(currentBrushSize);
            gc.setStroke(currentColor);
            gc.strokeLine(lastX, lastY, lastX, lastY);
        });

        drawingCanvas.setOnMouseDragged(event -> {
            if(!canDraw) return;

            double x = event.getX();
            double y = event.getY();

            gc.setLineWidth(currentBrushSize);
            gc.setStroke(currentColor);
            gc.strokeLine(lastX, lastY, x, y);

            if(connected) {
                Message drawMessage = Message.createDrawMessage(
                        lastX, lastY, colorToHex(currentColor), currentBrushSize
                );
                sendToServer(drawMessage);
            }

            lastX = x;
            lastY = y;
        });

        drawingCanvas.setOnMouseReleased(event -> {
            if(!canDraw) return;
        });
    }

    //start the timer
    private void startSmoothDrawingLoop() {
        drawingLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                processAnimationQueue();
            }
        };
        drawingLoop.start();
    }

    /**
     * Processes the queue of incoming points.
     * Uses a loop to "Speed Read" the queue if it gets too full.
     */
    private void processAnimationQueue() {
        if (pointQueue.isEmpty()) return;

        GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        // SPEED UP LOGIC:
        // If queue has > 2 points, draw up to 10 points per frame (Catch up!)
        // Otherwise, draw 1 point per frame (Smooth)
        int pointsToProcess;
        if (pointQueue.size() > 2) {
            pointsToProcess = 10;
        } else {
            pointsToProcess = 1;
        }

        for (int i = 0; i < pointsToProcess; i++) {
            // Stop if we run out of points mid-loop
            if (pointQueue.isEmpty()) return;

            PointRequest req = pointQueue.peek();
            Message.DrawData target = req.data;

            // CASE 1: New Stroke (Pen Lift)
            if (remoteFirstPoint || req.isNewStroke) {
                currentAnimX = target.getX();
                currentAnimY = target.getY();
                remoteFirstPoint = false;

                gc.setFill(Color.web(target.getColor()));
                gc.fillOval(currentAnimX - target.getSize()/2, currentAnimY - target.getSize()/2,
                        target.getSize(), target.getSize());

                pointQueue.poll();
                continue; // Move to next iteration
            }

            double dx = target.getX() - currentAnimX;
            double dy = target.getY() - currentAnimY;
            double distance = Math.sqrt(dx * dx + dy * dy);

            // CASE 2: Very close? Just snap.
            if (distance < 1.0) {
                gc.setStroke(Color.web(target.getColor()));
                gc.setLineWidth(target.getSize());
                gc.strokeLine(currentAnimX, currentAnimY, target.getX(), target.getY());

                currentAnimX = target.getX();
                currentAnimY = target.getY();

                pointQueue.poll();
                continue;
            }

            // CASE 3: Calculate Speed
            // If we are in the middle of a loop (catching up), go Instant (1.0).
            // Otherwise, use the smooth speed.
            double actualSpeed;
            if (i > 0) {
                actualSpeed = 1.0;
            } else {
                actualSpeed = SMOOTHING_SPEED;
            }

            double moveX = currentAnimX + (dx * actualSpeed);
            double moveY = currentAnimY + (dy * actualSpeed);

            gc.setStroke(Color.web(target.getColor()));
            gc.setLineWidth(target.getSize());
            gc.strokeLine(currentAnimX, currentAnimY, moveX, moveY);

            currentAnimX = moveX;
            currentAnimY = moveY;

            // If we moved 100% of the way (speed 1.0), remove the point
            if (actualSpeed >= 1.0) {
                pointQueue.poll();
            }
        }
    }

    /**
     * Clears the drawing canvas and resets the drawing related state.
     */
    @FXML
    private void clearCanvas() {
        if(!canDraw) {
            displayMessage("Only the drawer can clear the canvas!\n");
            return;
        }

        GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());

        lastX = 0;
        lastY = 0;
        remoteFirstPoint = true;

        if (connected) {
            Message clearMessage = Message.createClearMessage();
            sendToServer(clearMessage);
        }
    }

    @FXML
    private void onEraserClicked() {
        // Switch color to White (Background color)
        currentColor = Color.WHITE;

        // Locks the Color Picker so they can't change it while erasing
        if (colorPicker != null) {
            colorPicker.setValue(Color.WHITE);
            // Grey out the picker
            colorPicker.setDisable(true);
        }

        // Set Slider to Maximum (20) for big eraser
        if (sizeSlider != null) {
            sizeSlider.setValue(sizeSlider.getMax());
            currentBrushSize = sizeSlider.getMax();
        }

        // Update the label manually since the slider listener might lag slightly
        if (sizeValueLabel != null) {
            sizeValueLabel.setText("Brush: " + (int)currentBrushSize + "px");
        }
    }

    @FXML
    private void onDrawClicked() {
        // Unlock the Color Picker
        if (colorPicker != null) {
            // Enable it again
            colorPicker.setDisable(false);
            // Reset visual to Black
            colorPicker.setValue(Color.BLACK);
        }

        // Reset logic color to Black
        currentColor = Color.BLACK;

        // Reset Slider to normal size (4)
        if (sizeSlider != null) {
            sizeSlider.setValue(4);
            currentBrushSize = 4;
        }
        if (sizeValueLabel != null) {
            sizeValueLabel.setText("Brush: 4px");
        }
    }

    /**
     * Converts a JavaFX Color to #RRGGBB hex string.
     */
    private String colorToHex(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    /**
     * Connects to the server and starts listening for messages.
     * Runs connection in background thread to avoid blocking GUI.
     * Uses a timeout to detect if the server is full or unreachable.
     */
    private void connectToServer() {
        Runnable connectionTask = new Runnable() {
            @Override
            public void run() {
                try {
                    displayMessage("Attempting connection to " + serverIP + "...\n");

                    connection = new Socket();
                    connection.connect(new InetSocketAddress(serverIP, SERVER_PORT), 3000);
                    connection.setSoTimeout(2000);

                    output = new ObjectOutputStream(connection.getOutputStream());
                    output.flush();

                    input = new ObjectInputStream(connection.getInputStream());
                    connection.setSoTimeout(0);

                    connected = true;
                    displayMessage("SUCCESS: Connected to " + serverIP + "\n");

                    Message usernameMsg = Message.createUsernameMessage(username);
                    sendToServer(usernameMsg);

                    displayMessage("Welcome, " + username + "!\n");

                    // Start listening for messages from server
                    processServerMessages();

                } catch (UnknownHostException e) {
                    closeSocketOnError();
                    displayMessage("\n[ERROR] Invalid Host: " + serverIP + "\n");
                    displayMessage("Please check the IP address format.\n");

                } catch (SocketTimeoutException e) {
                    closeSocketOnError();
                    closeWindowOnError(
                            "Connection Timed Out",
                            "The server did not respond in time (3s).\n" +
                                    "Likely cause: The Server is FULL or not running."
                    );

                } catch (IOException e) {
                    // Catch other IO errors (like Connection Refused)
                    closeSocketOnError();
                    displayMessage("\n[ERROR] Connection failed!\n");
                    displayMessage("Server response: " + e.getMessage() + "\n");
                }
            }
        };

        executor.execute(connectionTask);
    }

    /**
     * Helper to force-close the socket if connection fails.
     * This ensures we don't leave half-open resources hanging.
     */
    private void closeSocketOnError() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }} catch (IOException e) {
            // Ignored because we are already handling an error
        }
    }

    /**
     * Shows an error popup and then force-closes the application window.
     * Uses Platform.runLater with an anonymous inner class.
     */
    private void closeWindowOnError(final String header, final String content) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Connection Error");
                alert.setHeaderText(header);
                alert.setContentText(content);

                alert.showAndWait();

                // Gets the current window (Stage) and close it
                if (chatTextArea.getScene() != null) {
                    Stage stage = (Stage) chatTextArea.getScene().getWindow();
                    stage.close();
                }

                // Makes sure background threads are killed
                disconnect();
            }
        });
    }
    /**
     * Continuously listens for messages from the server.
     * Runs in background thread - blocks at readObject() waiting for messages.
     *
     * All message types to be handled are in Message
     */
    private void processServerMessages() {
        while (connected) {
            try {
                // Blocks here waiting for server message
                Message message = (Message) input.readObject();

                // Handle different message types
                handleServerMessage(message);

            } catch (IOException ioException) {
                if (connected) {
                    displayMessage("Lost connection to server\n");
                    connected = false;
                }
                break;
            } catch (ClassNotFoundException classNotFoundException) {
                displayMessage("Received unknown message type\n");
            }
        }
    }

    /**
     * Receives data from server and queues it for the animation loop.
     * Detects pen lifts based on time delays.
     */
    private void drawRemotePoint(Message.DrawData drawData) {
        long now = System.currentTimeMillis();

        // If > 150ms delay, assume new stroke
        boolean isNewStroke = (now - lastReceivedTime > 150);
        lastReceivedTime = now;

        pointQueue.add(new PointRequest(drawData, isNewStroke));
    }

    /**
     * Handles messages received from the server.
     * Routes messages based on their type prefix.
     *
     * @param message the message from the server
     */
    private void handleServerMessage(Message message) {
        String messageType = message.getMessageType();

        if (messageType.equals(Message.CONNECTED)) {
            // Server confirms connection with client ID
            int clientID = message.parseConnectedMessage();
            displayMessage("You are Client " + clientID + "\n");

        } else if (messageType.equals(Message.DRAWER_ASSIGNED)) {
            Platform.runLater(() ->
            {
                // This makes sure if they were erasing last turn, they start this turn with a black pen
                onDrawClicked();
                timerLabel.setText("60");

                // Reset label while they choose a word
                if (wordToGuessLabel != null) {
                    wordToGuessLabel.setText("Choose a word...");
                }
                if (wordLabel != null) {
                    wordLabel.setText("YOU ARE DRAWING!");
                }
                drawingCanvas.setStyle("-fx-cursor: crosshair;");
            });
            displayMessage("*** YOU ARE THE DRAWER! ***\n");

        } else if (messageType.equals(Message.CHAT)) {
            // Chat message format: CHAT:username:message
            Message.ChatData chatData = message.parseChatMessage();
            String user = chatData.getUsername();
            String chatMessage = chatData.getMessage();

            // If the message contains "guessed correctly", play the sound!
            // (Adjust the string match if your server sends a different specific message)
            if (chatMessage.toLowerCase().contains("guessed correctly")) {
                if (correctSound != null) {
                    correctSound.play();
                }
            }
            displayMessage(user + ": " + chatMessage + "\n");

        } else if (messageType.equals(Message.DRAW)) {
            Message.DrawData drawData = message.parseDrawMessage();
            drawRemotePoint(drawData);

        } else if (messageType.equals(Message.WORD_OPTIONS)) {
            String[] words = message.getMessageContents().split(",");

            Platform.runLater(() -> {
                // Enable and Label Button 1
                WordOption1.setText(words[0]);
                WordOption1.setVisible(true);
                WordOption1.setDisable(false);
                WordOption1.setOnAction(e -> sendWordSelection(words[0]));

                // Enable and Label Button 2
                WordOption2.setText(words[1]);
                WordOption2.setVisible(true);
                WordOption2.setDisable(false);
                WordOption2.setOnAction(e -> sendWordSelection(words[1]));

                // Enable and Label Button 3
                WordOption3.setText(words[2]);
                WordOption3.setVisible(true);
                WordOption3.setDisable(false);
                WordOption3.setOnAction(e -> sendWordSelection(words[2]));

                displayMessage("Choose a word to draw!\n");
            });
        } else if (messageType.equals(Message.ROUND_START)) {
            // 1. Parse the message
            Message.RoundStartData data = message.parseRoundStartMessage();
            String textToDisplay = data.getWord(); // Will be "APPLE" or "_ _ _ _ _"
            int duration = data.getDuration();

            // 2. Update the GUI
            Platform.runLater(() -> {
                // 2. Logic to Lock/Unlock based on what we received
                if (textToDisplay.contains("_")) {
                    // Guesser
                    canDraw = false;
                    if (wordToGuessLabel != null){
                        wordToGuessLabel.setText(textToDisplay);
                    }
                    drawingCanvas.setStyle("-fx-cursor: default;");
                } else {
                    // Drawer
                    canDraw = true;
                    if (wordToGuessLabel != null){
                        wordToGuessLabel.setText(textToDisplay);
                    }
                    drawingCanvas.setStyle("-fx-cursor: crosshair;");
                }

                startRoundTimer(duration);
            });

        } else if (messageType.equals(Message.ROUND_UPDATE)) {
            int round = message.parseRoundUpdateMessage();

            Platform.runLater(() -> {
                stopRoundTimer();
                canDraw = false;

                // 1. Reset the cursor
                drawingCanvas.setStyle("-fx-cursor: default;");

                // 2. Update the Round Label
                if (roundLabel != null) {
                    if (round == 0) {
                        roundLabel.setText("Waiting...");
                    } else {
                        roundLabel.setText("Round: " + round + "/5");
                    }
                }
                if (wordToGuessLabel != null) {
                    wordToGuessLabel.setText("Waiting for drawer...");
                }
                if (wordLabel != null) {
                    wordLabel.setText("");
                }
                if (timerLabel != null) {
                    timerLabel.setText("60");
                }

                // Hide word option buttons if they're visible
                if (WordOption1 != null) {
                    WordOption1.setVisible(false);
                    WordOption2.setVisible(false);
                    WordOption3.setVisible(false);
                }
            });
        } else if (messageType.equals(Message.LEADERBOARD)) {
            String leaderboardData = message.getMessageContents();

            Platform.runLater(() -> {
                if (leaderboardData == null || leaderboardData.isEmpty()) {
                    // Clear leaderboard
                    Label[] labels = {leaderboardSpot1, leaderboardSpot2, leaderboardSpot3, leaderboardSpot4, leaderboardSpot5};
                    for (Label l : labels) {
                        if (l != null) l.setText("");
                    }
                } else {
                    String[] parts = leaderboardData.split(",");
                    updateLeaderboardLabels(parts);
                }
            });
        } else if (messageType.equals(Message.LEADER)) {
            Platform.runLater(() -> {
                if (startGame != null) {
                    startGame.setDisable(false);
                    displayMessage("*** You are the Lobby Leader! ***\n");
                }
            });

        } else if (messageType.equals(Message.CLEAR)) {
            //Clear canvas when receiving clear from server
            Platform.runLater(() -> {
                GraphicsContext gc = drawingCanvas.getGraphicsContext2D();
                gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());
                remoteFirstPoint = false;
            });
            displayMessage("[Canvas cleared]\n");

        } else if (messageType.equals(Message.GAME_OVER)) {
            String leaderboardData = message.getMessageContents();

            Platform.runLater(() -> {

                // stop the music
                if (gameMusicPlayer != null) {
                    gameMusicPlayer.stop();
                }
                //stop the game
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("GameOver.fxml"));

                    Stage gameOverStage = new Stage();
                    gameOverStage.setScene(new Scene(loader.load()));
                    gameOverStage.setTitle("Game Over");

                    gameOverStage.initModality(Modality.APPLICATION_MODAL);
                    gameOverStage.initStyle(StageStyle.UNDECORATED);

                    GameOverController controller = loader.getController();
                    controller.setLeaderboardData(leaderboardData);
                    controller.startCountdown();

                    gameOverStage.showAndWait();

                } catch (IOException e) {
                    e.printStackTrace();
                    displayMessage("[System] Error loading Game Over screen.\n");
                }
            });

        } else if (messageType.equals(Message.SCORE)) {
            int newScore = message.parseScoreMessage();

            Platform.runLater(() -> {
                if (scoreLabel != null) {
                    scoreLabel.setText(String.valueOf(newScore));
                }
            });
        }
        else {
            displayMessage("[SERVER] " + message.toString() + "\n");
        }
    }

    /**
     * Updates the 5 leaderboard labels on the sidebar.
     */
    private void updateLeaderboardLabels(String[] data) {
        Label[] labels = {leaderboardSpot1, leaderboardSpot2, leaderboardSpot3, leaderboardSpot4, leaderboardSpot5};

        // Clear all first
        for (Label l : labels) {
            if (l != null) l.setText("");
        }

        // Fill in available data
        int labelIndex = 0;
        for (int i = 0; i < data.length - 1; i += 2) {
            if (labelIndex >= labels.length) break;

            String name = data[i];
            String score = data[i+1];

            if (labels[labelIndex] != null) {
                labels[labelIndex].setText((labelIndex + 1) + ". " + name + " - " + score);
            }
            labelIndex++;
        }
    }

    /**
     * Starts a visual countdown for the client.
     */
    private void startRoundTimer(int seconds) {
        // Stop old timer if running
        if (roundTimer != null) roundTimer.stop();

        timerEndTime = System.currentTimeMillis() + (seconds * 1000);

        roundTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long remaining = timerEndTime - System.currentTimeMillis();
                int secondsLeft = (int) Math.ceil(remaining / 1000.0);

                if (secondsLeft < 0) secondsLeft = 0;

                if (timerLabel != null) {
                    timerLabel.setText(String.valueOf(secondsLeft));
                }

                if (secondsLeft <= 0) {
                    stop();
                }
            }
        };
        roundTimer.start();
    }

    /**
     * Stops the round timer immediately.
     */
    private void stopRoundTimer() {
        if (roundTimer != null) {
            roundTimer.stop();
        }
    }

    private void sendWordSelection(String word) {
        sendToServer(Message.createWordSelectedMessage(word));

        // Hide buttons immediately after choosing
        WordOption1.setVisible(false);
        WordOption2.setVisible(false);
        WordOption3.setVisible(false);
    }

    /**
     * Sends a chat message to the server.
     */
    @FXML
    private void sendServerChat() {
        String message = chatTextInput.getText().trim();
        if (message.isEmpty()) return;
        if (!connected) {
            displayMessage("Not connected to server!\n");
            return;
        }
        Message chatMessage = Message.createGuessMessage(username, message);
        sendToServer(chatMessage);
        chatTextInput.clear();
    }

    /**
     * Sends a message to the server.
     * Thread-safe method that can be called from any thread.
     *
     * @param message the message to send
     */
    private void sendToServer(Message message) {
        if (!connected || output == null) {
            displayMessage("Cannot send - not connected to server\n");
            return;
        }

        try {
            synchronized (output) {
                output.writeObject(message);
                output.flush();
            }
        } catch (IOException ioException) {
            displayMessage("Error sending message: " + ioException.getMessage() + "\n");
        }
    }

    /**
     * Displays a message in the chat text area.
     * Thread-safe - uses Platform.runLater() for GUI updates.
     *
     * @param message the message to display
     */
    private void displayMessage(String message) {
        Platform.runLater(() -> {
            chatTextArea.appendText(message);
        });
    }

    /**
     * Closes the connection to the server.
     * Should be called when application is closing.
     */
    public void disconnect() {
        connected = false;

        if (gameMusicPlayer != null) {
            gameMusicPlayer.stop();
        }

        try {
            if (output != null) {
                sendToServer(Message.createTerminateMessage());
                output.close();
            }
            if (input != null) {
                input.close();
            }
            if (connection != null) {
                connection.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}