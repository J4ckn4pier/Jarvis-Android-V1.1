package com.jarvis.mobile.brain;

import android.content.Context;
import android.content.SharedPreferences;
import com.jarvis.brain.MusicQueueStorePersistence;
import com.jarvis.brain.MusicTrack;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** App-private persistence for UI music queue/playback state. Audio transport/credentials remain elsewhere. */
public final class AndroidMusicQueueStorePersistence implements MusicQueueStorePersistence {
    private static final String NAME = "jarvis_music_queue";
    private static final String KEY = "snapshot";
    private final SharedPreferences preferences;

    public AndroidMusicQueueStorePersistence(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
        preferences = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Snapshot load() {
        String encoded = preferences.getString(KEY, null);
        if (encoded == null || encoded.isBlank()) return Snapshot.empty();
        try {
            JSONObject value = new JSONObject(encoded);
            JSONArray queueJson = value.optJSONArray("queue");
            List<MusicTrack> queue = new ArrayList<>();
            if (queueJson != null) {
                for (int i = 0; i < queueJson.length(); i++) {
                    JSONObject track = queueJson.optJSONObject(i);
                    if (track == null) continue;
                    try {
                        queue.add(new MusicTrack(
                                track.getString("id"),
                                track.getString("title"),
                                track.optString("artist", ""),
                                track.optLong("durationSeconds", 0)));
                    } catch (JSONException | IllegalArgumentException ignored) { }
                }
            }
            return new Snapshot(
                    queue,
                    value.optString("currentId", ""),
                    value.optBoolean("playing", false),
                    value.optBoolean("shuffle", false),
                    value.optBoolean("repeat", false),
                    value.optInt("volume", 70),
                    value.optLong("positionSeconds", 0));
        } catch (JSONException ignored) {
            return Snapshot.empty();
        }
    }

    @Override
    public void save(Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot required");
        try {
            JSONArray queue = new JSONArray();
            for (MusicTrack track : snapshot.queue()) {
                JSONObject item = new JSONObject();
                item.put("id", track.id());
                item.put("title", track.title());
                item.put("artist", track.artist());
                item.put("durationSeconds", track.durationSeconds());
                queue.put(item);
            }
            JSONObject value = new JSONObject();
            value.put("queue", queue);
            value.put("currentId", snapshot.currentId());
            value.put("playing", snapshot.playing());
            value.put("shuffle", snapshot.shuffle());
            value.put("repeat", snapshot.repeat());
            value.put("volume", snapshot.volume());
            value.put("positionSeconds", snapshot.positionSeconds());
            preferences.edit().putString(KEY, value.toString()).apply();
        } catch (JSONException failure) {
            throw new IllegalStateException("Unable to serialize music queue", failure);
        }
    }
}
