package in.joind.adapter;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;

import in.joind.helper.DateHelper;
import in.joind.helper.ImageLoader;
import in.joind.R;

public class EventCommentAdapter extends ArrayAdapter<JSONObject> {
    private ArrayList<JSONObject> items;
    private Context context;
    private ImageLoader image_loader;

    public EventCommentAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
        super(context, textViewResourceId, items);
        this.context = context;
        this.items = items;

        this.image_loader = new ImageLoader(context.getApplicationContext(), "gravatars");
    }

    @NonNull
    public View getView(int position, View convertview, @NonNull ViewGroup parent) {
        View v = convertview;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.commentrow, parent, false);
        }

        JSONObject o = items.get(position);
        if (o == null) return v;

        ImageView el = (ImageView) v.findViewById(R.id.CommentRowGravatar);
        el.setTag("");
        el.setVisibility(View.GONE);

        if (o.optInt("user_id") > 0) {
            String filename = "user" + o.optString("user_id") + ".jpg";
            el.setTag(filename);
            image_loader.displayImage("http://joind.in/inc/img/user_gravatar/", filename, (Activity) context, el);
        }

        String commentDate = DateHelper.parseAndFormat(o.optString("created_date"), "d LLL yyyy");
        TextView t1 = (TextView) v.findViewById(R.id.CommentRowComment);
        TextView t2 = (TextView) v.findViewById(R.id.CommentRowUName);
        TextView t3 = (TextView) v.findViewById(R.id.CommentRowDate);
        if (t1 != null) t1.setText(o.optString("comment"));
        if (t2 != null) t2.setText(o.isNull("user_display_name") ? "(" + this.context.getString(R.string.generalAnonymous) + ") " : o.optString("user_display_name") + " ");
        if (t3 != null) t3.setText(commentDate);

        ImageView r = (ImageView) v.findViewById(R.id.CommentRowRate);
        r.setVisibility(View.GONE);

        return v;
    }
}
