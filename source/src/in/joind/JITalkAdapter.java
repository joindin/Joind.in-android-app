package in.joind;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SectionIndexer;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

class JITalkAdapter extends ArrayAdapter<JSONObject> implements Filterable {
    private ArrayList<JSONObject> items, filtered_items;
    private Context context;
    private TimeZone tz;
    private StarredFilter filter;
    private boolean isAuthenticated;

    public JITalkAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> mTalks, TimeZone tz, boolean isAuthenticated) {
        super(context, textViewResourceId, mTalks);
        this.context = context;
        this.items = mTalks;
        this.filtered_items = mTalks;
        this.tz = tz;
        this.isAuthenticated = isAuthenticated;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.talkrow, null);
            holder = new ViewHolder();

            holder.ratingImage = (ImageView) convertView.findViewById(R.id.TalkRowRating);
            holder.starredCheckBox = (CheckBox) convertView.findViewById(R.id.TalkRowStarred);
            holder.commentsText = (TextView) convertView.findViewById(R.id.TalkRowComments);
            holder.captionText = (TextView) convertView.findViewById(R.id.TalkRowCaption);
            holder.speakerText = (TextView) convertView.findViewById(R.id.TalkRowSpeaker);
            holder.timeText = (TextView) convertView.findViewById(R.id.TalkRowTime);
            holder.trackText = (TextView) convertView.findViewById(R.id.TalkRowTrack);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final JSONObject o = filtered_items.get(position);
        if (o == null) {
            return convertView;
        }

        // Convert the supplied date/time string into something we can use
        Date talkDate = null;
        SimpleDateFormat outputTalkDateFormat = null;
        try {
            SimpleDateFormat inputTalkDateFormat = new SimpleDateFormat(context.getString(R.string.apiDateFormat));
            talkDate = inputTalkDateFormat.parse(o.getString("start_date"));
            String fmt = Build.VERSION.SDK_INT <= 8 ? "E d MMM yyyy" : "E d LLL yyyy";
            outputTalkDateFormat = new SimpleDateFormat(fmt + ", HH:mm");
            outputTalkDateFormat.setTimeZone(tz);
        } catch (Exception e) {
            e.printStackTrace();
            // Nothing here. Date is probably formatted badly
        }
        long cts = System.currentTimeMillis() / 1000;

        // Set a bit of darker color when the talk is currently held (the date_given is less than an hour old)
        if (talkDate != null && cts - talkDate.getTime() <= 3600 && cts - talkDate.getTime() >= 0) {
            convertView.setBackgroundColor(Color.rgb(218, 218, 204));
        } else {
            // This isn't right. We shouldn't set a white color, but the default color
            convertView.setBackgroundColor(Color.rgb(255, 255, 255));
        }

        String t2Text;
        int commentCount = o.optInt("comment_count");
        if (commentCount == 1) {
            t2Text = String.format(this.context.getString(R.string.generalCommentSingular), commentCount);
        } else {
            t2Text = String.format(this.context.getString(R.string.generalCommentPlural), commentCount);
        }

        String track = "";
        try {
            track = o.optJSONArray("tracks").getJSONObject(0).optString("track_name");
        } catch (JSONException e) {
            // Ignore if no track is available
        }

        holder.captionText.setText(o.optString("talk_title"));
        holder.commentsText.setText(t2Text);
        holder.timeText.setText(outputTalkDateFormat.format(talkDate));
        holder.trackText.setText(track);

        // Speaker details
        ArrayList<String> speakerNames = new ArrayList<String>();
        try {
            JSONArray speakerEntries = o.getJSONArray("speakers");
            for (int i = 0; i < speakerEntries.length(); i++) {
                speakerNames.add(speakerEntries.getJSONObject(i).getString("speaker_name"));
            }
        } catch (JSONException e) {
            Log.d(JIActivity.LOG_JOINDIN_APP, "Couldn't get speaker names");
            e.printStackTrace();
        }
        if (speakerNames.size() == 1) {
            holder.speakerText.setText("Speaker: " + speakerNames.get(0));
        } else if (speakerNames.size() > 1) {
            String allSpeakers = TextUtils.join(", ", speakerNames);
            holder.speakerText.setText("Speakers: " + allSpeakers);
        } else {
            holder.speakerText.setText("");
        }

        // Set specified talk category image
        Resources resources = context.getResources();
        if (o.optString("type").compareTo("Talk") == 0) {
            holder.captionText.setCompoundDrawables(resources.getDrawable(R.drawable.talk), null, null, null);
        }
        if (o.optString("type").compareTo("Social Event") == 0) {
            holder.captionText.setCompoundDrawables(resources.getDrawable(R.drawable.socialevent), null, null, null);
        }
        if (o.optString("type").compareTo("Workshop") == 0) {
            holder.captionText.setCompoundDrawables(resources.getDrawable(R.drawable.workshop), null, null, null);
        }
        if (o.optString("type").compareTo("Keynote") == 0) {
            holder.captionText.setCompoundDrawables(resources.getDrawable(R.drawable.keynote), null, null, null);
        }

        int rate = o.optInt("average_rating", 0);
        switch (rate) {
            case 0:
                holder.ratingImage.setBackgroundResource(R.drawable.rating_0);
                break;
            case 1:
                holder.ratingImage.setBackgroundResource(R.drawable.rating_1);
                break;
            case 2:
                holder.ratingImage.setBackgroundResource(R.drawable.rating_2);
                break;
            case 3:
                holder.ratingImage.setBackgroundResource(R.drawable.rating_3);
                break;
            case 4:
                holder.ratingImage.setBackgroundResource(R.drawable.rating_4);
                break;
            case 5:
                holder.ratingImage.setBackgroundResource(R.drawable.rating_5);
                break;
        }

        // Show/hide the starred icon if the talk is starred
        boolean starredStatus = o.optBoolean("starred", false);
        holder.starredCheckBox.setVisibility(isAuthenticated ? View.VISIBLE : View.GONE);
        holder.starredCheckBox.setChecked(starredStatus);
        final View finalV = convertView;
        holder.starredCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                markTalkStarred(finalV, o.optString("starred_uri"), ((CheckBox) view).isChecked());
            }
        });

        return convertView;
    }

    /**
     * Mark the talk as starred - update the icon and submit the request
     *
     * @param isStarred
     */
    protected void markTalkStarred(final View parentRow, final String starredURI, final boolean isStarred) {
        final CheckBox starredImageButton = (CheckBox) parentRow.findViewById(R.id.TalkRowStarred);
        final ProgressBar progressBar = (ProgressBar) parentRow.findViewById(R.id.TalkRowProgress);

        new Thread() {
            public void run() {
                updateProgressStatus(progressBar, starredImageButton, true);

                final String result = doStarTalk(isStarred, starredURI);

                updateProgressStatus(progressBar, starredImageButton, false);
            }
        }.start();
    }

    /**
     * CALLED FROM SEPARATE THREAD
     * This shows/hides the progressbar and the checkbox alternately.
     *
     * @param progressBar
     * @param starredImageButton
     * @param showProgress
     */
    private void updateProgressStatus(final ProgressBar progressBar, final CheckBox starredImageButton, final boolean showProgress) {
        progressBar.post(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            }
        });
        starredImageButton.post(new Runnable() {
            @Override
            public void run() {
                starredImageButton.setVisibility(showProgress ? View.GONE : View.VISIBLE);
            }
        });
    }

    /**
     * Post/delete the starred status from this talk
     *
     * @param initialState
     * @return
     */
    private String doStarTalk(boolean initialState, String starredURI) {
        JIRest rest = new JIRest(context);
        int error = rest.requestToFullURI(starredURI, null, initialState ? JIRest.METHOD_POST : JIRest.METHOD_DELETE);

        if (error != JIRest.OK) {
            return String.format(context.getString(R.string.generalStarringError), rest.getError());
        }

        // Everything went as expected
        if (initialState) {
            return context.getString(R.string.generalSuccessStarred);
        } else {
            return context.getString(R.string.generalSuccessUnstarred);
        }
    }

    public Filter getFilter() {
        if (filter == null) {
            filter = new StarredFilter();
        }
        return filter;
    }

    public int getCount() {
        return filtered_items.size();
    }

    public JSONObject getItem(int position) {
        return filtered_items.get(position);
    }

    /**
     * Starred filter
     */
    public class StarredFilter extends Filter {
        private boolean checkStarredStatus = false;

        public void setCheckStarredStatus(boolean checkStarredStatus) {
            this.checkStarredStatus = checkStarredStatus;
        }

        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence prefix, FilterResults results) {
            filtered_items = (ArrayList<JSONObject>) results.values;

            notifyDataSetChanged();
        }

        protected FilterResults performFiltering(CharSequence match) {
            FilterResults results = new FilterResults();
            ArrayList<JSONObject> i = new ArrayList<JSONObject>();

            // Multiple filters here
            if (checkStarredStatus || (match != null && match.length() > 0)) {
                for (int index = 0; index < items.size(); index++) {
                    JSONObject json = items.get(index);
                    JSONArray tracks = json.optJSONArray("tracks");
                    if (tracks.length() == 0) {
                        continue;
                    }

                    int tracksLength = tracks.length();
                    for (int j = 0; j < tracksLength; j++) {
                        JSONObject track = tracks.optJSONObject(j);
                        if (track == null) {
                            continue;
                        }

                        // Add to the filtered result list when the match is present in the URI
                        // If we need to check starred status as well, let's do that too
                        if (match.toString().length() > 0) {
                            // We have a track to match against, this happens first
                            if (track.optString("track_uri").equals(match.toString())) {
                                if ((checkStarredStatus && json.optBoolean("starred")) || !checkStarredStatus) {
                                    i.add(json);
                                    break;
                                }
                            }
                        } else if (checkStarredStatus && json.optBoolean("starred")) {
                            i.add(json);
                            break;
                        }
                    }
                }
                results.values = i;
                results.count = i.size();
            } else {
                synchronized (items) {
                    results.values = items;
                    results.count = items.size();
                }
            }

            return results;
        }
    }

    /**
     * Holder for each row
     */
    private class ViewHolder {
        ImageView ratingImage;
        TextView captionText;
        TextView commentsText;
        TextView speakerText;
        TextView timeText;
        TextView trackText;
        CheckBox starredCheckBox;
    }
}
