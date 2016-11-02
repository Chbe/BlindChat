package com.github.chris.socketnode.androidchat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;


/**
 * A chat fragment containing messages view and input form.
 */
public class MainFragment extends Fragment {
    protected static final String TAG = "MainFragment";

    private static final int REQUEST_LOGIN = 0;

    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private String username;
    private Socket mSocket;

    private BroadcastReceiver broadcastReceiver;
    private TextView textView;
    static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private double longitude = 0;
    private double latitude = 0;
    private Boolean isConnected = true;
    ArrayList<String> options = new ArrayList<String>();

    public MainFragment() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAdapter = new MessageAdapter(activity, mMessages);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        ChatApplication app = (ChatApplication) getActivity().getApplication();
        mSocket = app.getSocket();
        mSocket.on(Socket.EVENT_CONNECT, onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("new message", onNewMessage);
        mSocket.on("user joined", onUserJoined);
        mSocket.on("user left", onUserLeft);
        mSocket.on("updateusers", onUpdateUsers);
        mSocket.connect();

        startSignIn();

        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

            // MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        } else {
            Intent intent = new Intent(getActivity(), BackgroundLocationService.class);
            getActivity().startService(intent);

            printLocation();
        }

        printLocation();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(this.broadcastReceiver);
        Intent intent = new Intent(getActivity(), BackgroundLocationService.class);
        getActivity().stopService(intent);

        mSocket.disconnect();

        mSocket.off(Socket.EVENT_CONNECT, onConnect);
        mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.off("new message", onNewMessage);
        mSocket.off("user joined", onUserJoined);
        mSocket.off("user left", onUserLeft);

    }

    @Override
    public void onResume() {
        super.onResume();
        Intent intent = new Intent(getActivity(), BackgroundLocationService.class);
        getActivity().startService(intent);
        printLocation();
        Log.i(TAG, "Service startad från login");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textView = (TextView) view.findViewById(R.id.textView);
        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Activity.RESULT_OK != resultCode) {
            getActivity().finish();
            return;
        }

        username = data.getStringExtra("username");
        int numUsers = data.getIntExtra("numUsers", 1);


        addLog(getResources().getString(R.string.message_welcome));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu);

        /*MenuItem item;
        menu.findItem(R.id.menu_spinner1);
        item = menu.findItem( R.id.menu_spinner1);
        View view1 = item.getActionView();
        if (view1 instanceof Spinner)
        {
            final Spinner spinner = (Spinner) view1;
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),android.R.layout.simple_spinner_item,options);
            spinner.setAdapter(adapter);


            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {
                    // TODO Auto-generated method stub

                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                    // TODO Auto-generated method stub

                }
            });

        }*/
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_leave:
                leave();
                return true;
            case R.id.radius_change:
                ShowDialog();
                return true;
            case R.id.test:
                ShowDialogUsers();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    public void ShowDialogUsers() {

        final AlertDialog.Builder popDialog = new AlertDialog.Builder(getActivity());
        final LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);

        final View Viewlayout = inflater.inflate(R.layout.users_online_dialog,
                (ViewGroup) getView().findViewById(R.id.layout_dialog));

        final TextView item2 = (TextView) Viewlayout.findViewById(R.id.txtItem2); // txtItem2

        popDialog.setTitle("Users online");
        popDialog.setView(Viewlayout);
        item2.setText("");

        for (int i = 0; i < options.size(); i++) {
            item2.append(options.get(i));
            item2.append("\n");
        }

        popDialog.create();
        popDialog.show();

    }

    public void ShowDialog() {

        final AlertDialog.Builder popDialog = new AlertDialog.Builder(getActivity());
        final LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);

        final View Viewlayout = inflater.inflate(R.layout.activity_dialog,
                (ViewGroup) getView().findViewById(R.id.layout_dialog));

        final TextView item2 = (TextView) Viewlayout.findViewById(R.id.txtItem2); // txtItem2

        popDialog.setIcon(android.R.drawable.ic_menu_mylocation);
        popDialog.setTitle("Change your radius");
        popDialog.setView(Viewlayout);
        //  seekBar2
        SeekBar seek2 = (SeekBar) Viewlayout.findViewById(R.id.seekBar2);
        seek2.setProgress(Constants.radius);
        item2.setText("" + Constants.radius + "m radius");
        seek2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //Do something here with new value
                Constants.radius = progress;
                item2.setText("" + progress + "m radius");
            }

            public void onStartTrackingTouch(SeekBar arg0) {
                // TODO Auto-generated method stub

            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }
        });


        // Button OK
        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Log.i(TAG, "Ny radie." + Constants.radius + "m radie bestämt");
                    }

                });


        popDialog.create();
        popDialog.show();

    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }


    private void smackUpNotification(String ausername, String message) {
        if (!ausername.equals(username)) {

            NotificationCompat.Builder noti = new NotificationCompat.Builder(getActivity());
            noti.setContentTitle(ausername + ": ");
            noti.setContentText(message);
            noti.setSmallIcon(R.drawable.ic_launcher);
            noti.setAutoCancel(true);

            Intent notificationIntent = new Intent(Intent.ACTION_MAIN);
            notificationIntent.setClass(getActivity().getApplicationContext(), MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent notificationPendingIntent = PendingIntent.getActivity(getActivity(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            noti.setContentIntent(notificationPendingIntent);

            NotificationManager mNotificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);

            mNotificationManager.notify(1, noti.build());
        }
    }

    private void addMessage(String username, String message, String longitude1, String latitude1) {
        double distance = calculateDistance(longitude1, latitude1);
        String completeRadius = Constants.radius + "." + String.valueOf(00);
        Log.i(TAG, "" + distance + "m ifrån");
        Log.i(TAG, "" + completeRadius + "m radie bestämt");

        if (distance <= Double.parseDouble(completeRadius)) {

            mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                    .username(username + ": ").message(message).build());
            mAdapter.notifyItemInserted(mMessages.size() - 1);
            scrollToBottom();
            smackUpNotification(username, message);
        }
    }

    private double calculateDistance(String longitude1, String latitude1) {
        Location locationA = new Location("point A");

        locationA.setLatitude(Double.parseDouble(latitude1));
        locationA.setLongitude(Double.parseDouble(longitude1));

        Location locationB = new Location("point B");

        locationB.setLatitude(latitude);
        locationB.setLongitude(longitude);

        float distance = locationA.distanceTo(locationB);

        return distance;

    }

    private void attemptSend() {
        if (null == username) return;
        if (!mSocket.connected()) return;

        String message = mInputMessageView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            mInputMessageView.requestFocus();
            return;
        }


        mInputMessageView.setText("");
        addMessage(username, message, String.valueOf(longitude), String.valueOf(latitude));

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("username", username);
            jsonObject.put("message", message);
            jsonObject.put("longitude", longitude);
            jsonObject.put("latitude", latitude);
            mSocket.emit("new message", jsonObject);
        } catch (JSONException e) {
            Log.i(TAG, e.toString());
        }


        // perform the sending message attempt.
        //mSocket.emit("new message", username, message, String.valueOf(longitude), String.valueOf(latitude));
    }

    private void startSignIn() {
        username = null;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void leave() {
        username = null;
        mSocket.disconnect();
        mSocket.connect();
        startSignIn();
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isConnected) {
                        if (null != username)
                            mSocket.emit("add user", username);
                        Toast.makeText(getActivity().getApplicationContext(),
                                R.string.connect, Toast.LENGTH_LONG).show();
                        isConnected = true;
                    }
                }
            });
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    isConnected = false;
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.disconnect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    String latitude;
                    String longitude;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                        latitude = data.getString("latitude");
                        longitude = data.getString("longitude");
                        Log.i(TAG, "" + latitude + longitude);
                    } catch (JSONException e) {
                        return;
                    }
                    addMessage(username, message, longitude, latitude);
                }
            });
        }
    };

    private Emitter.Listener onUpdateUsers = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    Iterator itr = data.keys();
                    options.clear();

                    int i = 0;
                    while (itr.hasNext()) {
                        String key = itr.next().toString();
                        options.add(i, (key));
                        i++;
                    }
                    Collections.reverse(options);
                }
            });
        }
    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                }
            });
        }
    };

    public void printLocation() {
        if (broadcastReceiver == null) {
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    textView.setText("\n" + intent.getExtras().get("coordinates"));
                    latitude = (double) intent.getExtras().get("latitude");
                    longitude = (double) intent.getExtras().get("longitude");

                }
            };
        }
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter("location_update"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Intent intent = new Intent(getActivity(), BackgroundLocationService.class);
                    getActivity().startService(intent);

                } else {

                    // permission denied, boo! Disable the functionality that depends on this permission.
                }
                return;
            }
        }
    }

}

