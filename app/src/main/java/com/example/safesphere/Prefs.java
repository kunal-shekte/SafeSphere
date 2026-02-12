package com.example.safesphere;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String PREF_NAME = "safesphere_prefs";

    public static void saveUser(Context ctx, String name, String phone,
                                String keyword, String e1, String e2, String e3) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString("name", name)
                .putString("phone", phone)
                .putString("keyword", keyword.toLowerCase().trim())
                .putString("e1", e1)
                .putString("e2", e2)
                .putString("e3", e3)
                .apply();
    }

    public static SharedPreferences getAll(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getKeyword(Context ctx) {

        return getAll(ctx).getString("keyword", "");
    }


    public static String getUserPhone(Context ctx) {
        return getAll(ctx).getString("phone", "");
    }
    public static String[] getEmergencyNumbers(Context ctx) {
        SharedPreferences sp = getAll(ctx);
        return new String[]{
                sp.getString("e1", ""),
                sp.getString("e2", ""),
                sp.getString("e3", "")
        };
    }
}
