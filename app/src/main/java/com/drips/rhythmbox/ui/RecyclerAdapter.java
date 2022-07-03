package com.drips.rhythmbox.ui;

//region Imports
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.drips.rhythmbox.R;
//endregion Imports

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.Radaptor> {

    // RECYCLER ADAPTOR --- Dynamic Array Presentation

    // Identifier in logcat
    public final String TAG = "RAL - RAPTOR LOG";

    // region Fields
    private Context appContext;
    private String[] titles, artists, parenthesis, locs;
    // endregion Fields

    // Constructor
    public RecyclerAdapter(Context c, String[] t, String[] a, String[] p, String[] l){
        appContext = c;
        titles = t;
        artists = a;
        parenthesis = p;
        locs = l;
    }

    // UI Mapping
    public class Radaptor extends RecyclerView.ViewHolder{
        LinearLayout elementView;
        TextView titleView;
        TextView artistView;
        TextView parenthesisView;
        ImageView albumArt;
        public Radaptor(@NonNull View itemView) {
            super(itemView);
            elementView = itemView.findViewById(R.id.element);
            titleView = itemView.findViewById(R.id.titleView);
            artistView = itemView.findViewById(R.id.artistView);
            parenthesisView = itemView.findViewById(R.id.textViewParenthesis);
            albumArt = itemView.findViewById(R.id.elementalAlbumArt);
        }
    }

    // Interface creation
    @NonNull
    @Override
    public Radaptor onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(appContext);
        View inflated_view = inflater.inflate(R.layout.elemental_view, parent, false);
        return new Radaptor(inflated_view);
    }

    // Inflation specifications and data mapping
    @Override
    public void onBindViewHolder(@NonNull Radaptor holder, int position) {
        holder.elementView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent raptorIntention = new Intent("raptor_comm");
                raptorIntention.putExtra("title", holder.titleView.getText());
                raptorIntention.putExtra("position", holder.getAdapterPosition() + "");
                LocalBroadcastManager.getInstance(appContext).sendBroadcast(raptorIntention);
            }
        });
        holder.titleView.setText(titles[position]);
        holder.titleView.setTag(position);
        holder.artistView.setText(artists[position]);
        holder.parenthesisView.setText(parenthesis[position]);
        holder.albumArt.setClipToOutline(true);
    }

    // Helper function
    @Override
    public int getItemCount() {
        if (titles == null){
            return 0;
        }
        return titles.length;
    }

}
