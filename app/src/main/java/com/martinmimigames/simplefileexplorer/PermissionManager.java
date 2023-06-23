package com.martinmimigames.simplefileexplorer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import static android.provider.Settings.*;

public class PermissionManager {

  private static final int textId = Integer.MAX_VALUE - 1;
  private static final int cancelId = Integer.MAX_VALUE - 2;
  private static final int grantId = Integer.MAX_VALUE - 3;
  private final Activity activity;
  private final Dialog permissionDialog;

  @SuppressLint("SetTextI18n")
  public PermissionManager(Activity activity) {

    this.activity = activity;

    permissionDialog = new Dialog(this.activity);
    permissionDialog.setCancelable(false);
    permissionDialog.setTitle("Permission Request");

    RelativeLayout.LayoutParams layoutParams =
        new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );
    final RelativeLayout relativeLayout = new RelativeLayout(this.activity);
    relativeLayout.setLayoutParams(layoutParams);

    layoutParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );

    final TextView text = new TextView(this.activity);
    text.setId(textId);
    text.setLayoutParams(layoutParams);
    relativeLayout.addView(text);

    layoutParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
    layoutParams.addRule(RelativeLayout.BELOW, textId);

    final Button grant = new Button(this.activity);
    grant.setLayoutParams(layoutParams);
    grant.setText("Grant");
    grant.setId(grantId);
    relativeLayout.addView(grant);

    layoutParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    layoutParams.addRule(RelativeLayout.BELOW, textId);
    layoutParams.addRule(RelativeLayout.LEFT_OF, grantId);

    final Button deny = new Button(this.activity);
    deny.setLayoutParams(layoutParams);
    deny.setText("Deny");
    deny.setOnClickListener((v) -> permissionDialog.dismiss());
    deny.setId(cancelId);
    relativeLayout.addView(deny);

    permissionDialog.setContentView(relativeLayout);
  }

  public boolean havePermission(String permission) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
    boolean permissionAllowed = activity.getPackageManager().checkPermission(permission, activity.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    if (!permissionAllowed) {
      switch (permission) {
        case Manifest.permission.READ_EXTERNAL_STORAGE:
        case Manifest.permission.WRITE_EXTERNAL_STORAGE:
          if (Build.VERSION.SDK_INT >= 30)
            permissionAllowed = Environment.isExternalStorageManager();
          break;
      }
    }
    return permissionAllowed;
  }

  public boolean havePermission(String[] permissions) {
    for (String permission : permissions)
      if (!havePermission(permission))
        return false;
    return true;
  }

  public boolean getPermission(String permission, String description, boolean cancelable) {
    if (havePermission(permission))
      return true;

    ((TextView) permissionDialog.findViewById(textId)).setText(description);

    if (cancelable)
      permissionDialog.findViewById(cancelId).setVisibility(View.VISIBLE);
    else
      permissionDialog.findViewById(cancelId).setVisibility(View.GONE);

    permissionDialog.findViewById(grantId).setOnClickListener((v) -> {
      openSettings(permission);
      permissionDialog.dismiss();
    });

    activity.runOnUiThread(permissionDialog::show);
    return false;
  }

  public void openSettings(String permission) {
    switch (permission) {
      case Manifest.permission.WRITE_EXTERNAL_STORAGE:
      case Manifest.permission.READ_EXTERNAL_STORAGE:
        if (Build.VERSION.SDK_INT >= 30) {
          try {
            final Intent intent = new Intent(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
            return;
          } catch (ActivityNotFoundException ignored) {
          }
        }
        break;
    }
    try {
      final Intent intent = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      final Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
      intent.setData(uri);
      activity.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      final Intent intent = new Intent(ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity.startActivity(intent);
    }
  }
}
