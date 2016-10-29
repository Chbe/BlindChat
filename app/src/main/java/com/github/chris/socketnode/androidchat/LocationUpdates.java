package com.github.chris.socketnode.androidchat;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderApi;


/**
 * Created by HP-HP on 26-11-2015.
 */
public class LocationUpdates extends IntentService {

    private String TAG = this.getClass().getSimpleName();

    public LocationUpdates() {
        super("Fused Location");
    }

    public LocationUpdates(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        Log.i(TAG, "onHandleIntent");

        Location location = intent.getParcelableExtra(FusedLocationProviderApi.KEY_LOCATION_CHANGED);
        if(location !=null)
        {
            Log.i(TAG, "onHandleIntent " + location.getLatitude() + "," + location.getLongitude());

            Intent i = new Intent("location_update");
            i.putExtra("latitude",location.getLatitude());
            i.putExtra("longitude", location.getLongitude());
            i.putExtra("coordinates",location.getLongitude()+" "+location.getLatitude());
            sendBroadcast(i);
        }
    }
}
