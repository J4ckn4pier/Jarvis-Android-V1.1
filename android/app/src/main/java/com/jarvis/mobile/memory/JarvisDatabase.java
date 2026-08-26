package com.jarvis.mobile.memory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class JarvisDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "jarvis_private_memory.db";
    private static final int DATABASE_VERSION = 1;
    private static volatile JarvisDatabase instance;

    public static JarvisDatabase get(Context context) {
        if (instance == null) {
            synchronized (JarvisDatabase.class) {
                if (instance == null) instance = new JarvisDatabase(context.getApplicationContext());
            }
        }
        return instance;
    }

    private JarvisDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "memory_key TEXT NOT NULL COLLATE NOCASE UNIQUE," +
                "memory_value TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'open'," +
                "created_at INTEGER NOT NULL," +
                "completed_at INTEGER)");
        db.execSQL("CREATE TABLE events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "event_type TEXT NOT NULL," +
                "source TEXT," +
                "title TEXT," +
                "body TEXT," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX events_type_time ON events(event_type, created_at DESC)");
        db.execSQL("CREATE INDEX tasks_status_time ON tasks(status, created_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized void remember(String key, String value) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("memory_key", key.trim());
        values.put("memory_value", value.trim());
        values.put("created_at", now);
        values.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict(
                "memories", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String recall(String key) {
        String requested = key == null ? "" : key.trim();
        String selection = requested.isEmpty() ? null : "memory_key = ? OR memory_key LIKE ?";
        String[] args = requested.isEmpty() ? null : new String[]{requested, "%" + requested + "%"};
        try (Cursor cursor = getReadableDatabase().query(
                "memories",
                new String[]{"memory_key", "memory_value"},
                selection,
                args,
                null,
                null,
                "updated_at DESC",
                "1")) {
            if (!cursor.moveToFirst()) return "";
            return cursor.getString(0) + ": " + cursor.getString(1);
        }
    }

    public synchronized String recentMemories(int limit) {
        List<String> lines = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "memories",
                new String[]{"memory_key", "memory_value"},
                null,
                null,
                null,
                null,
                "updated_at DESC",
                String.valueOf(Math.max(1, limit)))) {
            while (cursor.moveToNext()) lines.add("• " + cursor.getString(0) + ": " + cursor.getString(1));
        }
        return String.join("\n", lines);
    }

    public synchronized long addTask(String title) {
        ContentValues values = new ContentValues();
        values.put("title", title.trim());
        values.put("status", "open");
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("tasks", null, values);
    }

    public synchronized String openTasks(int limit) {
        List<String> lines = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "tasks",
                new String[]{"id", "title"},
                "status = ?",
                new String[]{"open"},
                null,
                null,
                "created_at ASC",
                String.valueOf(Math.max(1, limit)))) {
            while (cursor.moveToNext()) lines.add("• " + cursor.getLong(0) + ": " + cursor.getString(1));
        }
        return String.join("\n", lines);
    }

    public synchronized boolean completeTask(String query) {
        long id = -1;
        try {
            id = Long.parseLong(query.trim());
        } catch (NumberFormatException ignored) {
        }
        String where;
        String[] args;
        if (id >= 0) {
            where = "id = ? AND status = 'open'";
            args = new String[]{String.valueOf(id)};
        } else {
            where = "title LIKE ? AND status = 'open'";
            args = new String[]{"%" + query.trim() + "%"};
        }
        ContentValues values = new ContentValues();
        values.put("status", "complete");
        values.put("completed_at", System.currentTimeMillis());
        return getWritableDatabase().update("tasks", values, where, args) > 0;
    }

    public synchronized void logEvent(String type, String source, String title, String body) {
        ContentValues values = new ContentValues();
        values.put("event_type", type);
        values.put("source", source);
        values.put("title", title);
        values.put("body", body);
        values.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("events", null, values);
        getWritableDatabase().delete(
                "events",
                "id NOT IN (SELECT id FROM events ORDER BY created_at DESC LIMIT 500)",
                null);
    }

    public synchronized String recentNotifications(int limit) {
        List<String> lines = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "events",
                new String[]{"source", "title", "body"},
                "event_type = ?",
                new String[]{"notification"},
                null,
                null,
                "created_at DESC",
                String.valueOf(Math.max(1, limit)))) {
            while (cursor.moveToNext()) {
                String source = safe(cursor.getString(0));
                String title = safe(cursor.getString(1));
                String body = safe(cursor.getString(2));
                String summary = (source + " — " + title + (body.isEmpty() ? "" : ": " + body)).trim();
                if (!summary.isEmpty()) lines.add("• " + summary);
            }
        }
        return String.join("\n", lines);
    }

    public synchronized int memoryCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public synchronized int openTaskCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE status = 'open'", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
