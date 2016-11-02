package in.joind.api;

/*
 * Data helper for the SQLite database.
 *
 * All data is stored in a database so we do not have to call the joind.in api
 * to display data. Instead of filtering out all JSON data, we just store some
 * standard data (event_id, talk_id) and for the rest we just store the actual
 * received JSON data. This is because we not going to seek or query data anyway.
 *
 * The helper functions here take care of everything, so no need to query anything
 * outside this class.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.ArrayAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import in.joind.activity.JIActivity;
import in.joind.adapter.EventAdapter;
import in.joind.adapter.EventCommentAdapter;
import in.joind.adapter.TalkCommentAdapter;
import in.joind.adapter.TalkAdapter;
import in.joind.adapter.TrackAdapter;


public final class DataHelper {
    private static DataHelper DHInstance = null;

    private static final String DATABASE_NAME = "joindin.db";
    private static final int DATABASE_VERSION = 14;  // Increasing this version number will result in automatic call to onUpgrade()

    public static final int ORDER_DATE_ASC = 1;
    public static final int ORDER_DATE_DESC = 2;
    public static final int ORDER_TITLE_ASC = 3;
    public static final int ORDER_TITLE_DESC = 4;

    private SQLiteDatabase db = null;

    private DataHelper(Context context) {
        OpenHelper openHelper = new OpenHelper(context);
        this.db = openHelper.getWritableDatabase();
    }

    public static DataHelper createInstance(Context context) {
        if (DHInstance == null) {
            reinitialise(context);
        }

        return DHInstance;
    }

    public static DataHelper getInstance() {
        if (DHInstance == null) return null;
        return DHInstance;
    }

    public static DataHelper getInstance(Context context) {
        return createInstance(context);
    }

    public static void reinitialise(Context context) {
        DHInstance = new DataHelper(context);
    }

    // Updates a event
    public long updateEvent(int eventRowID, JSONObject event) {
        ContentValues values = new ContentValues();
        values.put("json", event.toString());
        return db.update("events", values, "_rowid_=?", new String[]{Integer.toString(eventRowID)});
    }

    // insert a new event to specified event_type (hot, pending, past etc)
    public long insertEvent(String event_type, JSONObject event) {
        ContentValues values = new ContentValues();
        values.put("event_uri", event.optString("uri"));
        values.put("event_start", event.optInt("start_date"));
        values.put("event_title", event.optString("name"));
        values.put("json", event.toString());

        long eventID = getEventIDByURI(event.optString("uri"));
        if (eventID > 0) {
            db.delete("event_types", "event_id=? AND event_type=?", new String[]{String.valueOf(eventID), event_type});
        } else {
            eventID = db.insert("events", "", values);
        }

        // add event type
        values = new ContentValues();
        values.put("event_id", eventID);
        values.put("event_type", event_type);
        db.insert("event_types", "", values);

        return eventID;
    }

    // load event
    public JSONObject getEvent(int eventRowID) {
        Cursor c = this.db.rawQuery("SELECT json FROM events WHERE _rowid_ = " + eventRowID, null);
        JSONObject json;
        try {
            c.moveToFirst();
            json = new JSONObject(c.getString(0));
        } catch (Exception e) {
            json = new JSONObject();
        } finally {
            if (!c.isClosed()) c.close();
        }
        return json;
    }

    public long getEventIDByURI(String eventURI) {
        if (eventURI.length() == 0) {
            return 0;
        }
        long eventID;
        Cursor c = this.db.rawQuery("SELECT _rowid_ FROM events WHERE event_uri = ?", new String[]{eventURI});
        try {
            c.moveToFirst();
            eventID = c.getLong(0);
        } catch (Exception e) {
            eventID = 0;
        } finally {
            if (!c.isClosed()) c.close();
        }
        return eventID;
    }

    // Insert a new talk
    public long insertTalk(int eventRowID, JSONObject talk) {
        ContentValues values = new ContentValues();
        String uri = "";
        values.put("event_id", eventRowID);

        int track_id = -1;    // Defaul track id (none)

        try {
            uri = talk.getString("uri");
            if (talk.getJSONArray("tracks").length() > 0) {
                // There are tracks. This talk is inside the 1st track (could there be more tracks??)
                track_id = talk.getJSONArray("tracks").getJSONObject(0).optInt("ID");
            }
        } catch (JSONException e) {
            // Ignore
        }
        if (uri.length() == 0) {
            Log.d(JIActivity.LOG_JOINDIN_APP, "Talk URI is empty");
        }

        values.put("uri", uri);
        values.put("track_id", track_id);
        values.put("starred", talk.optBoolean("starred", false) ? 1 : 0);
        values.put("json", talk.toString());

        db.delete("talks", "uri=?", new String[]{uri});
        return db.insert("talks", "", values);
    }

    // Insert a new track
    public long insertTrack(int eventRowID, JSONObject track) {
        ContentValues values = new ContentValues();
        String uri = track.optString("uri");
        values.put("event_id", eventRowID);
        values.put("uri", uri);
        values.put("json", track.toString());

        db.delete("tracks", "uri=?", new String[]{uri});
        return db.insert("tracks", "", values);
    }

    // Insert a new talk comment
    public long insertTalkComment(int talkID, JSONObject talkComment) {
        ContentValues values = new ContentValues();
        values.put("talk_id", talkID);
        values.put("json", talkComment.toString());
        return db.insert("tcomments", "", values);
    }

    // Insert a new event comment
    public long insertEventComment(int eventRowID, JSONObject eventComment) {
        ContentValues values = new ContentValues();
        values.put("event_id", eventRowID);
        values.put("json", eventComment.toString());
        return db.insert("ecomments", "", values);
    }

    /**
     * Set a talk's starred value
     *
     * @param talkURI
     * @param isStarred
     * @return
     */
    public void markTalkStarred(String talkURI, boolean isStarred) {
        ContentValues values = new ContentValues();
        values.put("starred", isStarred ? 1 : 0);
        db.update("talks", values, "uri=?", new String[]{talkURI});
    }

    /**
     * Remove the specified type from the event.
     *
     * @param event_type Event type.
     */
    public void deleteAllEventsFromType(String event_type) {
        db.execSQL("DELETE FROM event_types WHERE event_type=?", new String[]{event_type});
    }

    // Removes all talks from specified event
    public void deleteTalksFromEvent(int event_id) {
        db.delete("talks", "event_id=?", new String[]{Integer.toString(event_id)});
    }

    // Removes all comments from specified event
    public void deleteCommentsFromEvent(int event_id) {
        db.delete("ecomments", "event_id=?", new String[]{Integer.toString(event_id)});
    }

    // Removes all comments from specified talk
    public void deleteCommentsFromTalk(int talk_id) {
        db.delete("tcomments", "talk_id=?", new String[]{Integer.toString(talk_id)});
    }

    // Removes EVERYTHING
    public void deleteAll() {
        db.delete("events", null, null);
        db.delete("talks", null, null);
        db.delete("ecomments", null, null);
        db.delete("tcomments", null, null);
    }

    // Populates an event adapter and return the number of items populated
    public int populateEvents(String event_type, EventAdapter m_eventAdapter, int order) {
        // Different handling for favorite list
        if (event_type.equals("favorites")) {
            Cursor c = this.db.rawQuery("SELECT json,events._rowid_ FROM events INNER JOIN favlist ON favlist.event_id = events._rowid_", null);
            int count = c.getCount();
            populate(c, m_eventAdapter);
            return count;
        }


        String order_sql = "";

        switch (order) {
            case ORDER_DATE_ASC:
                order_sql = "ORDER BY event_start ASC";
                break;
            case ORDER_DATE_DESC:
                order_sql = "ORDER BY event_start DESC";
                break;
            case ORDER_TITLE_ASC:
                order_sql = "ORDER BY event_title ASC";
                break;
            case ORDER_TITLE_DESC:
                order_sql = "ORDER BY event_title DESC";
                break;
        }

        Cursor c = this.db.rawQuery("SELECT json,events._rowid_ FROM events INNER JOIN event_types ON event_id = events._rowid_ WHERE event_types.event_type = '" + event_type + "' " + order_sql, null);

        int count = c.getCount();
        populate(c, m_eventAdapter);
        return count;
    }

    // Populates a talk adapter and returns the number of items populated
    public int populateTalks(int event_id, TalkAdapter m_talkAdapter) {
        Cursor c;

        c = this.db.rawQuery("SELECT json,_rowid_,starred FROM talks WHERE event_id = " + event_id, null);

        int count = c.getCount();
        populateTalks(c, m_talkAdapter);
        return count;
    }

    public int getTalkCountForEvent(int event_id) {
        Cursor c = this.db.rawQuery("SELECT json,_rowid_ FROM talks WHERE event_id = " + event_id, null);
        int count = c.getCount();
        c.close();

        return count;
    }

    // Populates a talk comment adapter and returns the number of items populated
    public int populateTalkComments(int talk_id, TalkCommentAdapter m_talkCommentAdapter) {
        Cursor c = this.db.rawQuery("SELECT json,_rowid_ FROM tcomments WHERE talk_id = " + talk_id, null);
        int count = c.getCount();
        populate(c, m_talkCommentAdapter);
        return count;
    }

    // Populates an event comment adapter and returns the number of items populated
    public int populateEventComments(int event_id, EventCommentAdapter m_eventCommentAdapter) {
        Cursor c = this.db.rawQuery("SELECT json,_rowid_ FROM ecomments WHERE event_id = " + event_id, null);
        int count = c.getCount();
        populate(c, m_eventCommentAdapter);
        return count;
    }

    public int getTrackCountForEvent(int event_id) {
        Cursor c = this.db.rawQuery("SELECT json,_rowid_ FROM tracks WHERE event_id = " + event_id, null);
        int count = c.getCount();
        c.close();

        return count;
    }

    public int populateTracks(int event_id, TrackAdapter m_trackAdapter) {
        Cursor c = this.db.rawQuery("SELECT json,_rowid_ FROM tracks WHERE event_id = " + event_id, null);
        int count = c.getCount();
        populate(c, m_trackAdapter);
        return count;
    }

    // Populates specified adapter with a query (should be JSON data column)
    private void populate(Cursor c, ArrayAdapter<JSONObject> adapter) {
        // No cursor given, do not do anything
        if (c == null) return;

        // Start with first item
        if (c.moveToFirst()) {
            do {
                try {
                    // Add JSON data to the adapter
                    JSONObject data = new JSONObject(c.getString(0));
                    data.put("rowID", c.getInt(1));
                    adapter.add(data);
                } catch (JSONException e) {
                    Log.e(JIActivity.LOG_JOINDIN_APP, "Could not add item to list", e);
                }
            } while (c.moveToNext());
        }
        // Close cursor
        if (!c.isClosed()) c.close();
    }

    /**
     * Slightly different population method for talks
     * as they have a 'starred' field which also needs populating into each JSON object
     *
     * @param c
     * @param adapter
     */
    private void populateTalks(Cursor c, ArrayAdapter<JSONObject> adapter) {
        if (c == null) return;

        // Start with first item
        if (c.moveToFirst()) {
            do {
                try {
                    // Add JSON data to the adapter
                    JSONObject data = new JSONObject(c.getString(0));
                    data.put("rowID", c.getInt(1));

                    // Talks have a 'starred' field, expected to be boolean by the adapter
                    data.put("starred", c.getInt(2) == 1);
                    adapter.add(data);
                } catch (JSONException e) {
                    Log.e(JIActivity.LOG_JOINDIN_APP, "Could not add talk to list", e);
                }
            } while (c.moveToNext());
        }

        if (!c.isClosed()) c.close();
    }

    private static class OpenHelper extends SQLiteOpenHelper {
        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        // Create new database (if needed)
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE [events]    ([event_uri] VARCHAR COLLATE NOCASE, [event_title] VARCHAR COLLATE NOCASE, [event_start] VARCHAR COLLATE NOCASE, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [event_types] ([event_id] INTEGER, [event_type] VARCHAR COLLATE NOCASE)");
            db.execSQL("CREATE TABLE [talks]     ([event_id] INTEGER, [uri] VARCHAR, [track_id] INTEGER, [starred] INTEGER DEFAULT 0, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [tracks]     ([event_id] INTEGER, [uri] VARCHAR, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [ecomments] ([event_id] INTEGER, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [tcomments] ([talk_id] INTEGER, [json] VARCHAR)");
        }

        // Upgrade database. Drop everything and call onCreate.. We do not care for old data anyway
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Drop everything
            db.execSQL("DROP TABLE IF EXISTS events");
            db.execSQL("DROP TABLE IF EXISTS event_types");
            db.execSQL("DROP TABLE IF EXISTS talks");
            db.execSQL("DROP TABLE IF EXISTS tracks");
            db.execSQL("DROP TABLE IF EXISTS ecomments");
            db.execSQL("DROP TABLE IF EXISTS tcomments");

            // Create new database
            onCreate(db);
        }
    }

}

