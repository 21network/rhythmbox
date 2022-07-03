package com.drips.rhythmbox.services;

// region IMPORTS
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.drips.rhythmbox.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
// endregion IMPORTS

public class UnMediaPlayer extends Service {

    // MEDIA SERVICE - Media Player and Notification Bundle

    // Identifier in logcat
    private static final String TAG = "MSL : MEDIA SERVICE";

    private Context appContext;
    private Intent globalIntent;

    // region Notification Service

    // Android 8+ Requires channel declarations
    private static final String CID_1 = "C1";
    NotificationChannel notifChannelOne;

    // Unique notification idea for updates
    private static final int NOTID = 7777;

    // Notification Builder
    NotificationCompat.Builder notifBuilder;
    NotificationManagerCompat nM;

    // Initializer; sets context and intent
    public void initNotification(Context context, Intent inte) {
        nM = NotificationManagerCompat.from(context);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, inte, PendingIntent.FLAG_IMMUTABLE);

        notifBuilder = new NotificationCompat.Builder(context, CID_1)
                .setSmallIcon(R.drawable.notico)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setSilent(true);
        generateNotification(context);
    }

    // region Notification Controls : CHANNEL, GENERATE, UPDATE, PURGE

    // Creates notification channel if Android 8+
    public void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifChannelOne = new NotificationChannel(CID_1, "Channel 1", NotificationManager.IMPORTANCE_HIGH);
            notifChannelOne.setDescription("Media Channel");
            NotificationManager mnM = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            mnM.createNotificationChannel(notifChannelOne);
        }
    }
    // Blank notification
    public void generateNotification(Context context) {
        createNotificationChannel(context);
        notifBuilder.setContentText(null);
        notifBuilder.setContentTitle(null);
        nM.notify(NOTID, notifBuilder.build());
    }
    // Filled notification; track and artist set
    public void updateNotification(String track, String artist) {
        notifBuilder.setContentTitle(track);
        notifBuilder.setContentText(artist);
        nM.notify(NOTID, notifBuilder.build());
    }
    // Deleting notification
    public void purgeNotification() {
        nM.cancel(NOTID);
    }

    // endregion Notification Controls


    // endregion Notification Service

    // region Media Service

    // Media Player instantiation
    private ExoPlayer mp;
    private IBinder mBinder = new CommBinder();

    // Media Player State
    private boolean isPlaying = false;

    // Asset
    private Uri resource;

    public UnMediaPlayer(Context context, Intent inte){
        appContext = context;
        globalIntent = inte;
        initNotification(context, globalIntent);
    }

    // region Communication

    // Allows public methods to be called by service binders
    public class CommBinder extends Binder {
        public UnMediaPlayer getService(){
            Log.d(TAG, "getService: CommBinder service requested");
            return UnMediaPlayer.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: UnMediaPlayer bound");
        return mBinder;
    }

    // endregion Communication

    // region Program State Functions
    @Override
    public void onCreate() {
        super.onCreate();
    }
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopMedia();
        purgeNotification();
        stopSelf();
    }
    // endregion Program State Functions

    // region Media Controllers
    public void playMedia(Uri rawID){
        if (mp == null){
            mp = new ExoPlayer.Builder(appContext).build();
            mp.setMediaItem(MediaItem.fromUri(rawID));
            mp.setAudioSessionId(777);
            mp.setHandleAudioBecomingNoisy(true);
            mp.addListener(new Player.Listener(){
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    switch(playbackState){
                        case ExoPlayer.STATE_ENDED:
                            long stamp = System.currentTimeMillis() / 1000L;
                            Intent mediaIntention = new Intent("media_comm");
                            mediaIntention.putExtra("next", true);
                            mediaIntention.putExtra("stamp", stamp);
                            LocalBroadcastManager.getInstance(appContext).sendBroadcast(mediaIntention);
                            break;
                    }
                }
            });
            Log.d(TAG, "playMedia: MP created");
        }
        play();
        isPlaying = true;
        Log.d(TAG, "playMedia: Requested playback");
    }
    public void play(){
        if (mp != null){
            mp.prepare();
            mp.play();
        }
        isPlaying = true;
        Log.d(TAG, "pause: Requested play");
    }
    public void pause(){
        if (mp != null){
            mp.pause();
        }
        isPlaying = false;
        Log.d(TAG, "pause: Requested pause");
    }
    public void stopMedia(){
        if (mp != null){
            pause();
            mp.release();
            mp = null;
        }
        isPlaying = false;
        Log.d(TAG, "stopMedia: Requested stop");
    }
    // endregion Media Controllers

    // region Accessors and Mutators
    public ExoPlayer getMp() {
        return mp;
    }
    public Uri getResource() {
        return resource;
    }
    public void setResource(Uri newRaw){
        Log.d(TAG, "setResource: Changing track.");
        resource = newRaw;
        stopMedia();
        playMedia(newRaw);
        //@@@FLAG notification meta details
        updateNotification(cut("" + resource), "");
    }
    // endregion Accessors and Mutators

    // endregion Media

    // region Helper Functions
    public String cut(String uncut){
        String cutter = uncut.substring(uncut.lastIndexOf('/') + 1, uncut.lastIndexOf('.'));
        return cutter;
    }
    public boolean getIsPlaying(){
        return isPlaying;
    }
    // endregion Helper Functions

    // region Output Sink Changes
    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pause();
            }
        }
    }

    // endregion Output Sink Changes

}
