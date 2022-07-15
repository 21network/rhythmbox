package com.drips.rhythmbox;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.drips.rhythmbox.services.DataStreamBus;
import com.drips.rhythmbox.services.MetaFace;
import com.drips.rhythmbox.services.PlaylistInterface;
import com.drips.rhythmbox.services.UnMediaPlayer;
import com.drips.rhythmbox.ui.EPRecyclerAdapter;
import com.drips.rhythmbox.ui.ERecyclerAdapter;
import com.drips.rhythmbox.ui.RecyclerAdapter;
import com.gauravk.audiovisualizer.visualizer.WaveVisualizer;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    // MAIN ACTIVITY --- App Management

    // Identifier in logcat
    public static final String TAG = "MAL - Main Activity";

    // region Fields

    // View State Toggles
    private boolean trackView;
    private boolean playlistView;
    private boolean playlistTrackView;
    private boolean statView;
    private boolean beatView;

    private boolean buttonsExpanded = false;
    private boolean mustGraph = false;
    private boolean editting = false;

    // Track View Elements
    private Button playPauseB;
    private SeekBar seekBarB;
    private Drawable playicoD;
    private Drawable pauseicoD;
    private TextView currentPositionT;
    private TextView finalPositionT;
    private ImageView almbumArty;

    private Button addMediaFileB;
    private Button addYoutubeAudioB;
    private Button addMediaFolderB;
    private Button exportPlaylistB;

    public final int PERMISSION_REQUEST_CODE = 504;

    // Media Service instantiation
    UnMediaPlayer ms;
    boolean msBound = false;

    // Data Manager for XML and JSON
    DataStreamBus dbs;

    // Playlist Interface for Media Session
    PlaylistInterface pli;

    // Handler for updating seekbar
    Handler skHandler = new Handler();

    // Manager for recycled views
    ERecyclerAdapter era;
    EPRecyclerAdapter erap;

    // Event Touch Trackers
    boolean doneOld = false, doneNew = false;
    float oldX, oldY, newX, newY;

    // Visualizer
    WaveVisualizer visualizer;

    // endregion Fields

    // Service Connection for function calls to media service
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            UnMediaPlayer.CommBinder binder = (UnMediaPlayer.CommBinder) service;
            ms = binder.getService();
            msBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            msBound = false;
        }
    };

    // Request for external storage access
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(getApplicationContext(), "Storage access granted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Storage access denied.\nApp might not function as intended.", Toast.LENGTH_SHORT).show();
                }
            });

    // General Initializations
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Registering Receivers for one-way communication between services
        LocalBroadcastManager.getInstance(this).registerReceiver(raptorComm, new IntentFilter("raptor_comm"));
        LocalBroadcastManager.getInstance(this).registerReceiver(mediaComm, new IntentFilter("media_comm"));

        // Connecting to data manager and playlist interface
        dbs = new DataStreamBus(getObbDir().toString());
        pli = new PlaylistInterface(getApplicationContext(), dbs);

        // Starting and binding to Media Service
        ms = new UnMediaPlayer(getApplicationContext(), getIntent());
        Intent intention = new Intent(this, UnMediaPlayer.class);
        startService(intention);
        bindService(intention, connection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "onCreate: Serviced" + msBound);

        // Opens on playlist view
        initPlayListView();

        // Verifying permissions
        if( ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            String[] permissionArr = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(MainActivity.this, permissionArr, PERMISSION_REQUEST_CODE);
        }

    }

    // region View Managers

    // region Layout Initializers

    private void initPlayListView(){

        setContentView(R.layout.list_view);

        // connecting elements
        addMediaFileB = (Button) findViewById(R.id.newMediaFileButton);
        addYoutubeAudioB = (Button) findViewById(R.id.newYTFileButton);
        addMediaFolderB = (Button) findViewById(R.id.newFolderResourceButton);
        exportPlaylistB = (Button) findViewById(R.id.export);

        // buttons initially expanded
        buttonsExpanded = true;
        closeExpansion();

        // getting playlists
        String[] playlists = dbs.getPlaylists();
        if (playlists != null) {
            raptor(playlists, getBlank(playlists.length), getBlank(playlists.length), getBlank(playlists.length));
        } else {
            raptor(null, null, null, null);
        }
        // Hiding floating and save buttons
        Button fab = (Button) findViewById(R.id.floatingActionButton);
        fab.setVisibility(View.INVISIBLE);
        findViewById(R.id.saveButton).setVisibility(View.INVISIBLE);

        if ((pli.getPosition() != 0)){
            fab.setVisibility(View.VISIBLE);
        }

        updateView("playlistview");

    }

    private void initPlayListTrackView(String playListName){

        setContentView(R.layout.list_view);

        // connecting elements
        addMediaFileB = (Button) findViewById(R.id.newMediaFileButton);
        addYoutubeAudioB = (Button) findViewById(R.id.newYTFileButton);
        addMediaFolderB = (Button) findViewById(R.id.newFolderResourceButton);
        exportPlaylistB = (Button) findViewById(R.id.export);

        findViewById(R.id.saveButton).setVisibility(View.INVISIBLE);
        findViewById(R.id.createButton).setVisibility(View.VISIBLE);

        // buttons initially expanded
        buttonsExpanded = true;
        closeExpansion();

        // getting tracks in playlist
        String[][] playlist = pli.getPlaylist(stripper(playListName));
        raptor(playlist[0], playlist[1], getBlank(playlist[0].length), playlist[2]);
        pli.setBrowsingPlaylist(stripper(playListName));

        // Getting current position if it exists
        int position = playlist[0].length - 1;
        Log.d(TAG, "initPlayListTrackView: " + position);
        if (pli != null && pli.getBrowsingPlaylist() != null){
            position = pli.getPosition() - 3;
            if (position < 0 || position > (pli.getPlaylist(pli.getBrowsingPlaylist())[0].length)){
                position = playlist[0].length - 1;
            }
        }
        ((RecyclerView)findViewById(R.id.recyclerView)).scrollToPosition(position);

        // floating button for quick navigation to current track
        Button fab = (Button) findViewById(R.id.floatingActionButton);
        fab.setVisibility(View.INVISIBLE);
        if ((pli.getPosition() != 0)){
            fab.setVisibility(View.VISIBLE);
        }

        updateView("playlisttrackview");

    }

    private void initTrackView(){

        setContentView(R.layout.track_view);

        // applying rounded effect to album art
        findViewById(R.id.albumArt).setClipToOutline(true);

        // connecting elements
        playPauseB = (Button) findViewById(R.id.playpause);
        seekBarB = (SeekBar) findViewById(R.id.seekBar);
        almbumArty = (ImageView) findViewById(R.id.albumArt);

        // Initializing resources to modify elements
        playicoD = getResources().getDrawable(R.drawable.playico);
        pauseicoD = getResources().getDrawable(R.drawable.pauseico);

        currentPositionT = (TextView) findViewById(R.id.currentPosition);
        finalPositionT = (TextView) findViewById(R.id.finalPosition);

        // region Dynamic Playlist Navigation

        (findViewById(R.id.trackViewLayout)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch(motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN: {
                        oldX = motionEvent.getX();
                        oldY = motionEvent.getY();
                        doneOld = true;
                        break; }
                    case MotionEvent.ACTION_UP: {
                        newX = motionEvent.getX();
                        newY = motionEvent.getY();
                        doneNew = true;
                        break; }
                }
                if (doneOld && doneNew){
                    doneOld = false;
                    doneNew = false;
                    Log.d(TAG, "onTouch: FROM " + oldX + " TO " + newX);
                    if (oldX == newX){
                        findViewById(R.id.playpause).performClick();
                    }
                    if (oldX > newX){
                        if ( (oldX - newX) < 50){
                            findViewById(R.id.playpause).performClick();
                        } else {
                            findViewById(R.id.next).performClick();
                        }
                    } else {
                        if ((newX - oldX) < 50){
                            findViewById(R.id.playpause).performClick();
                        } else {
                            findViewById(R.id.previous).performClick();
                        }
                    }
                }
                return true;
            }
        });

        // endregion Dynamic Playlist Navigation

        updateView("trackview");

    }

    private void initStatView(String playlistName, String trackName){
        setContentView(R.layout.stats);
        // connecting elements
        TextView statTextView = ((TextView) findViewById(R.id.statView));
        statTextView.setText(dbs.readRecord(playlistName, trackName));
        statTextView.setMovementMethod(new ScrollingMovementMethod());
        statTextView.setScrollY(statTextView.getLineCount());
        updateView("statview");

    }

    public void updateView(String view){
        trackView = false;
        playlistView = false;
        playlistTrackView = false;
        statView = false;
        beatView = false;
        switch (view){
            case "trackview": trackView = true; break;
            case "playlistview": playlistView = true; break;
            case "playlisttrackview": playlistTrackView = true; break;
            case "statview": statView = true; break;
            case "beatsview": beatView = true; break;
        }
    }

    // endregion Layout Initializers

    // region Button Controllers

    public void closeExpansion(){
        // closing expanded buttons (that overlay UI)
        if (buttonsExpanded && addMediaFileB != null && addMediaFolderB != null && addYoutubeAudioB != null && exportPlaylistB != null){
            addMediaFileB.setVisibility(View.INVISIBLE);
            addYoutubeAudioB.setVisibility(View.INVISIBLE);
            addMediaFolderB.setVisibility(View.INVISIBLE);
            exportPlaylistB.setVisibility(View.INVISIBLE);
            buttonsExpanded = false;
        }
    }

    public void backPress(View view){
        if (statView || beatView){
            // to track
            if (beatView && visualizer != null){
                visualizer.release();
            }
            initTrackView();
            String[] trackMeta = pli.getCurrentTrack();
            updateTrackView(trackMeta);
            findViewById(R.id.playpause).performClick();
        }
        else if (trackView){
            // to playlist tracks
            initPlayListTrackView(pli.getBrowsingPlaylist());
        } else if (playlistTrackView){
            // to playlists
            if (editting){
                initPlayListTrackView(pli.getBrowsingPlaylist());
                findViewById(R.id.saveButton).setVisibility(View.INVISIBLE);
                editting = false;
            } else {
                initPlayListView();
            }
        } else if (playlistView && editting){
            initPlayListView();
        }
    }

    public void showStatsButton(View view){
        showStats();
    }

    public void floaterClick(View view){
        String[] currentTrack = pli.getCurrentTrack();
        initTrackView();
        updateTrackView(currentTrack);
        findViewById(R.id.playpause).performClick();
    }

    public void editList(View view){
        // getting playlists/tracks
        if (playlistView){
            String[] playlists = dbs.getPlaylists();
            epraptor(playlists);
            editting = true;
            findViewById(R.id.saveButton).setVisibility(View.VISIBLE);
        } else if (playlistTrackView) {
            String[][] playlist = pli.getPlaylist(pli.getBrowsingPlaylist());
            eraptor(pli.getBrowsingPlaylist(), playlist[0], playlist[1], getBlank(playlist[0].length), playlist[2], playlist[3]);
            editting = true;
            findViewById(R.id.saveButton).setVisibility(View.VISIBLE);
        }
    }

    public void showStats(){
        String playlist = pli.getCurrentPlaylist();
        String[] trackName = pli.getCurrentTrack();
        initStatView(playlist, stripper(trackName[0] + "x" + trackName[1]));
        ((TextView) findViewById(R.id.statTrackTitle)).setText(trackName[0]);
        LineChart lc = (LineChart) findViewById(R.id.statChart);
        Log.d(TAG, "showStats: mustGraph?" + mustGraph);
        if (mustGraph) {
            // @@@FLAG GRAPH FEATURE EXPANSION
            int[][] monthMatrixInput = dbs.getMonthMatrix(pli.getBrowsingPlaylist(), stripper(pli.getCurrentTrack()[0] + "x" + pli.getCurrentTrack()[1]));
            if (monthMatrixInput != null){
                initGraph(monthMatrixInput, lc);
            }
        } else {
            lc.setVisibility(View.INVISIBLE);
        }
    }

    public void saveEditedPlaylist(View view){
        if (playlistTrackView){
            findViewById(R.id.saveButton).setVisibility(View.INVISIBLE);
            if (era != null){
                dbs.updatePlaylist(stripper(era.getPlaylistName()), era.getUpdatedPlaylist());
            }
            ((Button) findViewById(R.id.backButton)).performClick();
        } else if (playlistView){
            findViewById(R.id.saveButton).setVisibility(View.INVISIBLE);
            if (erap != null){
                dbs.updateParentPlaylist(erap.getUpdatedPlaylists());
                dbs.deleteParentPlaylists(erap.getDeletedPlaylists());
            }
            ((Button) findViewById(R.id.backButton)).performClick();
        }
    }

    public void exportPlaylist(View view){
        if (pli.getBrowsingPlaylist() != null){
            Date date = Calendar.getInstance().getTime();
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            String stamp = dateFormat.format(date);
            String outpath = verifyPublicStorage() + File.separator + "exports"  + File.separator +  pli.getBrowsingPlaylist() + stamp;
            String rPath = getObbDir() + File.separator + "recorder" + File.separator + pli.getBrowsingPlaylist();
            String pPath = getObbDir() + File.separator + "playlists" + File.separator + pli.getBrowsingPlaylist();

            File outFile = new File(outpath);
            File rDir = new File(rPath);
            File rDirOut = new File(outFile + File.separator + "records");
            rDirOut.mkdir();
            File pFile = new File(pPath);
            outFile.mkdir();
            if (new File(rPath).exists()){
                try {
                    FileUtils.copyDirectory(rDir, rDirOut);
                } catch (IOException e) {
                    Log.d(TAG, "exportPlaylist: Directory copy failed for " + rPath + " to " + outpath);
                }
            } else {
                Log.d(TAG, "exportPlaylist: Doesn't exist" + rPath);
            }
            if (new File(pPath).isFile()){
                try {
                    FileUtils.copyFileToDirectory(pFile, outFile, true);
                } catch (IOException e) {
                    Log.d(TAG, "exportPlaylist: File copy failed for " + rPath + " to " + outpath + "\n" + e.getMessage());
                }
            } else {
                Log.d(TAG, "exportPlaylist: Doesn't exist" + pPath);
            }
            Log.d(TAG, "exportPlaylist: Export Complete");
            closeExpansion();
            Toast.makeText(getApplicationContext(), "Export complete", Toast.LENGTH_SHORT).show();
        }
    }

    public void revealBeats(View view){
        showBeats();
    }

    // endregion Button Controllers

    // region Navigation Controller
    @Override
    public void onBackPressed() {
        ((Button)findViewById(R.id.backButton)).performClick();
    }
    //endregion Navigation Controller

    // region Seekbar
    public void initSeeker(){
        if (ms.getMp() != null) {
            Log.d(TAG, "initSeeker: Seeker activated");
            SeekBar sk = (SeekBar) (findViewById(R.id.seekBar));
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ms.getMp() != null) {
                        int cp = (int) ms.getMp().getCurrentPosition();
                        int mCurrentPosition = (int) ms.getMp().getCurrentPosition() / 10;
                        sk.setProgress(mCurrentPosition);
                        currentPositionT.setText( String.format("%02d", ((cp/1000)/60)) + ":" + String.format("%02d", ((cp/1000)%60))) ;
                        seekerFin();
                    }
                    skHandler.postDelayed(this, 100);
                }
            });
            // Updating track position when seekbar thumb changed
            sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (ms.getMp() != null && fromUser) {
                        ms.getMp().seekTo(progress * 10);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }
    public void seekerFin(){
        if (trackView){
            long duration = ms.getMp().getDuration();
            if (duration > 0){
                finalPositionT.setText( String.format("%02d", ((duration/1000)/60)) + ":" + String.format("%02d", ((duration/1000)%60))) ;
            } else {
                finalPositionT.setText("00:00");
            }
            SeekBar sk = (SeekBar) (findViewById(R.id.seekBar));
            sk.setMax((int)(duration / 10));
        }
    }
    //endregion Seekbar

    // region Graphing
    public void initGraph(int[][] monthMatrixInput, LineChart lc){
        lc.setTouchEnabled(true);
        lc.getAxis(YAxis.AxisDependency.RIGHT).setEnabled(false);
        lc.getLegend().setEnabled(false);
        lc.setExtraBottomOffset(5);
        lc.getDescription().setEnabled(false);
        lc.setHighlightPerDragEnabled(false);
        lc.setHighlightPerTapEnabled(false);

        XAxis lcXAxis = lc.getXAxis();
        lcXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        lcXAxis.setTextSize(15);
        lcXAxis.setTextColor(getResources().getColor(R.color.dark_w8));
        lcXAxis.setLabelCount(2);
        lcXAxis.setAvoidFirstLastClipping(true);
        lcXAxis.setValueFormatter(new vf());
        lcXAxis.setGranularity(0.01f);
        lcXAxis.setGranularityEnabled(false);

        YAxis lcYAxis = lc.getAxis(YAxis.AxisDependency.LEFT);
        lcYAxis.setTextSize(15);
        lcYAxis.setGranularity(1);
        lcYAxis.setGranularityEnabled(true);
        lcYAxis.setTextColor(getResources().getColor(R.color.dark_w8));
        lcYAxis.setAxisMinimum(0);

        lc.setBackgroundColor(getResources().getColor(R.color.dark_w2));
        lc.setDragEnabled(true);
        lc.setNoDataTextColor(getResources().getColor(R.color.dark_w8));
        lc.animateY(1000);

        List<Entry> entries = new ArrayList<Entry>();
        int[] xs = monthMatrixInput[0];
        int[] ys = monthMatrixInput[1];
        for (int i = 0; i < xs.length; i++) {
            entries.add(new Entry(xs[i], ys[i]));
            Log.d(TAG, "showStats: ADED*" + xs[i] + "*for*" + ys[i]);
        }

        LineDataSet lds = new LineDataSet(entries, "graph");
        lds.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        lds.setDrawFilled(true);
        lds.setColor(getResources().getColor(R.color.dark_w8));
        lds.setFillColor(getResources().getColor(R.color.dark_w8));
        lds.setCircleColor(getResources().getColor(R.color.dark_w8));
        lds.setDrawValues(false);

        LineData ld = new LineData(lds);
        lc.setData(ld);
        lc.invalidate();
    }
    class vf extends ValueFormatter{
        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            try {
                Log.d(TAG, "getAxisLabel: value" + value);
                String x = Math.round(value) + "";
                DateFormat sd = new SimpleDateFormat("MM/yyyy");
                Date d = sd.parse( x.substring(4) + "/" + x.substring(0, 4));
                return (new SimpleDateFormat("MM/yyyy", Locale.ENGLISH)).format(d);
            } catch (ParseException e) {
                e.printStackTrace();
                Log.d(TAG, "getAxisLabel: PARSE EXCEPTION OCCURED");
                return "";
            }
        }
    }
    public void graphToggle(View view){
        if (!mustGraph) { mustGraph = true; }
        else { mustGraph = false; }
        showStats();
    }
    // endregion Graphing

    // region Beats
    public void showBeats(){
        if( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Audio record permission is required to use audio visualizer library.", Toast.LENGTH_LONG).show();
            String[] permissionArr = {Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(MainActivity.this, permissionArr, PERMISSION_REQUEST_CODE);
        }
        if( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            setContentView(R.layout.beats_view);
            visualizer = findViewById(R.id.visualizer);
            visualizer.setAudioSessionId(ms.getMp().getAudioSessionId());
            visualizer.show();
            updateView("beatsview");
        }
    }
    // endregion Beats

    // endregion View Managers

    // region Media Buttons
    public void playPause(View view){
        if (view.getTag() == "playing"){
            view.setTag("paused");
            playPauseB.setCompoundDrawablesWithIntrinsicBounds(null, null, playicoD, null);
            if (ms != null){ ms.pause(); }
        } else {
            view.setTag("playing");
            playPauseB.setCompoundDrawablesWithIntrinsicBounds(null, null, pauseicoD, null);
            bindService(new Intent(this, UnMediaPlayer.class), connection, Context.BIND_AUTO_CREATE);
            if (ms != null){ ms.play(); }
            seekerFin();
            initSeeker();
        }
    }
    public void next(View view){
        // track view assumed
        String[] newTrackMeta = pli.getNextTrack();
        updateTrackView(newTrackMeta);
        if (new File(newTrackMeta[2]).exists()){
            ms.setResource(Uri.parse(newTrackMeta[2]));
        } else {
            Log.d(TAG, "next: RESOURCE MOVED");
            Toast.makeText(getApplicationContext(), "Track " + newTrackMeta[0] + " was modified!", Toast.LENGTH_SHORT).show();
            view.performClick();
        }
        seekerFin();
        if (!(findViewById(R.id.playpause).getTag() == "playing")){
            playPauseB.performClick();
        }
    }
    public void previous(View view){
        // track view assumed
        String[] newTrackMeta = pli.getPreviousTrack();
        updateTrackView(newTrackMeta);
        if (new File(newTrackMeta[2]).exists()){
            ms.setResource(Uri.parse(newTrackMeta[2]));
        } else {
            Log.d(TAG, "next: RESOURCE NOT FOUND AT EXPECTED POSITION");
            Toast.makeText(getApplicationContext(), "Track " + newTrackMeta[0] + " was modified!", Toast.LENGTH_SHORT).show();
            view.performClick();
        }
        seekerFin();
        if (!(findViewById(R.id.playpause).getTag() == "playing")){
            playPauseB.performClick();
        }
    }
    // endregion Media Buttons

    // region Data Collections

    public void addResource(View view){
        if (playlistView) {

            // Creating new playlist

            // Input Dialog for playlist name
            AlertDialog.Builder builerAlert = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogTheme)).setTitle("New Playlist");
            EditText playlistNameField = new EditText(this);

            // Formatting Dialog Style
            playlistNameField.setLinkTextColor(getResources().getColor(R.color.dark_w8));
            playlistNameField.setTextColor(getResources().getColor(R.color.dark_w8));
            playlistNameField.setBackground(null);
            playlistNameField.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

            // Verifying and creating playlist
            builerAlert.setView(playlistNameField);
            builerAlert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String playlistName = playlistNameField.getText().toString();
                    String playlistFile = playlistName;
                    Log.d(TAG, "onClick: GOT " + playlistName);
                    if (new File(playlistFile).exists()) {
                        Toast.makeText(getApplicationContext(), "Playlist already exists!", Toast.LENGTH_SHORT).show();
                    } else {
                        dbs.createXml(playlistFile, true, false);
                        String preamble = verifyPublicStorage();
                        String outpath = preamble + File.separator + stripperNoSpace(playlistName) + File.separator + "media";
                        new File(outpath).mkdirs();
                        initPlayListView();
                    }
                }
            });

            builerAlert.show().getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.dark_w8));

        } else if (playlistTrackView) {

            // Adding resources; toggles button overlay

            if (buttonsExpanded){
                closeExpansion();
            } else {
                addMediaFileB.setVisibility(View.VISIBLE);
                addYoutubeAudioB.setVisibility(View.VISIBLE);
                addMediaFolderB.setVisibility(View.VISIBLE);
                exportPlaylistB.setVisibility(View.VISIBLE);
                buttonsExpanded = true;
            }
        }
    }

    // region Local Raw Resource

    public void addResourceLocal(View view){
        closeExpansion();
        Intent pickFile = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
        filePickResultLauncher.launch(pickFile);
    }

    ActivityResultLauncher<Intent> filePickResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Uri data = result.getData().getData();
                    processLocalRaw(data);
                }
            }
    );

    public void processLocalRaw(Uri uri) {
        int code = pli.addTrack(uri);
        if (code == 1){
            Toast.makeText(getApplicationContext(), "FileType not supported.", Toast.LENGTH_SHORT).show();
        } else if (code == 0){
            openPlaylist(stripperNoSpace(pli.getBrowsingPlaylist()));
        } else if (code == 2){
            Toast.makeText(getApplicationContext(), "Track is already in playlist!", Toast.LENGTH_SHORT).show();
        }
    }

    // endregion Local Raw Resource

    // region Network Resource

    public void addResourceYT(View view){
        closeExpansion();

        // Input Dialog for URL
        AlertDialog.Builder builerAlertY = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogTheme)).setTitle("Uniform Resource Locator");
        EditText urlField = new EditText(this);

        // Formatting Dialog Style
        urlField.setLinkTextColor(getResources().getColor(R.color.dark_w8));
        urlField.setTextColor(getResources().getColor(R.color.dark_w8));
        urlField.setBackground(null);
        urlField.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        builerAlertY.setView(urlField);
        builerAlertY.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                String url = urlField.getText().toString();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ytDown(pli.getBrowsingPlaylist(), url);
                    }
                }).start();
            }
        });

        builerAlertY.show().getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.dark_w8));

    }

    public void ytDown(String playlistName, String link){
        try {

            String preamble = verifyPublicStorage();
            String outpath = preamble + File.separator + playlistName + File.separator + "media" + File.separator;
            YoutubeDL.getInstance().init(getApplication());
            FFmpeg.getInstance().init(getApplication());
            YoutubeDLRequest yr = new YoutubeDLRequest(link);
            yr.addOption("-o", new File(outpath).getAbsolutePath() + "/%(title)s.%(ext)s");
            yr.addOption("-f", "bestaudio[ext=m4a]");
            yr.addOption("--add-metadata", "true");
            YoutubeDL.getInstance().execute(yr, ((progress, etaInSeconds) -> {
                //a.setMessage(progress + "% (ETA " + etaInSeconds + " seconds)");
            }));

        } catch (YoutubeDLException e) {
            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Log.d(TAG, "ytDown: " + e.getMessage());
                    //Toast.makeText(getApplicationContext(), "YoutubeDL Error Occurred! " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } catch (InterruptedException e) {
                Toast.makeText(getApplicationContext(), "YoutubeDL interrupted.", Toast.LENGTH_SHORT).show();
            }
            Log.d(TAG, "ytDown: YoutubeDL downloaded one track.");
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Download Successful", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // endregion Network Resource

    // region Local Directory Resource

    public void addResourceFromDir(View view){
        closeExpansion();
        Intent pickFolder = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPickResultLauncher.launch(pickFolder);
    }

    ActivityResultLauncher<Intent> folderPickResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Uri data = result.getData().getData();
                    processLocalDirectory(data);
                }
            }
    );

    public void processLocalDirectory(Uri uri){
        int code = pli.addTracks(uri);
        if (code == 1){
            Toast.makeText(getApplicationContext(), "Please select a directory.", Toast.LENGTH_SHORT).show();
        } else if (code > 1){
            initPlayListTrackView(pli.getBrowsingPlaylist());
            Toast.makeText(getApplicationContext(), "Found " + (code - 2) + " tracks!", Toast.LENGTH_SHORT).show();
        }
    }

    // endregion Local Directory Resource

    // endregion Data Collections

    // region Recycler Adaptor

    // Manages updates to RecyclerView for different views
    public void raptor(String[] title, String[] artist, String[] meta, String[] loc){
        RecyclerView rv = findViewById(R.id.recyclerView);
        RecyclerAdapter ra = new RecyclerAdapter(getApplicationContext(), title, artist, meta, loc);
        rv.setAdapter(ra);
        rv.setLayoutManager(new LinearLayoutManager(this));
    }

    // Editable Playlist Tracks Recycler View
    public void eraptor(String playlistName, String[] title, String[] artist, String[] meta, String[] loc, String[] playcounts){
        RecyclerView rv = findViewById(R.id.recyclerView);
        era = new ERecyclerAdapter(getApplicationContext(), playlistName, title, artist, meta, loc, playcounts);
        era.attachIt(rv);
        rv.setAdapter(era);
        rv.setLayoutManager(new LinearLayoutManager(this));
    }

    // Editable Playlist Recycler View
    public void epraptor(String[] playlists){
        RecyclerView rv = findViewById(R.id.recyclerView);
        erap = new EPRecyclerAdapter(getApplicationContext(), playlists);
        erap.attachIt(rv);
        rv.setAdapter(erap);
        rv.setLayoutManager(new LinearLayoutManager(this));
    }

    public BroadcastReceiver raptorComm = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra("title");
            Log.d(TAG, "onReceive: RECEIVED *"+ intent.getStringExtra("position") + "* over here");
            int position = Integer.parseInt(intent.getStringExtra("position"));
            if (playlistView){
                openPlaylist(stripperNoSpace(title));
            } else if (playlistTrackView){
                pli.getSetPlaylist(stripper(pli.getBrowsingPlaylist()));
                setTrack(position);
                Log.d(TAG, "onReceive: POSITION " + position);
            }
        }
    };

    //endregion Recycler Adaptor

    // region General Communicators

    public BroadcastReceiver mediaComm = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Packet received on track completion

            // Stamp and Date for record
            Long stamp = intent.getLongExtra("stamp", 0);
            Date stampDate = new Date(stamp*1000);
            DateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
            DateFormat monthFormat = new SimpleDateFormat("MM", Locale.getDefault());

            // Track Meta
            String[] trackMeta = pli.getCurrentTrack();
            pli.updateCount();
            String trackPlaylistRecord = pli.getCurrentPlaylist();
            String countToRecord = pli.getCount();

            // Updating to next track in playlist
            if (intent.getBooleanExtra("next", false)){
                    String[] newTrackMeta = pli.getNextTrack();
                    if (!statView){
                        updateTrackView(newTrackMeta);
                    }
                    ms.setResource(Uri.parse(newTrackMeta[2]));
            }

            // Verifying storage integrity and updating
            if (stamp != 0){
                File appDirs = new File(getObbDir() + File.separator + "recorder");
                String parentContruction = getObbDir() + File.separator + "recorder" + File.separator + trackPlaylistRecord;
                String pathContruction = parentContruction + File.separator + stripper(trackMeta[0] + "x" + trackMeta[1]);
                File recordFile = new File(pathContruction);
                File recordDir = new File(parentContruction);
                if (!(appDirs.isDirectory())){
                    if (appDirs.mkdir()){
                        Log.d(TAG, "onReceive: MEDIA COMM CREATED DIRECTORY");
                    }
                }
                if (!recordDir.isDirectory()){
                    recordDir.mkdir();
                    Log.d(TAG, "onReceive: Created RECORD directory");
                }
                if (!recordFile.exists()){
                    dbs.createXml(pathContruction, false, true);
                }
                dbs.writeXml(pathContruction, true, countToRecord, stamp + "", monthFormat.format(stampDate), yearFormat.format(stampDate));
            }
        }
    };

    // endregion General Communicators

    // region Data Deposits

    public void openPlaylist(String playListName){
        pli.setBrowsingPlaylist(stripperNoSpace(playListName));
//        String[][] xmlContent = pli.getBrowsingPlaylistTracks();
//        raptor(xmlContent[0], xmlContent[1], getBlank(xmlContent[0].length), xmlContent[2]);
//        findViewById(R.id.createButton).setVisibility(View.VISIBLE);
        initPlayListTrackView(stripperNoSpace(playListName));
        updateView("playlisttrackview");
    }

    public void setTrack(int position){

        if (pli != null && pli.getPosition() == position){
            // continues if requested track is already selected
            initTrackView();
            String[] trackMeta = pli.getCurrentTrack();
            updateTrackView(trackMeta);
        } else {
            initTrackView();
            pli.setPosition(position);
            String[] trackMeta= pli.getCurrentTrack();
            if (new File(trackMeta[2]).exists()){
                ms.setResource(Uri.parse(trackMeta[2]));
                updateTrackView(trackMeta);
                findViewById(R.id.playpause).callOnClick();
            } else {
                Log.d(TAG, "next: RESOURCE NOT FOUND AT EXPECTED POSITION " + position);
                Toast.makeText(getApplicationContext(), "Track " + trackMeta[0] + " was modified!", Toast.LENGTH_SHORT).show();
                setTrack(position + 1);
            }
        }

    }

    public void updateTrackView(String[] trackMeta){
        if (trackView){
            //@@@FLAG improve efficiency
            ( (TextView) findViewById(R.id.trackTitle) ).setText(trackMeta[0]);
            ( (TextView) findViewById(R.id.trackTitle) ).setSelected(true);
            ( (TextView) findViewById(R.id.trackArtist) ).setText(trackMeta[1]);
            Bitmap bm = new MetaFace(pli.getCurrentTrack()[2]).getArt();
            if (bm!=null){
                ImageView image = (ImageView) findViewById(R.id.albumArt);
                image.setImageBitmap(Bitmap.createScaledBitmap(bm, image.getWidth(), image.getHeight(), false));
            }
            initSeeker();
            seekerFin();
        }
    }

    // endregion Data Deposits

    // region Helper Functions

    // Returns empty String array initialized to ""
    public String[] getBlank(int length){
        String[] blank = new String[length];
        for (int x = 0; x < blank.length; x++){
            blank[x] = "";
        }
        return blank;
    }

    // removes special characters; negative lookup alphanumeric and whitespace [regex]
    public String stripper(String unstrip){
        Pattern pattern = Pattern.compile("[^a-zA-Z0-9]+");
        String stripped = (pattern.matcher(unstrip).replaceAll("")).trim();
        return stripped;
    }
    public String stripperNoSpace(String unstrip){
        Pattern pattern = Pattern.compile("[^a-zA-Z0-9]+");
        String stripped = (pattern.matcher(unstrip).replaceAll("")).trim();
        return stripped;
    }

    // verifies / creates public storage directories
    public String verifyPublicStorage(){
        File appDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "rhythmdrip");
        if (!appDir.isDirectory()){
            appDir.mkdir();
            Log.d(TAG, "verifyPublicStorage: Public Storage Created");
        }
        Log.d(TAG, "verifyPublicStorage: Public Storage Exists");
        return appDir.getPath();
    }

    // endregion Helper Functions

}