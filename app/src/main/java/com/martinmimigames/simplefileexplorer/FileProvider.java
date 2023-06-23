package com.martinmimigames.simplefileexplorer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class FileProvider extends ContentProvider {

  private static final String SCHEME = "content://";
  private static final String AUTHORITY = "com.martinmimigames.simplefileexplorer.FileProvider";

  private static HashMap<Integer, FileNameRecord> database;

  public static File uriToFile(Uri uri) {
    final List<String> pathSegments = uri.getPathSegments();
    final FileNameRecord data = database.get(Integer.parseInt(pathSegments.get(pathSegments.size() - 2)));
    if (data == null)
      return null;
    else
      return data.name.equals(pathSegments.get(pathSegments.size() - 1)) ? data.file : null;
  }

  public static Uri fileToUri(File file) {
    final Random r = new Random();
    int key;
    while (true) {
      key = r.nextInt();
      if (database.get(key) == null) {
        final FileNameRecord data = new FileNameRecord();
        data.name = file.getName();
        data.file = file;
        database.put(key, data);
        return Uri.parse(SCHEME
            + AUTHORITY + "/"
            + key + "/"
            + data.name);
      }
    }
  }

  public static String getFileType(File file) {
    final int lastDot = file.getName().lastIndexOf('.');
    if (lastDot >= 0) {
      final String extension = file.getName().substring(lastDot + 1);
      final String mime = MimeTypeMap
          .getSingleton()
          .getMimeTypeFromExtension(extension);
      if (mime != null) return mime;
    }
    return "application/octet-stream";
  }

  @Override
  public boolean onCreate() {
    database = new HashMap<>();
    return true;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    final File file = uriToFile(uri);
    if (file == null)
      return new MatrixCursor(new String[0], 0);

    final String[] fileNameList = new String[]{file.getName()};
    final File[] fileList = new File[]{file};
    final MatrixCursor cursor = new MatrixCursor(fileNameList, 1);
    cursor.addRow(fileList);
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return getFileType(Objects.requireNonNull(uriToFile(uri)));
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return fileToUri(new File(uri.getPath()));
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return Objects.requireNonNull(uriToFile(uri)).delete() ? 1 : 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException("No external updates");
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    final File file = uriToFile(uri);
    int pfdMode;
    mode = mode.toLowerCase();

    final boolean readMode = mode.contains("r");
    final boolean writeMode = mode.contains("w");
    final boolean truncateMode = mode.contains("t");

    if (readMode && writeMode) pfdMode = ParcelFileDescriptor.MODE_READ_WRITE;
    else if (writeMode) pfdMode = ParcelFileDescriptor.MODE_WRITE_ONLY;
    else pfdMode = ParcelFileDescriptor.MODE_READ_ONLY;

    if (truncateMode) pfdMode = pfdMode | ParcelFileDescriptor.MODE_TRUNCATE;
    return ParcelFileDescriptor.open(file, pfdMode);
  }

  private static class FileNameRecord {
    String name;
    File file;
  }
}
