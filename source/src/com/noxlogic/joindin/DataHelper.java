package com.noxlogic.joindin;

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

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.ArrayAdapter;


public class DataHelper {
    private static final String DATABASE_NAME = "joindin.db";
    private static final int DATABASE_VERSION = 4;  // Increasing this version number will result in automatic call to onUpgrade()

    private Context context;
    private SQLiteDatabase db;

    public DataHelper(Context context) {
        this.context = context;
        OpenHelper openHelper = new OpenHelper(this.context);
        this.db = openHelper.getWritableDatabase();
    }

    // Updates a event
    public long updateEvent(int event_id, JSONObject event) {
        ContentValues values = new ContentValues();
        values.put("json", event.toString());
        return this.db.update("events", values, "event_id=?", new String[] {Integer.toString(event_id)});
    }

    // insert a new event to specified event_type (hot, pending, past etc)
    public long insertEvent(String event_type, JSONObject event) {
        ContentValues values = new ContentValues();
        values.put("event_id", event.optInt("ID"));
        values.put("event_type", event_type);
        values.put("json", event.toString());
        return this.db.insert("events", "", values);
    }

    // Insert a new talk
    public long insertTalk (JSONObject talk) {
        ContentValues values = new ContentValues();
        values.put("event_id", talk.optInt("event_id"));
        values.put("json", talk.toString());
        return this.db.insert("talks", "", values);
    }

    // Insert a new talk comment
    public long insertTalkComment (JSONObject talkComment) {
        ContentValues values = new ContentValues();
        values.put("talk_id", talkComment.optInt("talk_id"));
        values.put("json", talkComment.toString());
        return this.db.insert("tcomments", "", values);
    }

    // Insert a new event comment
    public long insertEventComment (JSONObject eventComment) {
        ContentValues values = new ContentValues();
        values.put("event_id", eventComment.optInt("event_id"));
        values.put("json", eventComment.toString());
        return this.db.insert("ecomments", "", values);
    }

    // Removes all events for specified event type (hot, pending, past etc)
    public void deleteAllEventsFromType(String event_type) {
        db.delete ("events", "event_type=?", new String[] {event_type});
    }

    // Removes all talks from specified event
    public void deleteTalksFromEvent(int event_id) {
        db.delete("talks", "event_id=?", new String[] {Integer.toString(event_id)});
    }

    // Removes all comments from specified event
    public void deleteCommentsFromEvent(int event_id) {
        db.delete("ecomments", "event_id=?", new String[] {Integer.toString(event_id)});
    }

    // Removes all comments from specified talk
    public void deleteCommentsFromTalk(int talk_id) {
        db.delete("tcomments", "talk_id=?", new String[] {Integer.toString(talk_id)});
    }

    // Removes EVERYTHING
    public void deleteAll () {
        db.delete("events", null, null);
        db.delete("talks", null, null);
        db.delete("ecomments", null, null);
        db.delete("tcomments", null, null);
    }

    // Populates an event adapter and return the number of items populated
    public int populateEvents(String event_type, JIEventAdapter m_eventAdapter) {
        Cursor c = this.db.rawQuery("SELECT json FROM events WHERE event_type = '"+event_type+"'", null);
        int count = c.getCount();
        populate (c, m_eventAdapter);
        return count;
    }

    // Populates a talk adapter and returns the number of items populated
    public int populateTalks(int event_id, JITalkAdapter m_talkAdapter) {
        Cursor c = this.db.rawQuery("SELECT json FROM talks WHERE event_id = "+event_id, null);
        int count = c.getCount();
        populate (c, m_talkAdapter);
        return count;
    }

    // Populates a talk comment adapter and retusn the number of items populated
    public int populateTalkComments(int talk_id, JITalkCommentAdapter m_talkCommentAdapter) {
        Cursor c = this.db.rawQuery("SELECT json FROM tcomments WHERE talk_id = "+talk_id, null);
        int count = c.getCount();
        populate (c, m_talkCommentAdapter);
        return count;
    }

    // Populates an event comment adapter and returns the number of items populated
    public int populateEventComments(int event_id, JIEventCommentAdapter m_eventCommentAdapter) {
        Cursor c = this.db.rawQuery("SELECT json FROM ecomments WHERE event_id = "+event_id, null);
        int count = c.getCount();
        populate (c, m_eventCommentAdapter);
        return count;
    }

    // Populates specified adapter with a query (should be JSON data column)
    private void populate (Cursor c, ArrayAdapter<JSONObject> adapter) {
        // No cursor given, do not do anything
        if (c == null) return;

        // Start with first item
        if (c.moveToFirst()) {
            do {
                try {
                    // Add JSON data to the adapter
                    adapter.add(new JSONObject(c.getString(0)));
                } catch (JSONException e) { e.printStackTrace(); }
            } while (c.moveToNext());
        }
        // Close cursor
        if (c != null && !c.isClosed()) c.close();
    }


    private static class OpenHelper extends SQLiteOpenHelper {
        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        // Create new database (if needed)
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE [events]    ([event_id] INTEGER, [event_type] VARCHAR, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [talks]     ([event_id] INTEGER, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [ecomments] ([event_id] INTEGER, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [tcomments] ([talk_id] INTEGER, [json] VARCHAR)");
        }

        // Upgrade database. Drop everything and call onCreate.. We do not care for old data anyway
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Drop everything
            db.execSQL("DROP TABLE IF EXISTS events");
            db.execSQL("DROP TABLE IF EXISTS talks");
            db.execSQL("DROP TABLE IF EXISTS ecomments");
            db.execSQL("DROP TABLE IF EXISTS tcomments");

            // Create new database
            onCreate(db);
        }
    }

}

