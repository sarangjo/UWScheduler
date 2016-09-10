package com.sarangjoshi.uwcalendar;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.sarangjoshi.uwcalendar.content.Day;
import com.sarangjoshi.uwcalendar.content.Request;
import com.sarangjoshi.uwcalendar.content.Schedule;
import com.sarangjoshi.uwcalendar.content.Segment;
import com.sarangjoshi.uwcalendar.data.FirebaseData;
import com.sarangjoshi.uwcalendar.fragments.ChangePasswordFragment;
import com.sarangjoshi.uwcalendar.fragments.RequestScheduleFragment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The central activity for the application's workflow. Once a user is logged in, they reach the
 * home screen (here!) from where they can adjust their class schedules, issue connection requests
 * to other users, and view established connections with other users.
 */
public class HomeActivity extends AppCompatActivity
        implements RequestScheduleFragment.NameSelectedListener, ChangePasswordFragment.ChangePasswordListener, Schedule.RetrieveSchedulesListener, FirebaseData.UsersLoadedListener {
    private static final String TAG = "HomeActivity";

    // Singletons
    private FirebaseData fb;

    // View/Controller
    private ListView mRequestsList;
    private ListView mConnectionsList;

    private ProgressDialog mDialog;

    // Model
    private List<Request> mRequests;
    private List<ConnectionProperty> mConnections;

    //// ACTIVITY METHODS ////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fb = FirebaseData.getInstance();
        fb.setUsersListener(this);

        // Get initial user-specific data
        fb.getCurrentUserRef().child(FirebaseData.USERNAME_KEY).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                TextView usernameTextView = (TextView) findViewById(R.id.username_text_view);
                if (usernameTextView != null)
                    try {
                        usernameTextView.setText(snapshot.getValue().toString());
                    } catch (NullPointerException e) {
                        usernameTextView.setText(getResources().getString(R.string.name_error));
                    }
            }

            @Override
            public void onCancelled(DatabaseError DatabaseError) {
                // TODO lel
                Log.d(TAG, "User download cancelled.");
            }
        });

        // Requests
        startService(new Intent(this, ReceiveRequestsService.class));

        fb.setRequestsValueListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // TODO: Update requests
                Toast.makeText(HomeActivity.this, "Request data changed", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCancelled(DatabaseError DatabaseError) {
                Log.d("Download error", DatabaseError.getMessage());
            }
        });

        mRequestsList = (ListView) findViewById(R.id.requests_list);
        mRequestsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                DialogFragment dialog = new DialogFragment() {
                    @NonNull
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);

                        // Setup "accept decline" dialog
                        builder.setTitle("Accept request.").setMessage("Accept request?").setPositiveButton(" Accept", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Accept request
                                new AcceptRequestTask().execute(position);
                            }
                        }).setNegativeButton("Decline", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });

                        return builder.create();
                    }
                };
                dialog.show(getSupportFragmentManager(), "acceptRequestFragment");
            }
        });
        mRequests = new ArrayList<>();

        // Connections management
        mConnectionsList = (ListView) findViewById(R.id.connections_list);
        mConnectionsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO: Open connection page
                Intent intent = new Intent(HomeActivity.this, ConnectionActivity.class);
                intent.putExtra(FirebaseData.CONNECTION_ID_KEY, mConnections.get(position).id);
                startActivity(intent);
            }
        });
        mConnectionsList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                new DeleteConnectionTask().execute();
                return true;
            }
        });
        mConnections = new ArrayList<>();

        // Check if users loaded
        if (!fb.getAllUsers().isEmpty()) {
            usersLoaded();
        }

        mDialog = new ProgressDialog(this);
    }

    //// VIEW UPDATE METHODS ////

    private void updateRequests(Intent intent) {
        mRequests.clear();

        String[] keys = intent.getStringArrayExtra(ReceiveRequestsService.KEYS);
        String[] ids = intent.getStringArrayExtra(ReceiveRequestsService.IDS);

        for (int i = 0; i < keys.length; ++i) {
            mRequests.add(new Request(keys[i], fb.getUsernameAndIdFromId(ids[i])));
        }

        ArrayAdapter<Request> adapter = new ArrayAdapter<Request>(this, android.R.layout.simple_list_item_1, mRequests) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setText(getItem(position).usernameAndId.username);
                return v;
            }
        };

        mRequestsList.setAdapter(adapter);
    }

    /**
     * Sets the connections view from the DataSnapshot.
     */
    private void updateConnections(DataSnapshot connections) {
        mConnections.clear();
        for (DataSnapshot conn : connections.getChildren()) {
            try {
                String id = conn.child(FirebaseData.CONNECTION_ID_KEY).getValue().toString();
                FirebaseData.UsernameAndId with = fb.getUsernameAndIdFromId(conn.child(FirebaseData.CONNECTION_WITH_KEY).getValue().toString());
                if (with != null) {
                    // Only add the connection if the name has loaded
                    mConnections.add(new ConnectionProperty(id, with));
                }
            } catch (NullPointerException ignored) {
                // TODO what is causing this?
            }
        }
        ArrayAdapter<ConnectionProperty> adapter = new ArrayAdapter<ConnectionProperty>(this, android.R.layout.simple_list_item_1, mConnections) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setText(getItem(position).with.username);
                return v;
            }
        };
        mConnectionsList.setAdapter(adapter);
    }

    //// BUTTONS CLICK RESPONSES ////

    public void logoutClicked(View view) {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void requestScheduleClicked(View view) {
        // First need to download the data, then call the corresponding fragment
        DialogFragment fragment = new RequestScheduleFragment();
        fragment.show(getSupportFragmentManager(), "requestSchedule");
    }

    public void viewClassesClicked(View view) {
        startActivity(new Intent(this, ScheduleActivity.class));
    }

    public void changePasswordClicked(View view) {
        DialogFragment changePassword = new ChangePasswordFragment();
        changePassword.show(getSupportFragmentManager(), "changePassword");
    }

    //// FRAGMENT RESPONSES ////

    @Override
    public void usernameSelected(final FirebaseData.UsernameAndId selected) {
        mDialog.setMessage("Requesting schedule...");
        // First check if a request has already been issued
        DatabaseReference reqRef = fb.getRequestsRef().child(selected.id);
        reqRef.orderByValue().equalTo(fb.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Iterator<DataSnapshot> iter = dataSnapshot.getChildren().iterator();
                if (iter.hasNext()) {
                    // There is already a request
                    Toast.makeText(HomeActivity.this, "Request to " + selected.username + " has already been made.", Toast.LENGTH_LONG).show();
                } else {
                    // No request has been made so far.
                    // TODO: Check if a connection exists

                    // Add a request to the object
                    fb.getRequestsRef().child(selected.id).push().setValue(fb.getUid());
                    Toast.makeText(HomeActivity.this, "Request made to " + selected.username, Toast.LENGTH_LONG).show();
                }
                mDialog.hide();
            }

            @Override
            public void onCancelled(DatabaseError DatabaseError) {
                // TODO as always
            }
        });
    }

    @Override
    public void passwordChanged(String oldPass, String newPass) {
        // TODO: don't need the old password at all
        mDialog.setMessage("Changing password...");
        mDialog.show();
        fb.getUser().updatePassword(newPass)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Password changed! Hurrah.
                            Toast.makeText(HomeActivity.this, "Password changed.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(HomeActivity.this, "Error", Toast.LENGTH_LONG).show();
                        }
                        mDialog.hide();
                    }
                });
    }

    @Override
    public void schedulesRetrieved(Request r, Schedule schedule1, Schedule schedule2) {
        // Now that the schedules have been retrieved, compile them
        Map<String, List<Day>> connectedQuarters = Schedule.connect(schedule1, schedule2);

        if (connectedQuarters.isEmpty()) {
            // For now, do nothing because there was an issue with the schedules
            return;  // TODO: what?
        }

        // Add to the connections collection
        DatabaseReference connRef = fb.getConnectionsRef().push();

        // 1. Participants
        DatabaseReference participantsRef = connRef.child(FirebaseData.PARTICIPANTS_KEY);

        participantsRef.push().setValue(r.usernameAndId.id);
        participantsRef.push().setValue(fb.getUid());

        // 2. Actual connection data
        DatabaseReference dataRef = connRef.child(FirebaseData.DATA_KEY);

        // TODO: probably abstract this out
        for (String qtr : connectedQuarters.keySet()) {
            DatabaseReference qtrRef = dataRef.child(qtr);
            List<Day> days = connectedQuarters.get(qtr);
            for (int i = 0; i < days.size(); ++i) {
                Day d = days.get(i);
                DatabaseReference dayRef = qtrRef.child(i + "");

                List<Segment> segs = d.getSegments();
                for (Segment s : segs) {
                    // Special case to save a fully empty day
                    if (s.classesMap.isEmpty() && segs.size() != 1)
                        continue;
                    fb.saveSegment(dayRef.push(), s);
                }
            }
        }

        // Add to both users' connections list:
        // Self
        DatabaseReference userConn = fb.getCurrentUserRef().child(FirebaseData.CONNECTIONS_KEY).push();
        userConn.child(FirebaseData.CONNECTION_ID_KEY).setValue(connRef.getKey());
        userConn.child(FirebaseData.CONNECTION_WITH_KEY).setValue(r.usernameAndId.id);

        // Other
        userConn = fb.getUsersRef().child(r.usernameAndId.id).child(FirebaseData.CONNECTIONS_KEY).push();
        userConn.child(FirebaseData.CONNECTION_ID_KEY).setValue(connRef.getKey());
        userConn.child(FirebaseData.CONNECTION_WITH_KEY).setValue(fb.getUid());

        // Delete the request
        fb.getRequestsRef().child(fb.getUid()).child(r.key).removeValue();

        mDialog.hide();
    }

    @Override
    public void usersLoaded() {
        // Listen to user connection changes
        ValueEventListener connVEL = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                updateConnections(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError DatabaseError) {

            }
        };
        fb.setConnectionsValueListener(connVEL);
    }

    //// INNER CLASSES ////

    /**
     * Accept a connection request. Parameters: position
     */
    private class AcceptRequestTask extends AsyncTask<Integer, Void, Void> {
        @Override
        protected void onPreExecute() {
            mDialog.setMessage("Accepting request...");
            mDialog.show();
        }

        @Override
        protected Void doInBackground(Integer... params) {
            Request r = mRequests.get(params[0]);

            // Request schedules to combine
            Schedule.request(r, HomeActivity.this);

            return null;
        }
    }

    /**
     * Deletes a connection.
     */
    private class DeleteConnectionTask extends AsyncTask<Integer, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            mDialog.setMessage("Deleting connection...");
            mDialog.show();
        }

        @Override
        protected Boolean doInBackground(Integer... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            mDialog.hide();
        }
    }

    public class ConnectionProperty {
        public String id;
        public FirebaseData.UsernameAndId with;

        public ConnectionProperty(String id, FirebaseData.UsernameAndId with) {
            this.id = id;
            this.with = with;
        }
    }
}
