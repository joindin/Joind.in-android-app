package in.joind;


import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.util.ArrayList;

class JITalkCommentAdapter extends ArrayAdapter<JSONObject> {
    private ArrayList<JSONObject> items;
    private Context context;

    public JITalkCommentAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
        super(context, textViewResourceId, items);
        this.context = context;
        this.items = items;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.commentrow, parent, false);
            holder = new ViewHolder();

            holder.gravatarImage = (ImageView) convertView.findViewById(R.id.CommentRowGravatar);
            holder.gravatarImage.setTag("");
            holder.commentText = (TextView) convertView.findViewById(R.id.CommentRowComment);
            holder.usernameText = (TextView) convertView.findViewById(R.id.CommentRowUName);
            holder.dateText = (TextView) convertView.findViewById(R.id.CommentRowDate);
            holder.ratingImage = (ImageView) convertView.findViewById(R.id.CommentRowRate);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        JSONObject o = items.get(position);
        if (o == null) {
            return convertView;
        }

        holder.gravatarImage.setVisibility(View.GONE);
        String gravatarHash = o.optString("gravatar_hash");
        if (gravatarHash != null && gravatarHash.length() > 0) {
            holder.gravatarImage.setTag(gravatarHash);
            Picasso picasso = new Picasso.Builder(context)
                    .listener(new Picasso.Listener() {
                        @Override
                        public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception) {
                        android.util.Log.d("JOINDIN", "Failed to load image: " + uri.toString());
                        }
                    })
                    .build();
            String url = "https://www.gravatar.com/avatar/" + gravatarHash;
            picasso.load(url).resize(30,30).into(holder.gravatarImage);
            holder.gravatarImage.setVisibility(View.VISIBLE);
        }

        String commentDate = DateHelper.parseAndFormat(o.optString("created_date"), "d LLL yyyy");
        holder.commentText.setText(o.optString("comment"));
        holder.usernameText.setText(o.isNull("user_display_name") ? "(" + this.context.getString(R.string.generalAnonymous) + ") " : o.optString("user_display_name") + " ");
        holder.dateText.setText(commentDate);
        Linkify.addLinks(holder.commentText, Linkify.ALL);

        switch (o.optInt("rating")) {
            default:
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

        return convertView;
    }

    /**
     * Holder for each row
     */
    private class ViewHolder {
        ImageView gravatarImage;
        ImageView ratingImage;
        TextView commentText;
        TextView usernameText;
        TextView dateText;
    }
}
