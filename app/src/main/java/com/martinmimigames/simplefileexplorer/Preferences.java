package com.martinmimigames.simplefileexplorer;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {

  private static final String PACKAGE_NAME = "com.martinmimigames.simplefileexplorer";

  static final String FILE_PATH_KEY = "file_path";
  static final String TOGGLE_HIDDEN_KEY = "toggle_hidden";

  static final String SORTER_KEY = "sorter";

  SharedPreferences sharedPreferences;


  void onCreate(Context context) {
    sharedPreferences = context.getSharedPreferences(Preferences.PACKAGE_NAME, Context.MODE_PRIVATE);
  }

  SharedPreferences getSharedPreferences() {
    return sharedPreferences;
  }
}
