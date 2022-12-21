package com.martinmimigames.simplefileexplorer;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.res.Resources.getSystem;
import static android.view.Gravity.CENTER_VERTICAL;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public class MainActivity extends Activity {

  private static final int INFO_VIEW_ID = Integer.MAX_VALUE;
  private static final int ONGOING_OPERATION_ID = Integer.MAX_VALUE - 1;
  private static final int LOADING_VIEW_ID = Integer.MAX_VALUE - 2;
  private static final String FILE_PATH_FLAG = "file path";
  private static final int HIGHLIGHT_COLOR = 0xFF00A4C9;
  private final Status idleState;
  private final Status selectState;
  private final Status pasteState;
  private final Status openFileState;
  private final ShortTabOnButton shortTabOnButton;
  private final LongPressOnButton longPressOnButton;
  private final ArrayList<File> currentSelectedFiles;
  private final ThreadPoolExecutor executor;
  private final FileOpener fopen;
  private Status userStatus;
  private int hasOperations;
  private LinearLayout ll;
  private int width;
  private Bitmap fileImage, pictureImage, audioImage, videoImage, unknownImage, archiveImage, folderImage;
  private String filePath;
  private File parent;
  private boolean isCut, isCopy;
  private PermissionManager permissionManager;
  private Dialog openListDialog;
  private Dialog deleteConfirmationDialog;
  private Dialog createDirectoryDialog;
  private Dialog renameDialog;

  public MainActivity() {
    shortTabOnButton = new ShortTabOnButton();
    longPressOnButton = new LongPressOnButton();
    currentSelectedFiles = new ArrayList<>(3);
    executor = new ScheduledThreadPoolExecutor(1);
    fopen = new FileOpener(this);
    idleState = new IdleState();
    selectState = new SelectState();
    pasteState = new PasteState();
    openFileState = new OpenFileState();

    hasOperations = 0;
  }

  private void setupUI() {
    setupTopBar();
    setupMenu();
    //setupDriveList(); in onStart for reload
    setupSelectMenu();
    setupPasteMenu();
    setupOpenListDialogue();
    setupRenameDialog();
    setupDeleteDialog();
    setupCreateDirectoryDialog();
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    permissionManager = new PermissionManager(this);

    Intent intent = getIntent();
    switch (intent.getAction()) {
      case Intent.ACTION_OPEN_DOCUMENT:
      case Intent.ACTION_GET_CONTENT:
        fopen.isRequestDocument = true;
        setResult(RESULT_CANCELED);
        break;
      default:
        fopen.isRequestDocument = false;
    }

    ll = findViewById(R.id.list);

    setupBitmaps();
    setupUI();

    if (savedInstanceState != null) {
      filePath = savedInstanceState.getString(FILE_PATH_FLAG);
    }

    userStatus = idleState;
    idleState.start();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);
    savedInstanceState.putString(FILE_PATH_FLAG, filePath);
  }

  @Override
  protected void onStart() {
    super.onStart();
    final File[] rootDirectories = FileOperation.getAllStorages(this);
    setupDriveList(rootDirectories);

    executor.execute(() -> {
      if (filePath != null) {
        if (checkPermission())
          listItem(new File(filePath));
      } else {
        for (File folder : rootDirectories) {
          if (checkPermission()) listItem(folder);
          break;
        }
      }
    });
  }

  private boolean checkPermission() {

    permissionManager.getPermission(READ_EXTERNAL_STORAGE, "Storage access is required", false);
    permissionManager.getPermission(WRITE_EXTERNAL_STORAGE, "Storage access is required", false);

    return permissionManager.havePermission(new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE});
  }

  private void listItem(final File folder) {
    String info = "Name: " + folder.getName() + "\n";
    if (Build.VERSION.SDK_INT >= 9) {
      StatFs stat = new StatFs(folder.getPath());
      long bytesAvailable = Build.VERSION.SDK_INT >= 18 ?
        stat.getBlockSizeLong() * stat.getAvailableBlocksLong() :
        (long) stat.getBlockSize() * stat.getAvailableBlocks();
      info += "Available size: " + FileOperation.getReadableMemorySize(bytesAvailable) + "\n";
      if (Build.VERSION.SDK_INT >= 18) {
        bytesAvailable = stat.getTotalBytes();
        info += "Capacity size: " + FileOperation.getReadableMemorySize(bytesAvailable) + "\n";
      }
    }
    final String finalInfo = info;
    runOnUiThread(() -> {
      ll.removeAllViews();
      ((TextView) findViewById(R.id.title)).setText(filePath);
      addIdDialog(finalInfo, 16, INFO_VIEW_ID);

      addIdDialog("Loading...", 16, LOADING_VIEW_ID);
      addIdDialog("cutting/copying/deleting files...", 16, ONGOING_OPERATION_ID);
      if (hasOperations == 0)
        findViewById(ONGOING_OPERATION_ID).setVisibility(View.GONE);
    });

    parent = folder.getParentFile();

    filePath = folder.getPath();
    if (folderAccessible(folder)) {
      final File[] items = folder.listFiles();
      assert items != null;
      sort(items);
      if (items.length == 0) {
        addDialog("Empty folder!", 16);
      } else {
        String lastLetter = "";
        boolean hasFolders = false;
        for (File item : items) {
          if (item.isDirectory()) {
            if (!hasFolders) {
              addDialog("Folders:", 18);
              hasFolders = true;
            }
            if (item.getName().substring(0, 1)
              .compareToIgnoreCase(lastLetter) > 0) {
              lastLetter = item.getName().substring(0, 1).toUpperCase();
              addDialog(lastLetter, 16);
            }
            addDirectory(item);
          }
        }
        lastLetter = "";
        boolean hasFiles = false;
        for (File item : items)
          if (item.isFile()) {
            if (!hasFiles) {
              addDialog("Files:", 18);
              hasFiles = true;
            }
            if (item.getName().substring(0, 1)
              .compareToIgnoreCase(lastLetter) > 0) {
              lastLetter = item.getName().substring(0, 1).toUpperCase();
              addDialog(lastLetter, 16);
            }
            switch (FileProvider.getFileType(item).split("/")[0]) {
              case "image":
                addItem(getImageView(pictureImage), item);
                break;
              case "video":
                addItem(getImageView(videoImage), item);
                break;
              case "audio":
                addItem(getImageView(audioImage), item);
                break;
              case "application":
                if (FileProvider.getFileType(item).contains("application/octet-stream"))
                  addItem(getImageView(unknownImage), item);
                else
                  addItem(getImageView(archiveImage), item);
                break;
              case "text":
                addItem(getImageView(fileImage), item);
                break;
              default:
                addItem(getImageView(unknownImage), item);
            }
          }
      }
    } else {
      if (filePath.contains("Android/data") || filePath.contains("Android/obb")) {
        addDialog("For android 11 or higher, Android/data and Android/obb is refused access.\n", 16);
      } else addDialog("Access Denied", 16);
    }
    runOnUiThread(() -> ll.removeView(findViewById(LOADING_VIEW_ID)));
  }

  private void addIdDialog(final String dialog, final int textSize, final int id) {
    final TextView tv = new TextView(this);
    tv.setText(dialog);
    tv.setBackgroundColor(Color.TRANSPARENT);
    tv.setGravity(CENTER_VERTICAL);
    tv.setTextColor(Color.WHITE);
    tv.setTextSize(textSize);
    tv.setId(id);

    runOnUiThread(() -> ll.addView(tv));
  }

  private void addDialog(final String dialog, final int textSize) {
    addIdDialog(dialog, textSize, View.NO_ID);
  }

  private void sort(final File[] items) {
    // for every item
    for (int i = 0; i < items.length; i++) {
      // j = for every next item
      for (int j = i + 1; j < items.length; j++) {
        // if larger than next
        if (items[i].toString()
          .compareToIgnoreCase(items[j].toString()) > 0) {
          File temp = items[i];
          items[i] = items[j];
          items[j] = temp;
        }
      }
    }
  }

  //return true if can read
  private boolean folderAccessible(final File folder) {
    try {
      return folder.canRead();
    } catch (SecurityException e) {
      return false;
    }
  }

  private void addDirectory(final File folder) {
    addItem(getImageView(folderImage), folder);
  }

  private void addItem(final ImageView imageView, final File file) {
    new Item(imageView, file, this);
  }

  private ImageView getImageView(final Bitmap bitmap) {
    final ImageView imageView = new ImageView(this);
    imageView.setImageBitmap(bitmap);
    final int width10 = width / 8;
    imageView.setAdjustViewBounds(true);
    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    imageView.setMinimumWidth(width10);
    imageView.setMinimumHeight(width10);
    imageView.setMaxWidth(width10);
    imageView.setMaxHeight(width10);
    return imageView;
  }

  private void selectFiles(File file, View view) {
    if (!currentSelectedFiles.contains(file)) {
      currentSelectedFiles.add(file);
      view.setBackgroundColor(HIGHLIGHT_COLOR);
    } else {
      currentSelectedFiles.remove(file);
      view.setBackgroundColor(Color.TRANSPARENT);
    }
    if (currentSelectedFiles.size() > 1) {
      findViewById(R.id.select_rename).setVisibility(View.GONE);
    } else {
      if (currentSelectedFiles.size() == 0) {
        userStatus.change(idleState);
      } else {
        userStatus.change(selectState);
      }
      findViewById(R.id.select_rename).setVisibility(View.VISIBLE);
    }
  }

  private void clearHighlight() {
    forEachItem(item -> item.setBackgroundColor(Color.TRANSPARENT));
  }

  private void setupTopBar() {
    findViewById(R.id.back_button).setOnClickListener((v) -> {
      if (parent != null && folderAccessible(parent))
        executor.execute(() -> listItem(parent));
    });

    findViewById(R.id.menu_button).setOnClickListener((v) -> {
      final View menuList = findViewById(R.id.menu_list);
      if (menuList.isShown())
        menuList.setVisibility(View.GONE);
      else
        menuList.setVisibility(View.VISIBLE);
    });
  }

  private void setupMenu() {
    findViewById(R.id.menu_create_new_directory)
      .setOnClickListener((v) -> {
        createDirectoryDialog.show();
        findViewById(R.id.menu_list).setVisibility(View.GONE);
      });
    findViewById(R.id.menu_quick_switch)
      .setOnClickListener((v) -> {
        findViewById(R.id.menu_list).setVisibility(View.GONE);
        final View driveList = findViewById(R.id.drive_list);
        if (driveList.isShown())
          driveList.setVisibility(View.GONE);
        else
          driveList.setVisibility(View.VISIBLE);
      });
  }

  private void setupDriveList(final File[] rootDirectories){
    final LinearLayout list = findViewById(R.id.drive_list);
    list.removeAllViews();
    for (File file: rootDirectories){
      final Button entry = new Button(this);
      entry.setLayoutParams(
        new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      );
      entry.setText(file.getPath());
      entry.setOnClickListener((v) -> {
        executor.execute(() -> listItem(file));
        findViewById(R.id.drive_list).setVisibility(View.GONE);
      });
      list.addView(entry);
    }
  }

  private void setupSelectMenu() {
    findViewById(R.id.select_cut).setOnClickListener((v) -> {
      isCut = true;
      userStatus.change(pasteState);
    });
    findViewById(R.id.select_copy).setOnClickListener((v) -> {
      isCopy = true;
      userStatus.change(pasteState);
    });

    findViewById(R.id.select_rename).setOnClickListener((v) -> {
      ((EditText) renameDialog.findViewById(R.id.new_name)).setText(currentSelectedFiles.get(0).getName());
      renameDialog.show();
    });

    findViewById(R.id.select_delete).setOnClickListener((v) -> {
      StringBuilder list = new StringBuilder("Delete the following files/folders?\n");
      for (int i = 0; i < currentSelectedFiles.size(); i++) {
        list.append(currentSelectedFiles.get(i).getName());
        if (i < currentSelectedFiles.size() - 1) {
          list.append(",\n");
        }
      }
      ((TextView) deleteConfirmationDialog.findViewById(R.id.delete_list)).setText(list.toString());
      deleteConfirmationDialog.show();
    });
    findViewById(R.id.select_cancel).setOnClickListener((v) -> userStatus.change(idleState));

    findViewById(R.id.select_all)
      .setOnClickListener(v -> forEachItem(item -> {
          if (!currentSelectedFiles.contains(item.file)) {
            selectFiles(item.file, item);
          }
        })
      );

    findViewById(R.id.invert_select_all)
      .setOnClickListener(v -> forEachItem(item -> selectFiles(item.file, item)));
  }

  private void setupPasteMenu() {
    findViewById(R.id.paste_paste)
      .setOnClickListener(
        (v) -> new Thread(() -> {
          final int currentOperation = hasOperations + 1;
          hasOperations = currentOperation;
          runOnUiThread(() -> {
            if (findViewById(ONGOING_OPERATION_ID) != null)
              findViewById(ONGOING_OPERATION_ID).setVisibility(View.VISIBLE);
          });
          final ArrayList<File> selectedFiles = new ArrayList<>(currentSelectedFiles.size());
          selectedFiles.addAll(currentSelectedFiles);
          runOnUiThread(() -> userStatus.change(idleState));
          if (isCut) {
            for (int i = 0; i < selectedFiles.size(); i++) {
              File file = selectedFiles.get(i);
              FileOperation.move(file, new File(filePath, file.getName()));
            }
          } else if (isCopy) {
            for (int i = 0; i < selectedFiles.size(); i++) {
              final File file = selectedFiles.get(i);
              FileOperation.copy(file, new File(filePath, file.getName()));
            }
          }

          if (hasOperations == currentOperation)
            hasOperations = 0;
          runOnUiThread(() -> {
            if (findViewById(ONGOING_OPERATION_ID) != null)
              findViewById(ONGOING_OPERATION_ID).setVisibility(View.GONE);
          });
          selectedFiles.clear();
          executor.execute(() -> listItem(new File(filePath)));
        }).start());
    findViewById(R.id.paste_cancel).setOnClickListener((v) -> userStatus.change(idleState));
  }

  private void setupOpenListDialogue() {
    openListDialog = new Dialog(this, R.style.app_theme_dialog);
    openListDialog.setContentView(R.layout.open_list);
    openListDialog.findViewById(R.id.open_list_open).setOnClickListener((v) -> {
      openListDialog.dismiss();
      fopen.open(currentSelectedFiles.get(0));
      userStatus.change(idleState);
    });
    if (fopen.isRequestDocument){
      openListDialog.findViewById(R.id.open_list_share).setVisibility(View.GONE);
    } else {
      openListDialog.findViewById(R.id.open_list_share).setOnClickListener((v) -> {
        openListDialog.dismiss();
        fopen.share(currentSelectedFiles.get(0));
        userStatus.change(idleState);
      });
    }
    openListDialog.findViewById(R.id.open_list_cancel).setOnClickListener((v) -> {
      openListDialog.dismiss();
      userStatus.change(idleState);
    });
    openListDialog.setOnCancelListener((d) -> userStatus.change(idleState));
  }

  private void setupRenameDialog() {
    renameDialog = new Dialog(this, R.style.app_theme_dialog);
    renameDialog.setTitle("Rename File/Folder");
    renameDialog.setContentView(R.layout.rename_confirmation);
    renameDialog.findViewById(R.id.rename_rename)
      .setOnClickListener(
        (v) -> {
          renameDialog.dismiss();
          String name = ((EditText) renameDialog.findViewById(R.id.new_name)).getText().toString();
          File src = currentSelectedFiles.get(0);
          File dst = new File(src.getParent(), name);
          FileOperation.move(src, dst);
          userStatus.change(idleState);
          executor.execute(() -> listItem(new File(filePath)));
        }
      );
    renameDialog.findViewById(R.id.rename_cancel)
      .setOnClickListener(
        (v) -> {
          renameDialog.dismiss();
          userStatus.change(idleState);
        }
      );
  }

  private void setupDeleteDialog() {
    deleteConfirmationDialog = new Dialog(this, R.style.app_theme_dialog);
    deleteConfirmationDialog.setCancelable(false);
    deleteConfirmationDialog.setTitle("Confirm delete:");
    deleteConfirmationDialog.setContentView(R.layout.delete_comfirmation);
    deleteConfirmationDialog.findViewById(R.id.delete_delete).setOnClickListener((v) -> {
      deleteConfirmationDialog.dismiss();
      new Thread(() -> {
        runOnUiThread(() -> {
          if (findViewById(ONGOING_OPERATION_ID) != null)
            findViewById(ONGOING_OPERATION_ID).setVisibility(View.VISIBLE);
        });
        final int currentOperation = hasOperations + 1;
        hasOperations = currentOperation;
        ArrayList<File> selectedFiles = new ArrayList<>(currentSelectedFiles.size());
        selectedFiles.addAll(currentSelectedFiles);
        runOnUiThread(() -> userStatus.change(idleState));
        for (int i = 0; i < selectedFiles.size(); i++)
          FileOperation.delete(selectedFiles.get(i));
        if (hasOperations == currentOperation)
          hasOperations = 0;
        runOnUiThread(() -> {
          if (findViewById(ONGOING_OPERATION_ID) != null)
            findViewById(ONGOING_OPERATION_ID).setVisibility(View.GONE);
        });
        executor.execute(() -> listItem(new File(filePath)));
      }).start();
    });
    deleteConfirmationDialog.findViewById(R.id.delete_cancel).setOnClickListener((v) -> {
      deleteConfirmationDialog.dismiss();
      userStatus.change(idleState);
    });
  }

  private void setupCreateDirectoryDialog() {
    createDirectoryDialog = new Dialog(this, R.style.app_theme_dialog);
    createDirectoryDialog.setTitle("Create new folder");
    createDirectoryDialog.setContentView(R.layout.new_directory);
    createDirectoryDialog.findViewById(R.id.new_directory_cancel).setOnClickListener((v) -> {
      createDirectoryDialog.dismiss();
    });
    createDirectoryDialog.findViewById(R.id.new_directory_create).setOnClickListener((v) -> {
      String name = ((EditText) createDirectoryDialog.findViewById(R.id.new_directory_name)).getText().toString();
      new File(filePath, name).mkdirs();
      createDirectoryDialog.dismiss();
      executor.execute(() -> listItem(new File(filePath)));
    });
  }

  private void setupBitmaps() {
    width = getSystem().getDisplayMetrics().widthPixels;
    folderImage = BitmapFactory.decodeResource(getResources(),
      R.drawable.folder);
    fileImage = BitmapFactory.decodeResource(getResources(),
      R.drawable.file);
    archiveImage = BitmapFactory.decodeResource(getResources(),
      R.drawable.archive);
    audioImage = BitmapFactory.decodeResource(getResources(),
      R.drawable.audio);
    videoImage = BitmapFactory.decodeResource(getResources(),
      R.drawable.video);
    pictureImage = BitmapFactory.decodeResource(getResources(),
      R.drawable.picture);
    unknownImage = BitmapFactory.decodeResource(getResources(),
      R.drawable.unknown);
  }

  private void forEachItem(ForEachItemFunction forEachItemFunction) {
    View v;
    for (int i = ll.getChildCount() - 1; i >= 0; i--) {
      v = ll.getChildAt(i);
      if (v instanceof Item)
        forEachItemFunction.forEachItem((Item) v);
    }
  }

  private interface ForEachItemFunction {
    void forEachItem(Item item);

  }

  private class Status {

    void start() {
    }

    public void change(Status status) {
      userStatus = status;
      userStatus.start();
    }
  }

  private class IdleState extends Status {

    @Override
    public void start() {
      findViewById(R.id.paste_operation).setVisibility(View.GONE);
      findViewById(R.id.select_operation).setVisibility(View.GONE);
      findViewById(R.id.quick_selection).setVisibility(View.GONE);
      clearHighlight();
      currentSelectedFiles.clear();
    }
  }

  private class SelectState extends Status {
    @Override
    public void start() {
      findViewById(R.id.paste_operation).setVisibility(View.GONE);
      findViewById(R.id.select_operation).setVisibility(View.VISIBLE);
      findViewById(R.id.quick_selection).setVisibility(View.VISIBLE);
    }
  }

  private class PasteState extends Status {
    @Override
    public void start() {
      findViewById(R.id.select_operation).setVisibility(View.GONE);
      findViewById(R.id.quick_selection).setVisibility(View.GONE);
      findViewById(R.id.paste_operation).setVisibility(View.VISIBLE);
    }
  }

  private class OpenFileState extends Status {
  }

  private class Item extends LinearLayout {
    final File file;

    private Item(final ImageView imageView, final File file, final Context context) {
      super(context);

      this.file = file;
      this.setClickable(true);
      this.setOnClickListener(shortTabOnButton);
      this.setOnLongClickListener(longPressOnButton);

      final TextView textView = new TextView(context);
      textView.setText(file.getName());
      textView.setTextColor(Color.WHITE);
      textView.setBackgroundColor(Color.TRANSPARENT);
      textView.setGravity(CENTER_VERTICAL);
      textView.setWidth(width - (width / 8));
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); //material list title

      this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
      runOnUiThread(() -> {
        this.addView(imageView);
        this.addView(textView);
        ll.addView(this);
      });
    }
  }

  private class ShortTabOnButton implements View.OnClickListener {
    @Override
    public void onClick(final View view) {
      final File folder = ((Item) view).file;
      if (!checkPermission())
        return;

      if (folder.isDirectory()) {
        if (userStatus == selectState) {
          selectFiles(folder, view);
        } else {
          executor.execute(() -> listItem(folder));
        }
      }

      if (folder.isFile()) {
        if (userStatus == idleState) {
          userStatus.change(openFileState);

          currentSelectedFiles.clear();
          currentSelectedFiles.add(folder);

          openListDialog.setTitle(folder.getName());
          openListDialog.show();

        } else if (userStatus == selectState) {
          selectFiles(folder, view);
        }
      }
    }
  }

  private class LongPressOnButton implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(final View view) {
      if (fopen.isRequestDocument) return true;
      if (userStatus == idleState || userStatus == selectState) {
        userStatus.change(selectState);
        final File folder = ((Item) view).file;
        selectFiles(folder, view);
      }
      return true;
    }
  }
}