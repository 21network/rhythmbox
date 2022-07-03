package com.drips.rhythmbox.services;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import java.io.File;

public class MetaFace {
    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
    String source;

    public MetaFace(String path){
        source = path;
    }

    public Bitmap getArt(){
        try {
            if (new File(source).exists()) {
                mmr.setDataSource(source);
                byte[] biteArray = mmr.getEmbeddedPicture();
                Bitmap bmp = BitmapFactory.decodeByteArray(biteArray, 0, biteArray.length);
                return bmp;
            } else {
                return null;
            }
        } catch (Exception e){
            return null;
        }
    }

}
