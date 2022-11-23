package com.martinmimigames.simplefileexplorer;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;

public class FileOpener {


  public static final int VIEW = 1;
  public static final int SHARE = 2;
  private static final String OPEN_FILE_WITH = "open file with :";
  private static final String SHARE_FILE_WITH = "share file with :";
  private final Activity activity;
  boolean isRequestDocument;


  public FileOpener(Activity activity) {
    this.activity = activity;
  }

  public void open(final File file, final int operationId) {
    switch (operationId) {
      case VIEW:
        openFile(file);
        break;
      case SHARE:
        shareFile(file);
        break;
    }
  }

  private void openFile(final File file) {
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
    activity.startActivity(Intent.createChooser(intent, OPEN_FILE_WITH));
  }

  private void shareFile(final File file) {
    if (isRequestDocument) {
      openFile(file);
      return;
    }
    final Intent intent = new Intent(Intent.ACTION_SEND);
    final Uri uri = getUriFromFile(file);
    intent.setType(FileProvider.getFileType(file));
    intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
    intent.putExtra(Intent.EXTRA_STREAM, uri);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity.startActivity(Intent.createChooser(intent, SHARE_FILE_WITH));
  }

  private Uri getUriFromFile(File file) {
    if (Build.VERSION.SDK_INT >= 24)
      return FileProvider.fileToUri(file);
    else
      return Uri.fromFile(file);
  }
}
