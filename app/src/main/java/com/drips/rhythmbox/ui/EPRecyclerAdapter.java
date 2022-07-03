package com.drips.rhythmbox.ui;

//region Imports
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.drips.rhythmbox.R;

import java.util.ArrayList;
import java.util.Arrays;
//endregion Imports

public class EPRecyclerAdapter extends RecyclerView.Adapter<EPRecyclerAdapter.Radaptor> {

    // RECYCLER ADAPTOR --- Dynamic Array Mutable Presentation

    // Identifier in logcat
    public final String TAG = "ERAL - ERAPTOR LOG";

    // region Fields
    private Context appContext;
    private String[] playlists;
    private String[] previousPlaylists;
    private ArrayList<String> deadPlaylists = new ArrayList<String>();
    // endregion Fields

    // region Position and Presence modifications

    ItemTouchHelper.SimpleCallback sc = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END, 0){

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            int draggerFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            int swiperFlags = ItemTouchHelper.START | ItemTouchHelper.END;
            return makeMovementFlags(draggerFlags, swiperFlags);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            RecyclerView.Adapter adapter = recyclerView.getAdapter();
            int originalPos = viewHolder.getAdapterPosition();
            int targetPos = target.getAdapterPosition();
            updatePositions(originalPos, targetPos);
            adapter.notifyItemMoved(originalPos, targetPos);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int markDead = viewHolder.getAdapterPosition();
            deadPlaylists.add(previousPlaylists[markDead]);
            playlists = removeIndex(markDead, playlists);
            previousPlaylists = removeIndex(markDead, previousPlaylists);
            notifyDataSetChanged();
        }
    };
    ItemTouchHelper it = new ItemTouchHelper(sc);

    // endregion Position and Presence Modification

    // Constructor
    public EPRecyclerAdapter(Context c, String[] p){
        appContext = c;
        playlists = p;
        previousPlaylists = p.clone();
    }

    // UI Mapping
    public class Radaptor extends RecyclerView.ViewHolder{
        LinearLayout elementView;
        TextView playlistName;
        // TextView parenthesisView;
        ImageView albumArt;
        public Radaptor(@NonNull View itemView) {
            super(itemView);
            elementView = itemView.findViewById(R.id.element);
            playlistName = itemView.findViewById(R.id.titleView);
            albumArt = itemView.findViewById(R.id.elementalAlbumArt);
        }
    }

    // Interface creation
    @NonNull
    @Override
    public Radaptor onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(appContext);
        View inflated_view = inflater.inflate(R.layout.elemental_editable_view, parent, false);
        return new Radaptor(inflated_view);
    }

    // Inflation specifications and data mapping
    @Override
    public void onBindViewHolder(@NonNull Radaptor holder, int position) {
        holder.playlistName.setText(playlists[position]);
        holder.playlistName.setTag(position);
        holder.albumArt.setClipToOutline(true);

        // title updater
        holder.playlistName.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {
                playlists[holder.getAdapterPosition()] = holder.playlistName.getText().toString();
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

        });

    }

    // Helper function
    @Override
    public int getItemCount() {
        return playlists.length;
    }

    // region Accessors and Mutators [Communicators]
    public void attachIt(RecyclerView rv){
        // attaches recycler view when required
        // @@@FLAG possible breakpoint; crucial component
        it.attachToRecyclerView(rv);
    }
    public String[][] getUpdatedPlaylists(){
        return new String[][] {playlists, previousPlaylists};
    }
    public ArrayList<String> getDeletedPlaylists(){
        return deadPlaylists;
    }
    // endregion Accessors and Mutators [Communicators]

    // region Local Modifiers
    // @@@FLAG possible efficiency improvements
    public String[] removeIndex(int index, String[] arr){
        ArrayList<String> arrL = new ArrayList<>(Arrays.asList(arr));
        Log.d(TAG, "removeIndex: " + index + " of " + arrL.size());
        arrL.remove(index);
        return arrL.toArray(new String[arrL.size()]);
    }
    public void updatePositions(int originP, int destP){
        String[][] mutator = {playlists, previousPlaylists};
        for (int i = 0; i < mutator.length; i++){
            String temp = mutator[i][destP];
            mutator[i][destP] = mutator[i][originP];
            mutator[i][originP] = temp;
        }
    }
    // endregion Local Modifiers

}
