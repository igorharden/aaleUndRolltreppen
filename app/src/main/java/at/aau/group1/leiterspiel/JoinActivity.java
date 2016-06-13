package at.aau.group1.leiterspiel;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.nsd.NsdServiceInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import at.aau.group1.leiterspiel.Network.Client;
import at.aau.group1.leiterspiel.Network.ILobby;
import at.aau.group1.leiterspiel.Network.INsdObserver;
import at.aau.group1.leiterspiel.Network.MessageComposer;
import at.aau.group1.leiterspiel.Network.MessageParser;
import at.aau.group1.leiterspiel.Network.NsdDiscovery;
import at.aau.group1.leiterspiel.Network.NsdService;

public class JoinActivity extends AppCompatActivity implements INsdObserver, ILobby {

    private final String TAG = "Join";

    private LinearLayout list;
    private EditText playerNameInput;
    private Button startButton;

    public static Client client;
    public static MessageComposer composer;
    public static int msgID = 0;

    private boolean discoveryStarted = false;
    private static NsdDiscovery discovery;
    private Timer uiTimer;
    private TimerTask timerTask;
    private ArrayList<NsdServiceInfo> unhandledInfos = new ArrayList<>();
    private TreeMap<Integer, NsdServiceInfo> availableServices = new TreeMap<>();

    private boolean uiChanged = false;
    private String clientName = "Missing name";
    private int playerIndex = -1;

    // lobby settings, set by server
    private boolean[] playerSelection = new boolean[LobbyActivity.MAX_PLAYERS];
    private String[] playerNames = new String[LobbyActivity.MAX_PLAYERS];
    private String[] playerTypes = new String[LobbyActivity.MAX_PLAYERS];
    private boolean cheatsEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        list = (LinearLayout) findViewById(R.id.servicesList);
        playerNameInput = (EditText) findViewById(R.id.clientNameView);
        startButton = (Button) findViewById(R.id.searchButton);
        startButton.requestFocus();
    }

    private void updateUI() {
        if (uiChanged) {
            for (NsdServiceInfo info: unhandledInfos) {
                String serverName = info.getServiceName().replace(NsdService.SERVICE_NAME, "");
                serverName = serverName.replace("\\032", " ");

                Button button = new Button(getApplicationContext());
                button.setText(serverName);
                button.setBackgroundColor(getResources().getColor(R.color.blue));
                button.setTextColor(getResources().getColor(R.color.white));
                int id = availableServices.size()+1;
                availableServices.put(id, info);
                button.setId(id); // set ID so it can be identified later on when clicked
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        joinService(v);
                    }
                });
                list.addView(button);
            }
            unhandledInfos.clear();
            uiChanged = false;
        }
    }

    @Override
    protected void onPause() {
        if (discoveryStarted) discovery.stopDiscovery();
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (discoveryStarted) discovery.startDiscovery();
        super.onResume();
    }

    @Override
    public void notifyOfNewService(NsdServiceInfo serviceInfo) {
        unhandledInfos.add(serviceInfo);
        uiChanged = true;
    }

    private void joinService(View view) {
        int id = view.getId();
        NsdServiceInfo info = availableServices.get(id);

        if (client == null) {
            client = new Client();
            client.registerLobby(this);
            client.connectToServer(info);
            composer = new MessageComposer(clientName, false);
            composer.registerClient(client);
        } else {
            client.disconnect();
            client.connectToServer(info);
        }

        for (int i=0; i<list.getChildCount(); i++) {
            list.getChildAt(i).setEnabled(false);
        }
        startButton.setText(getString(R.string.connected_wait));
        startButton.setBackgroundColor(getResources().getColor(R.color.darkgreen));

        composer.joinLobby(msgID++);
        Log.d(TAG, "Attempt to join selected lobby...(ID "+(msgID-1)+")");
    }

    public void initDiscovery(View view) {
        Log.d(TAG, "initDiscovery");
        if (!discoveryStarted) {
            clientName = playerNameInput.getText().toString();

            discovery = new NsdDiscovery(getApplicationContext(), clientName);
            discovery.registerObserver(this);

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
            startButton.setText(R.string.searching_games);

            discovery.startDiscovery();
            discoveryStarted = true;
            Log.d(TAG, "Started discovery as "+clientName);
        }
    }

    @Override
    public void ack(int id) {
        // TODO
        Log.d(TAG, "Ack for message ID "+id+" received");
    }

    @Override
    public void joinLobby(int id, String name) {
        // do nothing as client
    }

    @Override
    public void assignIndex(int id, int index, String clientName) {
        if (this.clientName.equals(clientName)) playerIndex = index;

        composer.ack(id);
    }

    @Override
    public void setPlayer(int id, int playerIndex, String playerType, String playerName) {
        this.playerSelection[playerIndex] = true;
        this.playerTypes[playerIndex] = playerType;
        this.playerNames[playerIndex] = playerName;

        composer.ack(id);
    }

    @Override
    public void allowCheats(int id, boolean permitCheats) {
        this.cheatsEnabled = permitCheats;

        composer.ack(id);
    }

    @Override
    public void startGame(int id) {
        // This is the player controlled on this client instance, which means it's local instead of
        // online here. See startGame() method in LobbyActivity.
        for (int i=0; i<LobbyActivity.MAX_PLAYERS; i++) {
            if (i == this.playerIndex && playerTypes[i].equals(LobbyActivity.ONLINE))
                playerTypes[i] = LobbyActivity.LOCAL;
        }

        Intent intent = new Intent(getApplicationContext(), GameActivity.class);
        // add all lobby settings to intent so GameActivity can use them in initGame()
        intent.putExtra("PlayerSelection", playerSelection);
        intent.putExtra("PlayerNames", playerNames);
        intent.putExtra("PlayerTypes", playerTypes);
        intent.putExtra("CheatPermission", cheatsEnabled);
        intent.putExtra("ClientInstance", true);
        intent.putExtra("PlayerIndex", playerIndex);

        composer.ack(id);

        startActivity(intent); // start the game activity
        finish(); // end this activity as soon as the game activity finished
    }
}
