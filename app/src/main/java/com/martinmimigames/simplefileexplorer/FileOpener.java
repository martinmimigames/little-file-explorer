package com.martinmimigames.simplefileexplorer;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;

import static android.app.Activity.RESULT_OK;

class FileOpener {

  private static final String SHARE_FILE_WITH = "Share with";
  private final Activity activity;
  boolean isRequestDocument;

  FileOpener(Activity activity) {
    this.activity = activity;
  }

  void open(final File file) {
    final Intent intent = new Intent(Intent.ACTION_VIEW);
    final Uri uri = getUriFromFile(file);
    intent.setDataAndType(uri, FileProvider.getFileType(file));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      intent.setClipData(ClipData.newRawUri("", uri));
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (isRequestDocument) {
      activity.setResult(RESULT_OK, intent);
      activity.finish();
      return;
    }
    activity.startActivity(intent);
  }

  void share(File[] files) {
    if (isRequestDocument) {
      open(files[0]);
      return;
    }
    Intent intent;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT && files.length > 1) {
      intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
      var fileList = new ArrayList<Uri>();
      String generalMimetype = null;
      for (var file : files) {
        fileList.add(getUriFromFile(file));
        var mimetype = FileProvider.getFileType(file);
        if (generalMimetype == null) {
          generalMimetype = mimetype;
          continue;
        }
        if (generalMimetype.equals(mimetype))
          continue;
        var type = mimetype.split("/")[0];
        if (generalMimetype.startsWith(type)) {
          generalMimetype = type + "/*";
          continue;
        }
        generalMimetype = "*/*";
      }
      intent.setType(generalMimetype);
      intent.putExtra(Intent.EXTRA_STREAM, fileList);
    } else {
      intent = new Intent(Intent.ACTION_SEND);
      intent.setType(FileProvider.getFileType(files[0]));
      intent.putExtra(Intent.EXTRA_SUBJECT, files[0].getName());
      intent.putExtra(Intent.EXTRA_STREAM, getUriFromFile(files[0]));
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity.startActivity(Intent.createChooser(intent, SHARE_FILE_WITH));
  }

  private Uri getUriFromFile(File file) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
      return FileProvider.fileToUri(file);
    else
      return Uri.fromFile(file);
  }
}
