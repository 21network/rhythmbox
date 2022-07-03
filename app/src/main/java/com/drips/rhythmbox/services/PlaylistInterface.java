package com.drips.rhythmbox.services;

// region Imports
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.drips.rhythmbox.extutils.PathUtil;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
// endregion Imports

public class PlaylistInterface {

    // Playlist Interface --- Track Order Management

    // Identifier in logcat
    public static final String TAG = "PLI";

    // region Fields
    private DataStreamBus dbs;
    private Context appContext;

    private String currentPlaylist;
    private String browsingPlaylist;
    private String[] titles;
    private String[] artists;
    private String[] locations;

    private int position;
    // endregion Fields

    // Initializer; sets context and connects data manager
    public PlaylistInterface(Context context, DataStreamBus dbs){
        this.appContext = context;
        this.dbs = dbs;
    }

    // region Playlist Navigators
    public String[] getNextTrack(){
        if (position + 1 >= titles.length){
            position = 0;
        } else {
            position += 1;
        }
        String[] meta = { titles[position], artists[position], locations[position] };
        return meta;
    }
    public String[] getCurrentTrack(){
        String [] meta = { titles[position], artists[position], locations[position]};
        return meta;
    }
    public String[] getPreviousTrack(){
        if ((position -1) < 0){
            position = titles.length - 1;
        } else {
            position -= 1;
        }
        String [] meta = { titles[position], artists[position], locations[position]};
        return meta;
    }
    // endregion Playlist Navigators

    // region Accessors and Mutators [Communicators]
    public int getPosition(){
        return position;
    }
    public void setPosition(int newPosition){
        position = newPosition;
    }
    public String[][] getPlaylist(String playListName){
        String[][] xmlContent = dbs.readXml(playListName);
        return xmlContent;
    }
    public String[][] getSetPlaylist(String playListName){
        String[][] xmlContent = dbs.readXml(playListName);
        currentPlaylist = playListName;
        titles = xmlContent[0];
        artists = xmlContent[1];
        locations = xmlContent[2];
        return xmlContent;
    }
    public String getCurrentPlaylist(){
        return currentPlaylist;
    }
    public String getCount(){
        int count = dbs.getTrackPlayCount(currentPlaylist, titles[position]);
        return "" + count;
    }
    public String getBrowsingPlaylist(){
        if (browsingPlaylist != null){
            return browsingPlaylist;
        } else {
            return currentPlaylist;
        }
    }
    public void setBrowsingPlaylist(String bp){
        browsingPlaylist = bp;
    }
    public String[][] getBrowsingPlaylistTracks(){
        return getPlaylist(browsingPlaylist);
    }
    // endregion Accessors and Mutators [Communicators]

    // region Creators and Destroyers
    // @@@FLAG source of extension filter
    // determine exoplayer acceptable formats and update filter
    // potentially renders getMime useless
    public int addTrack(Uri uri){
        // Not in data manager due to required access of all track locations
        // @@@FLAG future normalization
        // implement method in DSB to handle array list
        Log.d(TAG, "addTrack: MIMEFETCH *" + getMime(uri) + "*");
        if (!getMime(uri).matches("mp3|mp4|m4a|webm")){
            // file not mp3
            return 1;
        } else {
            Log.d(TAG, "addTrack: bp"+ browsingPlaylist);
            Log.d(TAG, "addTrack: bpl"+ getPlaylist(browsingPlaylist)[2]);
            if (!Arrays.asList(getPlaylist(browsingPlaylist)[2]).contains(PathUtil.getPath(appContext, uri))){
                Log.d(TAG, "addTrack: " + PathUtil.getPath(appContext, uri));
                String path = PathUtil.getPath(appContext, uri);
                String fileName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                dbs.writeXml(browsingPlaylist, false, fileName, "", PathUtil.getPath(appContext, uri), "0");
                return 0;
            } else {
                return 2;
                // file already exists
            }
        }
    }
    public int addTracks(Uri uri){
        // looped caller of addTrack; uri of directory
        // @@@FLAG source of extension filter
        // copy identifiers from previous function
        int tracksAdded = 2;
        File trackDir = new File(PathUtil.getPath(appContext, DocumentFile.fromTreeUri(appContext, uri).getUri()));
        if (trackDir.isDirectory()){
            File[] tracks = trackDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return (file.getPath().endsWith(".mp3") | file.getPath().endsWith(".mp4") | file.getPath().endsWith(".m4a") | file.getPath().endsWith(".webm"));
                }
            });
            for (int i = 0; i < tracks.length; i++){
                int code = addTrack(Uri.fromFile(tracks[i]));
                if (code == 0){ tracksAdded++; }
            }
            return tracksAdded;
        } else {
            return 1;
        }
    }
    public void updateCount(){
        dbs.updatePlayCount(currentPlaylist, titles[position]);
    }
    // endregion Creators and Destroyers

    // region Helpers
    public String getMime(Uri uri) {
        String ext;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            ext = mime.getExtensionFromMimeType(appContext.getContentResolver().getType(uri));
        } else {
            ext = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString());
        }
        return ext;
    }
    // endregion Helpers

}
