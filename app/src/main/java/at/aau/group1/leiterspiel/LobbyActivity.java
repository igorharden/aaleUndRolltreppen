package at.aau.group1.leiterspiel;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import at.aau.group1.leiterspiel.Network.AckChecker;
import at.aau.group1.leiterspiel.Network.ICommListener;
import at.aau.group1.leiterspiel.Network.ILobby;
import at.aau.group1.leiterspiel.Network.MessageComposer;
import at.aau.group1.leiterspiel.Network.NsdService;
import at.aau.group1.leiterspiel.Network.Server;

/**
 * Created by Igor on 22.05.2016.
 */
public class LobbyActivity extends AppCompatActivity implements ICommListener, ILobby {

    private final String TAG = "Lobby";

    public static final int MAX_PLAYERS = 6;
    public static final String BOT = "Bot";
    public static final String LOCAL = "Local";
    public static final String ONLINE = "Online";
    private final int BOARD_TYPES = 3;

    private final int MAX_TURN_SKIPS = 3;
    private static int turnSkips = 1;

    private static NsdService service;
    private static String serverName = "Missing name";
    private static boolean serviceActive = false;
    private static Timer uiTimer;
    private static TimerTask timerTask;
    private static String newPlayer;
    private static int openOnlineSlots = 0;
    private static boolean uiChanged = false;
    // true if an online client is connected, otherwise start an offline game
    private static boolean online = false;

    public static Server server;
    public static MessageComposer composer;

    private static String[] playerNames = new String[MAX_PLAYERS];
    private static boolean[] playerSelection = new boolean[MAX_PLAYERS];
    private static String[] playerTypes = new String[MAX_PLAYERS];

    private LinearLayout list;
    private ImageButton[] playerImages = new ImageButton[MAX_PLAYERS];
    private Button[] typeButtons = new Button[MAX_PLAYERS];
    private EditText[] playerNameFields = new EditText[MAX_PLAYERS];
    private Button cheatToggleButton;
    private Button boardToggleButton;
    private int[] playerIcons = {
            R.drawable.patrick,
            R.drawable.sponge,
            R.drawable.krabs,
            R.drawable.squid,
            R.drawable.plankton,
            R.drawable.sandy };

    private boolean cheatsEnabled = false;
    private int boardType = 0;

    public static int msgID = 0;
    private AckChecker ackChecker = new AckChecker();

    //Fullscreen
    private Fullscreen fs = new Fullscreen();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        // create the list of players
        list = (LinearLayout) findViewById(R.id.playerList);
        for (int i=0; i<MAX_PLAYERS; i++) {
            // inflate the layout and add it as child
            getLayoutInflater().inflate(getResources().getLayout(R.layout.player_item), list);
            // get the children of the loaded layout
            playerImages[i] = (ImageButton) list.getChildAt(i).findViewById(R.id.playerImage);
            typeButtons[i] = (Button) list.getChildAt(i).findViewById(R.id.playerType);
            playerNameFields[i] = (EditText) list.getChildAt(i).findViewById(R.id.playerName);
            // the ID doesn't have to be unique for every single View,
            // so for simple identification use the index
            playerImages[i].setId(i);
            typeButtons[i].setId(i);
            playerNameFields[i].setId(i);
        }

        cheatToggleButton = (Button) findViewById(R.id.cheatToggleButton);
        boardToggleButton = (Button) findViewById(R.id.boardToggleButton);

        fs.setDecorView(getWindow().getDecorView());
        fs.hideSystemUI();

        init();
    }

    @Override
    protected void onStop() {
        stopService();
        super.onStop();
    }

    private void init() {
        Arrays.fill(playerTypes, BOT);
        // by default, create a local player and a bot
        togglePlayer(0);
        togglePlayer(1);
        // player 0 is always the host/local player
        playerTypes[0] = LOCAL;
        typeButtons[0].setText(R.string.local);
        playerNameFields[0].setEnabled(true);
        playerImages[0].setBackground(getResources().getDrawable(playerIcons[0]));
    }

    private void startService() {
        if (!serviceActive) {
            serverName = String.valueOf(playerNameFields[0].getText());
            if (serverName.equals("")) serverName = getString(R.string.default_name);

            service = new NsdService(getApplicationContext(), serverName);
            service.startService();
            if (server == null) {
                server = new Server();
                server.registerListener(this);
                server.registerLobby(this);
                server.startCommunication(service.getServerSocket());
                // attach a composer to the server
                composer = new MessageComposer(serverName, true);
                composer.registerServer(server);
                // start the timer for updating the UI
                uiTimer = new Timer();
                timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateUI();
                            }
                        });
                    }
                };
                uiTimer.scheduleAtFixedRate(timerTask, 0, 1000);
            }

            serviceActive = true;
        }
    }

    private void stopService() {
        if (serviceActive) {
            service.stopService();
            service = null;
            serviceActive = false;
        }
    }

    private void updateUI() {
        if (uiChanged) {
            if (newPlayer != null && openOnlineSlots>0) {
                // begin with index 1 because player 0 is always the server
                for (int i=1; i<MAX_PLAYERS; i++) {
                    if (playerSelection[i] && playerTypes[i].equals(ONLINE)) {
                        playerNameFields[i].setText(newPlayer);
                        playerNameFields[i].setBackground(getResources().getDrawable(R.drawable.rounded_online_player));
                        playerNameFields[i].setTextColor(getResources().getColor(R.color.white));
                        playerNameFields[i].setAlpha(1.0f);
                        playerNames[i] = newPlayer;
                        break;
                    }
                }

                openOnlineSlots--;
                newPlayer = null;
                online = true;
            }

            uiChanged = false;
        }
    }

    public void startGame(View view) {
        if (openOnlineSlots > 0) {
            Toast.makeText(getApplicationContext(), getString(R.string.open_slot), Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i=0; i<MAX_PLAYERS; i++) {
            playerNames[i] = String.valueOf(playerNameFields[i].getText());
            if (playerNames[i].equals("")) playerNames[i] = getString(R.string.default_name);
        }

        // transmit lobby information to the client
        if (online) {
            // in case the server-side player changes the name before starting the game, update the name just to be safe
            composer.changeName(playerNames[0]);

            for (int i = 0; i < MAX_PLAYERS; i++) {
                if (playerSelection[i]) { // if the player on this index is enabled
                    String type = playerTypes[i];
                    if (type.equals(ONLINE)) {
                        composer.assignIndex(msgID++, i, playerNames[i]);
                        if (!ackChecker.waitForAcknowledgement(msgID-1)) {
                            Toast.makeText(getApplicationContext(), getString(R.string.timeout), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    // Send player info:
                    // A player which is an online player on this server instance, is a local player
                    // on a client instance, and vice versa. This has to be considered, or else the
                    // different GameManagers get confused.
                    //
                    // If multiple clients get connected, only one online player per client becomes
                    // local(the one with the same index as i). This is currently considered
                    // client-side with an extra check.
                    if (type.equals(LOCAL)) type = ONLINE;
                    composer.setPlayer(msgID++, i, type, playerNames[i]);

                    if (!ackChecker.waitForAcknowledgement(msgID-1)) {
                        Toast.makeText(getApplicationContext(), getString(R.string.timeout), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
        }

        Intent intent = new Intent(getApplicationContext(), GameActivity.class);
        // add all lobby settings to intent so GameActivity can use them for init
        intent.putExtra("PlayerSelection", playerSelection);
        intent.putExtra("PlayerNames", playerNames);
        intent.putExtra("PlayerTypes", playerTypes);
        intent.putExtra("CheatPermission", cheatsEnabled);
        intent.putExtra("TurnSkips", turnSkips);
        intent.putExtra("BoardType", boardType);
        intent.putExtra("ClientInstance", false);
        intent.putExtra("PlayerIndex", 0);
        intent.putExtra("Online", online);

        // makes system ui "invisible"           --NOT WORKING PROPERLY YET!! HAS TO BE UPDATED!
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        view.setSystemUiVisibility(uiOptions);

        // transmit final settings and start the game on all connected instances
        if (online) {
            composer.allowCheats(msgID++, cheatsEnabled, turnSkips);
            if (!ackChecker.waitForAcknowledgement(msgID-1)) {
                Toast.makeText(getApplicationContext(), getString(R.string.timeout), Toast.LENGTH_SHORT).show();
                return;
            }
            composer.setBoardType(msgID++, boardType);
            if (!ackChecker.waitForAcknowledgement(msgID-1)) {
                Toast.makeText(getApplicationContext(), getString(R.string.timeout), Toast.LENGTH_SHORT).show();
                return;
            }
            composer.startGame(msgID++);
            if (!ackChecker.waitForAcknowledgement(msgID-1)) {
                Toast.makeText(getApplicationContext(), getString(R.string.timeout), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        stopService(); // the service isn't needed anymore as soon as the connection runs

        startActivity(intent); // start the game activity
        finish(); // end this activity as soon as the game activity finished
    }

    public void toggleCheats(View view) {
        if (!cheatsEnabled) {
            cheatsEnabled = true;
            turnSkips = 0;
        }
        if (cheatsEnabled){
            if (++turnSkips > MAX_TURN_SKIPS) {
                cheatsEnabled = false;
                cheatToggleButton.setText(getString(R.string.allow_cheats));
            } else cheatToggleButton.setText(getString(R.string.punishment)+": "+turnSkips+"x "+getString(R.string.skip_cheater));
        }
    }

    public void toggleGameBoard(View view) {
        if (++boardType >= BOARD_TYPES) boardType = 0;
        if (boardType == 0) boardToggleButton.setText(getString(R.string.default_eels));
        if (boardType == 1) boardToggleButton.setText(getString(R.string.many_eels));
        if (boardType == 2) boardToggleButton.setText(getString(R.string.no_eels));
    }

    public void togglePlayer(View view) {
        for (int i=1; i<MAX_PLAYERS; i++) {
            if ((int)view.getId() == i) {
                togglePlayer(i);
                break;
            }
        }
    }

    public void togglePlayerType(View view) {
        for (int i=1; i<MAX_PLAYERS; i++) {
            if ((int)view.getId() == i) {
                togglePlayerType(i);
                break;
            }
        }
    }

    private void togglePlayer(int index) {
        playerSelection[index] = !playerSelection[index];

        for (int i=1; i<MAX_PLAYERS; i++) {
            if (index == i) {
                playerNameFields[i].setEnabled(playerSelection[i]);
                if (playerSelection[i]) {
                    playerImages[i].setBackground(getResources().getDrawable(playerIcons[i]));
                    if (playerTypes[i].equals(ONLINE)) openOnlineSlots++;
                } else {
                    playerImages[i].setBackground(getResources().getDrawable(R.drawable.no_player));
                    if (playerTypes[i].equals(ONLINE)) openOnlineSlots--;
                }
                break;
            }
        }
        toggleUI();
    }

    private void togglePlayerType(int index) {
        for (int i = 0; i<MAX_PLAYERS; i++) {
            if (i == index) {
                if (playerTypes[i] == BOT) { // if bot
                    playerTypes[i] = ONLINE;
                    typeButtons[i].setText(R.string.online);
                    playerNameFields[i].setHint(R.string.waiting);
                    openOnlineSlots++;
                    // start service if an online player is requested
                    startService();
                    Toast.makeText(getApplicationContext(), getString(R.string.service_visible), Toast.LENGTH_SHORT).show();

                } else if (playerTypes[i] == LOCAL) { // if local
                    playerTypes[i] = BOT;
                    typeButtons[i].setText(R.string.bot);
                } else { // if online
                    playerTypes[i] = LOCAL;
                    typeButtons[i].setText(R.string.local);
                    playerNameFields[i].setHint(R.string.default_name);
                    openOnlineSlots--;
                }
                break;
            }
        }
        toggleUI();
    }

    private void toggleUI() {
        for (int i=1; i<MAX_PLAYERS; i++) {
            playerNameFields[i].setEnabled( playerTypes[i] == LOCAL && playerSelection[i] );
        }
    }

    @Override
    public void inputReceived() {
        String input = server.getInput();
        Log.d(TAG, "Server received input: "+input);
    }

    @Override
    public void ack(int id) {
        ackChecker.setLastAckID(id);
    }

    @Override
    public void joinLobby(int id, String name) {
        if (openOnlineSlots>0) {
            newPlayer = name;
            uiChanged = true;
            // answer with an ack
            composer.ack(id);
            Log.d(TAG, "Online player "+name+" joined the lobby");
        } else {
            Log.d(TAG, "Online player "+name+" can't join: no available slots");
        }
    }

    @Override
    public void assignIndex(int id, int index, String clientName) {
        // this is the server, so ignore server-exclusive messages
    }

    @Override
    public void setPlayer(int id, int playerIndex, String playerType, String playerName) {
        // this is the server, so ignore server-exclusive messages
    }

    @Override
    public void allowCheats(int id, boolean permitCheats, int turnSkips) {
        // this is the server, so ignore server-exclusive messages
    }

    @Override
    public void setBoardType(int id, int type) {
        // this is the server, so ignore server-exclusive messages
    }

    @Override
    public void startGame(int id) {
        // this is the server, so ignore server-exclusive messages
    }
}
