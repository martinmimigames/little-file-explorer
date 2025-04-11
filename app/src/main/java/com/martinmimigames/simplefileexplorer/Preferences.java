package com.martinmimigames.simplefileexplorer;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {

    static final String FILE_PATH_KEY = "file_path";
    static final String TOGGLE_HIDDEN_KEY = "toggle_hidden";

    static final String SORTER_KEY = "sorter2";
    static final String SORTER_FLIP = "sorterf";
    static final String THEME_KEY = "theme2";

    SharedPreferences sharedPreferences;


    void onCreate(Context context) {
        sharedPreferences = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
    }

    SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }
}
