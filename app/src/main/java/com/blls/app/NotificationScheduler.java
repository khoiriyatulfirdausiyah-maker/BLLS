package com.blls.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

public class NotificationScheduler {

    private static final String CHANNEL_ID = "blls_due";
    private static final String PREFS = "blls_notification_prefs";
    private static final String KEY_JSON = "notification_json";
    private static final String KEY_CODES = "request_codes";

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Jatuh Tempo BLLS",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Pengingat cicilan H-3, H-1, dan hari H.");
            nm.createNotificationChannel(channel);
        }
    }

    public static void scheduleAll(Context context, String json) {
        ensureChannel(context);
        cancelOld(context);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_JSON, json == null ? "[]" : json).apply();

        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            StringBuilder codes = new StringBuilder();
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            long now = System.currentTimeMillis();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.optLong("at", 0L);
                if (at <= now) continue;

                int requestCode = o.optInt("requestCode", 1);
                String title = o.optString("title", "BLLS");
                String text = o.optString("text", "Ada cicilan yang mendekati jatuh tempo.");

                Intent intent = new Intent(context, NotificationReceiver.class);
                intent.putExtra("notificationId", requestCode);
                intent.putExtra("title", title);
                intent.putExtra("text", text);

                PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, at, pi);
                }

                if (codes.length() > 0) codes.append(",");
                codes.append(requestCode);
            }

            prefs.edit().putString(KEY_CODES, codes.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void rescheduleSaved(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_JSON, "[]");
        scheduleAll(context, json);
    }

    private static void cancelOld(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_CODES, "");
        if (saved == null || saved.trim().isEmpty()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String part : saved.split(",")) {
            try {
                int code = Integer.parseInt(part.trim());
                Intent intent = new Intent(context, NotificationReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        code,
                        intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            } catch (Exception ignored) {
            }
        }
        prefs.edit().remove(KEY_CODES).apply();
    }

    public static String channelId() {
        return CHANNEL_ID;
    }
}
