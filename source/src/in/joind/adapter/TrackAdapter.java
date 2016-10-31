package in.joind.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;

import in.joind.R;

public class TrackAdapter extends ArrayAdapter<JSONObject> {
    private ArrayList<JSONObject> items;
    private Context context;

    public TrackAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> mTracks) {
        super(context, textViewResourceId, mTracks);
        this.context = context;
        this.items = mTracks;
    }

    @NonNull
    public View getView(int position, View convertview, @NonNull ViewGroup parent) {
        View v = convertview;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.trackrow, parent, false);
        }

        JSONObject o = items.get(position);
        if (o == null) return v;

        String t2Text;
        int talkCount = o.optInt("talks_count");
        if (talkCount == 1) {
            t2Text = String.format(this.context.getString(R.string.generalEventTalksSingular), talkCount);
        } else {
            t2Text = String.format(this.context.getString(R.string.generalEventTalksPlural), talkCount);
        }

        TextView t1 = (TextView) v.findViewById(R.id.TrackRowCaption);
        TextView t2 = (TextView) v.findViewById(R.id.TrackRowTalkCount);
        TextView t3 = (TextView) v.findViewById(R.id.TrackRowDescription);
        if (t1 != null) t1.setText(o.optString("track_name"));
        if (t2 != null) t2.setText(t2Text);
        if (t3 != null) t3.setText(o.optString("track_desc"));

        return v;
    }
}
