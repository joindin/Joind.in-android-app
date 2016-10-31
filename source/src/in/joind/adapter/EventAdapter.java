package in.joind.adapter;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import in.joind.DateHelper;
import in.joind.R;

public class EventAdapter extends ArrayAdapter<JSONObject> {
    private final ArrayList<JSONObject> all_items;
    private ArrayList<JSONObject> filtered_items;
    private Context context;
    LayoutInflater inflater;
    private PTypeFilter filter;
    private Picasso picasso;

    public int getCount() {
        return filtered_items.size();
    }

    public JSONObject getItem(int position) {
        return filtered_items.get(position);
    }

    public EventAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
        super(context, textViewResourceId, items);
        this.context = context;
        this.all_items = items;
        this.filtered_items = items;
        this.inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.picasso = new Picasso.Builder(context)
                .listener(new Picasso.Listener() {
                    @Override
                    public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                        android.util.Log.d("JOINDIN", "Failed to load image: " + uri.toString());
                    }
                })
                .build();
    }

    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = this.inflater.inflate(R.layout.eventrow, parent, false);
        }

        // Get the (JSON) data we need
        JSONObject o = filtered_items.get(position);
        if (o == null) return convertView;

        // Display (or load in the background if needed) the event logo
        ImageView el = (ImageView) convertView.findViewById(R.id.EventDetailLogo);
        el.setTag("");
        el.setVisibility(View.VISIBLE);

        // Display (or load in the background if needed) the event logo
        JSONObject images = o.optJSONObject("images");
        if (images != null && images.length() > 0) {
            JSONObject smallImage = images.optJSONObject("small");
            String url = smallImage.optString("url");
            el.setTag(url);
            this.picasso.load(url).resize(70,70).into(el);
        } else {
            el.setImageResource(R.drawable.event_icon_none);
        }

        // Set a darker color when the event is currently running.
        long event_start = 0;
        long event_end = 0;
        try {
            event_start = new SimpleDateFormat(context.getString(R.string.apiDateFormat), Locale.US).parse(o.optString("start_date")).getTime();
            event_end = new SimpleDateFormat(context.getString(R.string.apiDateFormat), Locale.US).parse(o.optString("end_date")).getTime();
        } catch (ParseException e) {
            // do nothing
        }
        long cts = System.currentTimeMillis() / 1000;
        if (event_start <= cts && cts <= event_end) {
            convertView.setBackgroundColor(Color.rgb(218, 218, 204));
        } else {
            // This isn't right. We shouldn't set a white color, but the default color
            convertView.setBackgroundColor(Color.rgb(255, 255, 255));
        }

        // Find our textviews we need to fill
        TextView tt = (TextView) convertView.findViewById(R.id.EventDetailCaption);
        TextView bt = (TextView) convertView.findViewById(R.id.EventDetailDate);
        TextView at = (TextView) convertView.findViewById(R.id.EventDetailAttending);

        // When the user is attending this event, we display our "attending" image.
        ImageView im = (ImageView) convertView.findViewById(R.id.EventDetailAttendingImg);
        if (!o.optBoolean("attending")) {
            im.setVisibility(View.GONE);
        } else {
            im.setVisibility(View.VISIBLE);
        }

        // Set our texts
        if (at != null)
            at.setText(String.format(this.context.getString(R.string.activityMainAttending), o.optInt("attendee_count")));
        if (tt != null) tt.setText(o.optString("name"));
        if (bt != null) {
            // Display start date. Only display end date when it differs (ie: it's multiple day event)
            // Android 2.2 and below don't support the "L" pattern character
            String fmt = Build.VERSION.SDK_INT <= 8 ? "d MMM yyyy" : "d LLL yyyy";
            String d1 = DateHelper.parseAndFormat(o.optString("start_date"), fmt);
            String d2 = DateHelper.parseAndFormat(o.optString("end_date"), fmt);
            bt.setText(d1.equals(d2) ? d1 : d1 + " - " + d2);
        }

        return convertView;
    }


    public Filter getFilter() {
        if (filter == null) {
            filter = new PTypeFilter();
        }
        return filter;
    }

    private class PTypeFilter extends Filter {
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence prefix, FilterResults results) {
            filtered_items = (ArrayList<JSONObject>) results.values;
            notifyDataSetChanged();
        }

        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            ArrayList<JSONObject> i = new ArrayList<>();

            if (prefix != null && prefix.toString().length() > 0) {

                for (int index = 0; index < all_items.size(); index++) {
                    JSONObject json = all_items.get(index);
                    String title = json.optString("name");
                    // Add to the filtered result list when our string is found in the event_name
                    if (title.toUpperCase().contains(prefix.toString().toUpperCase()))
                        i.add(json);
                }
                results.values = i;
                results.count = i.size();
            } else {
                // No more filtering, display all items
                synchronized (all_items) {
                    results.values = all_items;
                    results.count = all_items.size();
                }
            }

            return results;
        }
    }
}
