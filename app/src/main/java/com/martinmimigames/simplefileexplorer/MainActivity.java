package com.martinmimigames.simplefileexplorer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import com.martinmimigames.simplefileexplorer.layout.DeleteConfirmationDialogContainer;
import com.martinmimigames.simplefileexplorer.layout.DetailsDialogContainer;
import com.martinmimigames.simplefileexplorer.layout.OpenListDialogContainer;
import com.martinmimigames.simplefileexplorer.view.ItemView;
import mg.utils.clipboard.v1.ClipBoard;
import mg.utils.helper.MainThread;
import mg.utils.notify.ToastHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.*;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends Activity {
    private static final int ONGOING_OPERATION_ID = Integer.MAX_VALUE;
    private static final int LOADING_VIEW_ID = Integer.MAX_VALUE - 1;
    private static final int HIGHLIGHT_COLOR = 0xFF00A4C9;
    private final ShortTabOnButton shortTabOnButton;
    private final LongPressOnButton longPressOnButton;
    private final ArrayList<File> currentSelectedFiles;
    private final ExecutorService executor;
    private final FileOpener fopen;
    private final AppState currentState;

    private boolean allowHiddenFileDisplay;
    private int hasOperations;
    private LinearLayout mainListView;
    private int width;
    private boolean isCutElseCopy;
    private PermissionManager permissionManager;
    private Dialog openListDialog;
    private Dialog deleteConfirmationDialog;
    private DeleteConfirmationDialogContainer deleteConfirmationDialogContainer;
    private Dialog createDirectoryDialog;
    private Dialog renameDialog;
    private Dialog detailsDialog;
    private final Preferences preferences;
    private ConcurrentManager concurrentManager;

    public MainActivity() {
        concurrentManager = new ConcurrentManager();
        shortTabOnButton = new ShortTabOnButton();
        longPressOnButton = new LongPressOnButton();
        currentSelectedFiles = new ArrayList<>(3);
        executor = Executors.newCachedThreadPool();
        fopen = new FileOpener(this);
        currentState = new AppState();

        preferences = new Preferences();

        hasOperations = 0;
    }

    // For easier concurrent processes cancelling
    // no need for custom implementation
    private final class ConcurrentManager {
        FutureTask<Boolean> listFiles;
        FutureTask<Void> listFilesAwaitTermination;
        Future<?> md5Calculator;
    }

    private void setupUI() {
        setupTopBar();
        setupMenu();
        setupSelectMenu();
        setupSingleSelectMenu();
        setupPasteMenu();
        setupOpenListDialogue();
        setupRenameDialog();
        setupDeleteDialog();
        setupCreateDirectoryDialog();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentState.filePath = savedInstanceState.getString(Preferences.FILE_PATH_KEY);
        }

        preferences.onCreate(this);

        var save = preferences.getSharedPreferences();
        allowHiddenFileDisplay = save.getBoolean(Preferences.TOGGLE_HIDDEN_KEY, false);
        currentState.sorterName = save.getString(Preferences.SORTER_KEY, AppState.Sorters.ASCENDING_NAME_SORTER_TAG);
        currentState.theme = save.getInt(Preferences.THEME_KEY, (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) ? AppState.Theme.SYSTEM_THEME : AppState.Theme.DARK_THEME);

        setTheme(getCurrentTheme());

        setContentView(R.layout.activity_main);

        permissionManager = new PermissionManager(this);

        Intent intent = getIntent();
        switch (intent.getAction()) {
            case Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT -> {
                fopen.isRequestDocument = true;
                setResult(RESULT_CANCELED);
            }
            default -> fopen.isRequestDocument = false;
        }

        mainListView = findViewById(R.id.list);

        width = Resources.getSystem().getDisplayMetrics().widthPixels;
        setupUI();

        // default app state is idle state
        currentState.changeTo(AppState.Mode.IDLE);
    }

    private int getCurrentTheme() {
        int theme = currentState.theme;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO && currentState.theme == AppState.Theme.SYSTEM_THEME) {
            theme = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        }
        return (theme == AppState.Theme.LIGHT_THEME) ? R.style.app_theme_light : R.style.app_theme_default;
    }

    private int getCurrentDialogTheme() {
        int theme = currentState.theme;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO && currentState.theme == AppState.Theme.SYSTEM_THEME) {
            theme = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        }
        return (theme == AppState.Theme.LIGHT_THEME) ? R.style.app_theme_dialog_light : R.style.app_theme_dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString(Preferences.FILE_PATH_KEY, currentState.filePath);
    }

    @Override
    protected void onStart() {
        super.onStart();

        executor.execute(() -> {
            if (currentState.filePath != null && new File(currentState.filePath).exists()) {
                if (checkPermission())
                    listItem(currentState.filePath);
            } else {
                for (File folder : FileOperation.getAllStorages(this)) {
                    if (checkPermission()) runOnUiThread(() -> listItem(folder));
                    break;
                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        currentState.changeTo(AppState.Mode.IDLE);

        var saveEditor = preferences.getSharedPreferences().edit();
        saveEditor.putBoolean(Preferences.TOGGLE_HIDDEN_KEY, allowHiddenFileDisplay);
        saveEditor.putString(Preferences.FILE_PATH_KEY, currentState.filePath);

        saveEditor.putString(Preferences.SORTER_KEY, currentState.sorterName);
        saveEditor.putInt(Preferences.THEME_KEY, currentState.theme);

        saveEditor.commit();
    }

    private boolean checkPermission() {

        permissionManager.getPermission(READ_EXTERNAL_STORAGE, "Storage access is required", false);
        permissionManager.getPermission(WRITE_EXTERNAL_STORAGE, "Storage access is required", false);

        return permissionManager.havePermission(new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE});
    }

    private void listItem(String path) {
        listItem(new File(path));
    }

    /**
     * flag for calling a directory list update
     */
    private boolean needDirectoryUpdate;

    /**
     * Update the directory list, displaying the folder provided.
     *
     * @param folder the folder to display contents of
     */
    private boolean updateDirectoryView(File folder) {

        runOnUiThread(() -> {
            mainListView.removeAllViews();
            ((TextView) findViewById(R.id.title)).setText(currentState.filePath);

            // create process indicator
            addIdDialog("Loading...", 16, LOADING_VIEW_ID);
            addIdDialog("cutting/copying/deleting files...", 16, ONGOING_OPERATION_ID);
            if (hasOperations == 0)
                setViewVisibility(ONGOING_OPERATION_ID, View.GONE);
        });

        // update variables for current path and parent
        currentState.parent = folder.getParentFile();
        currentState.filePath = folder.getPath();

        if (folderAccessible(folder)) {
            final File[] items = folder.listFiles((file) -> allowHiddenFileDisplay || !file.isHidden());

            assert items != null;

            Arrays.sort(items, switch (currentState.sorterName) {
                case AppState.Sorters.DESCENDING_NAME_SORTER_TAG -> AppState.Sorters.DESCENDING_NAME;
                case AppState.Sorters.ASCENDING_MODIFIED_TIME_SORTER_TAG -> AppState.Sorters.ASCENDING_MODIFIED_TIME;
                case AppState.Sorters.DESCENDING_MODIFIED_TIME_SORTER_TAG -> AppState.Sorters.DESCENDING_MODIFIED_TIME;
                case AppState.Sorters.ASCENDING_FILE_SIZE_TAG -> AppState.Sorters.ASCENDING_FILE_SIZE;
                case AppState.Sorters.DESCENDING_FILE_SIZE_TAG -> AppState.Sorters.DESCENDING_FILE_SIZE;
                //case AppState.Sorters.ASCENDING_NAME_SORTER_TAG -> AppState.Sorters.ASCENDING_NAME;
                default -> AppState.Sorters.ASCENDING_NAME;
            });

            if (items.length == 0) {
                addDialog("Empty folder!", 16);
            } else {

                // print folders items
                boolean hasFolders = false;
                for (File item : items) {
                    if (item.isDirectory()) {
                        if (!hasFolders) {
                            addDialog("", 10);
                            hasFolders = true;
                        }
                        // if need to update, immediate return to avoid unwanted writing the list from concurrency
                        if (Thread.currentThread().isInterrupted()) return false;

                        addDirectory(item);
                    }
                }

                // print file items
                boolean hasFiles = false;
                for (File item : items)
                    if (item.isFile()) {
                        if (!hasFiles) {
                            addDialog("", 10);
                            hasFiles = true;
                        }
                        // if need to update, immediate return to avoid unwanted writing the list from concurrency
                        if (Thread.currentThread().isInterrupted()) return false;

                        switch (FileProvider.getFileType(item).split("/")[0]) {
                            case "image" -> addItem(getImageView(R.drawable.picture), item);
                            case "video" -> addItem(getImageView(R.drawable.video), item);
                            case "audio" -> addItem(getImageView(R.drawable.audio), item);
                            case "application" -> {
                                if (FileProvider.getFileType(item).contains("application/octet-stream"))
                                    addItem(getImageView(R.drawable.unknown), item);
                                else
                                    addItem(getImageView(R.drawable.archive), item);
                            }
                            case "text" -> addItem(getImageView(R.drawable.file), item);
                            default -> addItem(getImageView(R.drawable.unknown), item);
                        }
                    }

                addDialog("", 10);

                // retrieve folder and driver information
                if (Build.VERSION.SDK_INT >= 9) {
                    StatFs stat = new StatFs(folder.getPath());
                    long bytesAvailable = Build.VERSION.SDK_INT >= 18 ?
                            stat.getBlockSizeLong() * stat.getAvailableBlocksLong() :
                            (long) stat.getBlockSize() * stat.getAvailableBlocks();
                    String info = "Available size: " + FileOperation.getReadableMemorySize(bytesAvailable);
                    if (Build.VERSION.SDK_INT >= 18) {
                        bytesAvailable = stat.getTotalBytes();
                        info += "\nCapacity size: " + FileOperation.getReadableMemorySize(bytesAvailable);
                    }
                    // add slight padding
                    final String finalInfo = info + "\n";
                    addDialog(finalInfo, 16);
                }

                filter();

            }


        } else {
            // apps are not allowed to access these folders due to permissions in Android 11
            if (currentState.filePath.contains("Android/data") || currentState.filePath.contains("Android/obb")) {
                addDialog("For android 11 or higher, Android/data and Android/obb is refused access.\n", 16);
            } else addDialog("Access Denied", 16);
        }

        runOnUiThread(() -> mainListView.removeView(findViewById(LOADING_VIEW_ID)));
        return true;
    }

    /**
     * filter through each item using filter text entered in filter bar
     */
    private void filter() {
        var filter = ((TextView) findViewById(R.id.filter)).getText().toString().toLowerCase();
        runOnUiThread(() -> {
            forEachItem(item -> item.setVisibility(item.file.getName().toLowerCase().contains(filter) ? View.VISIBLE : View.GONE));
        });
    }

    private void listItem(final File folder) {
        if (!folder.getPath().equals(currentState.filePath)) {
            ((TextView) findViewById(R.id.filter)).setText("");
        }
        // update flag, helps avoid concurrent list update
        if (concurrentManager.listFiles != null && concurrentManager.listFilesAwaitTermination != null) {
            concurrentManager.listFiles.cancel(true);
            try {
                concurrentManager.listFilesAwaitTermination.get();
            } catch (ExecutionException | InterruptedException ignored) {
            }
        }

        // wrapped to allow awaiting termination
        concurrentManager.listFiles = new FutureTask<>(() -> updateDirectoryView(folder));
        concurrentManager.listFilesAwaitTermination = new FutureTask<>(concurrentManager.listFiles, null);
        executor.execute(concurrentManager.listFilesAwaitTermination);
    }

    private void addIdDialog(final String dialog, final int textSize, final int id) {
        final TextView tv = new TextView(this);
        tv.setText(dialog);
        tv.setBackgroundColor(Color.TRANSPARENT);
        tv.setTextSize(textSize);
        tv.setId(id);

        runOnUiThread(() -> mainListView.addView(tv));
    }

    private void addDialog(final String dialog, final int textSize) {
        addIdDialog(dialog, textSize, View.NO_ID);
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
        addItem(getImageView(R.drawable.folder), folder);
    }

    private void addItem(final ImageView imageView, final File file) {
        new ItemView(this, mainListView, imageView, file, width, shortTabOnButton, longPressOnButton);
    }

    private ImageView getImageView(int imageId) {
        final ImageView imageView = new ImageView(this);
        imageView.setImageResource(imageId);
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
        switch (currentSelectedFiles.size()) {
            case 1 -> {
                currentState.changeTo(AppState.Mode.SELECT);
                setViewVisibility(R.id.single_select_operation, View.VISIBLE);
            }
            case 0 -> currentState.changeTo(AppState.Mode.IDLE);
            default -> setViewVisibility(R.id.single_select_operation, View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (!returnToParent())
            super.onBackPressed();
    }

    /**
     * tries to go back to a parent folder
     *
     * @return false if failed, else true
     */
    boolean returnToParent() {
        if (currentState.parent == null || !folderAccessible(currentState.parent))
            return false;
        listItem(currentState.parent);
        return true;
    }

    private void setupTopBar() {
        findViewById(R.id.back_button).setOnClickListener(v -> returnToParent());

        findViewById(R.id.menu_button).setOnClickListener(v -> {
            final View menuList = findViewById(R.id.menu_list);
            if (menuList.isShown()) {
                menuList.setVisibility(View.GONE);
                if (currentState.mode == AppState.Mode.SELECT)
                    setViewVisibility(R.id.quick_selection, View.VISIBLE);
            } else {
                menuList.setVisibility(View.VISIBLE);
                setViewVisibility(R.id.quick_selection, View.GONE);
            }
        });

        ((EditText) findViewById(R.id.filter)).addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                filter();
            }
        });
    }

    private void setupMenu() {
        setViewVisibility(R.id.menu_list, View.GONE);

        ((TextView) findViewById(R.id.menu_toggle_theme)).setText("Theme: " + switch (currentState.theme) {
            case AppState.Theme.LIGHT_THEME -> "Light";
            case AppState.Theme.SYSTEM_THEME -> "System";
            default -> "Dark";
        });
        findViewById(R.id.menu_toggle_theme)
                .setOnClickListener(v -> {
                    currentState.theme = switch (currentState.theme) {
                        case AppState.Theme.LIGHT_THEME -> AppState.Theme.DARK_THEME;
                        case AppState.Theme.DARK_THEME ->
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) ? AppState.Theme.SYSTEM_THEME : AppState.Theme.LIGHT_THEME;
                        default -> AppState.Theme.LIGHT_THEME;
                    };

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        setTheme(currentState.theme == AppState.Theme.LIGHT_THEME ? R.style.app_theme_light : R.style.app_theme_default);
                        recreate();
                    } else {
                        ToastHelper.showShort(this, "relaunch app to apply");
                    }
                });

        findViewById(R.id.menu_toggle_hidden)
                .setOnClickListener(v -> {
                    allowHiddenFileDisplay = !allowHiddenFileDisplay;
                    listItem(currentState.filePath);
                    setViewVisibility(R.id.menu_list, View.GONE);
                });

        findViewById(R.id.menu_sorter)
                .setOnClickListener(v -> {
                    currentState.sorterName = switch (currentState.sorterName) {
                        case AppState.Sorters.ASCENDING_NAME_SORTER_TAG -> AppState.Sorters.DESCENDING_NAME_SORTER_TAG;
                        case AppState.Sorters.DESCENDING_NAME_SORTER_TAG ->
                                AppState.Sorters.ASCENDING_MODIFIED_TIME_SORTER_TAG;
                        case AppState.Sorters.ASCENDING_MODIFIED_TIME_SORTER_TAG ->
                                AppState.Sorters.DESCENDING_MODIFIED_TIME_SORTER_TAG;
                        case AppState.Sorters.DESCENDING_MODIFIED_TIME_SORTER_TAG ->
                                AppState.Sorters.ASCENDING_FILE_SIZE_TAG;
                        case AppState.Sorters.ASCENDING_FILE_SIZE_TAG -> AppState.Sorters.DESCENDING_FILE_SIZE_TAG;
                        // case AppState.Sorters.DESCENDING_FILE_SIZE_TAG -> AppState.Sorters.ASCENDING_NAME_SORTER_TAG;
                        default -> AppState.Sorters.ASCENDING_NAME_SORTER_TAG;
                    };
                    ((TextView) v).setText(currentState.sorterName);
                    listItem(currentState.filePath);
                });
        ((TextView) findViewById(R.id.menu_sorter)).setText(currentState.sorterName);

        findViewById(R.id.menu_create_new_directory)
                .setOnClickListener(v -> {
                    createDirectoryDialog.show();
                    setViewVisibility(R.id.menu_list, View.GONE);
                });

        // ensure drive list is not visible by default
        // visibility state is required
        final LinearLayout storageList = findViewById(R.id.drive_list);
        storageList.setVisibility(View.GONE);

        findViewById(R.id.menu_quick_switch)
                .setOnClickListener(v -> {
                    setViewVisibility(R.id.menu_list, View.GONE);

                    // update storage list

                    storageList.removeAllViews();
                    var storages = FileOperation.getAllStorages(this);
                    for (var storage : storages) {
                        // skip unreadable folders
                        if (!storage.canRead()) continue;

                        var entry = new Button(this);
                        entry.setLayoutParams(
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                        );
                        entry.setText(storage.getPath());
                        entry.setOnClickListener(v2 -> {
                            listItem(storage);
                            setViewVisibility(R.id.drive_list, View.GONE);
                        });
                        storageList.addView(entry);
                    }

                    storageList.setVisibility((storageList.isShown()) ? View.GONE : View.VISIBLE);
                });
    }

    private void setupSingleSelectMenu() {
        findViewById(R.id.single_select_copy_path).setOnClickListener(v -> {
            copyFilePath();
            currentState.changeTo(AppState.Mode.IDLE);
        });

        findViewById(R.id.single_select_rename).setOnClickListener(v -> {
            ((EditText) renameDialog.findViewById(R.id.new_name)).setText(currentSelectedFiles.get(0).getName());
            renameDialog.show();
        });
    }

    private void setupSelectMenu() {
        findViewById(R.id.select_cut).setOnClickListener(v -> {
            isCutElseCopy = true;
            currentState.changeTo(AppState.Mode.PASTE);
        });
        findViewById(R.id.select_copy).setOnClickListener(v -> {
            isCutElseCopy = false;
            currentState.changeTo(AppState.Mode.PASTE);
        });

        findViewById(R.id.select_delete).setOnClickListener(v -> {
            StringBuilder list = new StringBuilder("Proceed with delete?\n");
            for (int i = 0; i < currentSelectedFiles.size(); i++) {
                list.append(currentSelectedFiles.get(i).getName());
                if (i < currentSelectedFiles.size() - 1) {
                    list.append(",\n");
                }
            }
            deleteConfirmationDialogContainer.deleteList.setText(list.toString());
            deleteConfirmationDialog.show();
        });
        findViewById(R.id.select_cancel).setOnClickListener(v -> currentState.changeTo(AppState.Mode.IDLE));

        findViewById(R.id.select_all)
                .setOnClickListener(v -> forEachItem(item -> {
                            if (!currentSelectedFiles.contains(item.file)) {
                                selectFiles(item.file, item);
                            }
                        })
                );

        findViewById(R.id.invert_select_all)
                .setOnClickListener(v -> forEachItem(item -> selectFiles(item.file, item)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT) {
            findViewById(R.id.share_selected)
                    .setOnClickListener(v -> {
                        fopen.share(getSelectedFiles());
                        currentState.changeTo(AppState.Mode.IDLE);
                    });
        } else {
            setViewVisibility(R.id.share_selected, View.GONE);
        }
    }

    private void setupPasteMenu() {
        findViewById(R.id.paste_paste)
                .setOnClickListener(v -> executor.execute(() -> {
                    final int currentOperation = hasOperations + 1;
                    hasOperations = currentOperation;
                    if (findViewById(ONGOING_OPERATION_ID) != null)
                        setViewVisibility(ONGOING_OPERATION_ID, View.VISIBLE);

                    var selectedFiles = getSelectedFiles();
                    var dst = currentState.filePath;

                    currentState.changeTo(AppState.Mode.IDLE);

                    if (isCutElseCopy) {
                        for (var file : selectedFiles) {
                            FileOperation.move(file, new File(dst, file.getName()));
                        }
                    } else {
                        for (var file : selectedFiles) {
                            FileOperation.copy(file, new File(dst, file.getName()));
                        }
                    }

                    if (hasOperations == currentOperation)
                        hasOperations = 0;
                    if (findViewById(ONGOING_OPERATION_ID) != null)
                        setViewVisibility(ONGOING_OPERATION_ID, View.GONE);

                    listItem(currentState.filePath);
                }));
        findViewById(R.id.paste_cancel).setOnClickListener(v -> currentState.changeTo(AppState.Mode.IDLE));
    }

    private void setViewVisibility(int id, int visibility) {
        runOnUiThread(() -> findViewById(id).setVisibility(visibility));
    }

    /**
     * Copies the selected file's file path and place in clipboard.
     */
    private void copyFilePath() {
        ClipBoard.copyText(this, currentSelectedFiles.get(0).getAbsolutePath());
        ToastHelper.showShort(this, "copied");
        currentState.changeTo(AppState.Mode.IDLE);
    }

    private void setupOpenListDialogue() {
        openListDialog = new Dialog(this, getCurrentDialogTheme());
        var openListDialogContainer = new OpenListDialogContainer(this);
        openListDialog.setContentView(openListDialogContainer.base);
        openListDialogContainer.open.setOnClickListener(v -> {
            openListDialog.dismiss();
            fopen.open(currentSelectedFiles.get(0));
            currentState.changeTo(AppState.Mode.IDLE);
        });

        detailsDialog = new Dialog(this, getCurrentDialogTheme());
        var detailsDialogContainer = new DetailsDialogContainer(this);
        detailsDialog.setContentView(detailsDialogContainer.base);
        detailsDialog.setOnCancelListener(d -> {
            detailsDialog.dismiss();
            concurrentManager.md5Calculator.cancel(true);
        });

        openListDialogContainer.details.setOnClickListener(v -> {
            openListDialog.dismiss();
            currentState.changeTo(AppState.Mode.IDLE);
            detailsDialog.show();
            var file = currentSelectedFiles.get(0);
            detailsDialog.setTitle("Details: " + file.getName());
            detailsDialogContainer.size.setText("Size: " + FileOperation.getReadableMemorySize(file.length()) + " (" + file.length() + "B)");
            detailsDialogContainer.lastModified.setText("Last modified: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(file.lastModified()));
            detailsDialogContainer.mime.setText("MIME type: " + FileProvider.getFileType(file));
            detailsDialogContainer.md5.setText("MD5: calculating...");
            concurrentManager.md5Calculator = executor.submit(() -> {
                String md5hash = FileOperation.getMD5(this, file);
                // returns null if some error happened or interruption
                if (md5hash == null) return;
                runOnUiThread(() -> {
                    detailsDialogContainer.md5.setText("MD5: " + md5hash);
                    detailsDialogContainer.copyMd5.setOnClickListener((b2) -> {
                        ClipBoard.copyText(this, md5hash);
                        ToastHelper.showShort(this, "copied");
                    });
                    detailsDialogContainer.checkMd5.setOnClickListener((b2) -> {
                        if (ClipBoard.getClipboardText(this).equalsIgnoreCase(md5hash)) {
                            ToastHelper.showShort(this, "MD5 match");
                        } else {
                            ToastHelper.showShort(this, "MD5 mismatch");
                        }
                    });
                });
            });
        });

        openListDialogContainer.share.setOnClickListener(v -> {
            openListDialog.dismiss();
            fopen.share(getSelectedFiles());
            currentState.changeTo(AppState.Mode.IDLE);
        });

        openListDialogContainer.cancel.setOnClickListener(v -> {
            openListDialog.dismiss();
            currentState.changeTo(AppState.Mode.IDLE);
        });
        openListDialog.setOnCancelListener(d -> currentState.changeTo(AppState.Mode.IDLE));
    }

    File[] getSelectedFiles() {
        return currentSelectedFiles.toArray(new File[0]);
    }

    private void setupRenameDialog() {
        renameDialog = new Dialog(this, getCurrentDialogTheme());
        renameDialog.setTitle("Rename File/Folder");
        renameDialog.setContentView(R.layout.rename_confirmation);
        renameDialog.findViewById(R.id.rename_rename)
                .setOnClickListener(
                        v -> {
                            String name = ((EditText) renameDialog.findViewById(R.id.new_name)).getText().toString();
                            File src = currentSelectedFiles.get(0);
                            File dst = new File(src.getParent(), name);
                            if (!FileOperation.move(src, dst)) {
                                ToastHelper.showShort(this, "invalid name");
                                return;
                            }
                            renameDialog.dismiss();
                            currentState.changeTo(AppState.Mode.IDLE);
                            listItem(currentState.filePath);
                        }
                );
        renameDialog.findViewById(R.id.rename_cancel)
                .setOnClickListener(
                        v -> {
                            renameDialog.dismiss();
                            currentState.changeTo(AppState.Mode.IDLE);
                        }
                );
    }

    private void setupDeleteDialog() {
        deleteConfirmationDialog = new Dialog(this, getCurrentDialogTheme());
        deleteConfirmationDialog.setCancelable(false);
        deleteConfirmationDialog.setTitle("Confirm delete:");
        deleteConfirmationDialogContainer = new DeleteConfirmationDialogContainer(this);
        deleteConfirmationDialog.setContentView(deleteConfirmationDialogContainer.base);
        deleteConfirmationDialogContainer.delete.setOnClickListener(v -> {
            deleteConfirmationDialog.dismiss();
            executor.execute(() -> {
                if (findViewById(ONGOING_OPERATION_ID) != null)
                    setViewVisibility(ONGOING_OPERATION_ID, View.VISIBLE);
                final int currentOperation = hasOperations + 1;
                hasOperations = currentOperation;
                var selectedFiles = getSelectedFiles();
                currentState.changeTo(AppState.Mode.IDLE);
                for (var file : selectedFiles)
                    FileOperation.delete(file);
                if (hasOperations == currentOperation)
                    hasOperations = 0;
                if (findViewById(ONGOING_OPERATION_ID) != null)
                    setViewVisibility(ONGOING_OPERATION_ID, View.GONE);
                listItem(currentState.filePath);
            });
        });
        deleteConfirmationDialogContainer.cancel.setOnClickListener(v -> {
            deleteConfirmationDialog.dismiss();
            currentState.changeTo(AppState.Mode.IDLE);
        });
    }

    private void setupCreateDirectoryDialog() {
        createDirectoryDialog = new Dialog(this, getCurrentDialogTheme());
        createDirectoryDialog.setTitle("Create new folder");
        createDirectoryDialog.setContentView(R.layout.new_directory);
        createDirectoryDialog.findViewById(R.id.new_directory_cancel).setOnClickListener(v -> createDirectoryDialog.dismiss());
        createDirectoryDialog.findViewById(R.id.new_directory_create).setOnClickListener(v -> {
            EditText namer = createDirectoryDialog.findViewById(R.id.new_directory_name);
            var name = namer.getText().toString();
            namer.setText("");
            var folder = new File(currentState.filePath, name);
            if (!folder.exists()) {
                // no invalid name for folders
                folder.mkdirs();
                listItem(currentState.filePath);
            } else {
                ToastHelper.showShort(this, "Folder already exists");
            }
            createDirectoryDialog.dismiss();
        });
    }

    /**
     * Loop call function for each entry item stored in mainListView.
     * Must be called in uiThread to avoid threading issues.
     *
     * @param forEachItemFunctionalInterface function to be run against each item in mainListView
     */
    private void forEachItem(ForEachItemFunctionalInterface forEachItemFunctionalInterface) {
        View v;
        for (int i = 0; i < mainListView.getChildCount(); i++) {
            v = mainListView.getChildAt(i);
            if (v instanceof ItemView item)
                forEachItemFunctionalInterface.forEachItem(item);
        }
    }

    /**
     * functional interface for forEachItem
     */
    private interface ForEachItemFunctionalInterface {
        void forEachItem(ItemView item);
    }

    private class AppState {

        private String sorterName;

        private String filePath;
        private File parent;
        private int theme;
        byte mode;

        @TargetApi(Build.VERSION_CODES.FROYO)
        static final class Theme {

            @SuppressLint("InlinedApi")
            static final int LIGHT_THEME = Configuration.UI_MODE_NIGHT_NO;
            @SuppressLint("InlinedApi")
            static final int DARK_THEME = Configuration.UI_MODE_NIGHT_YES;
            @TargetApi(Build.VERSION_CODES.FROYO)
            static final int SYSTEM_THEME = -1;

        }

        static final class Sorters {
            static final Comparator<File> ASCENDING_NAME = (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName());
            static final String ASCENDING_NAME_SORTER_TAG = "A-z";
            static final Comparator<File> DESCENDING_NAME = (f1, f2) -> ASCENDING_NAME.compare(f2, f1);
            static final String DESCENDING_NAME_SORTER_TAG = "z-A";

            static final Comparator<File> DESCENDING_MODIFIED_TIME = (f1, f2) -> Math.toIntExact(Math.max(Math.min(f1.lastModified() - f2.lastModified(), 1), -1));
            static final String DESCENDING_MODIFIED_TIME_SORTER_TAG = "By oldest";
            static final Comparator<File> ASCENDING_MODIFIED_TIME = (f1, f2) -> DESCENDING_MODIFIED_TIME.compare(f2, f1);
            static final String ASCENDING_MODIFIED_TIME_SORTER_TAG = "By earliest";

            static final Comparator<File> DESCENDING_FILE_SIZE = (f1, f2) -> Math.toIntExact(Math.max(Math.min(f2.length() - f1.length(), 1), -1));
            static final String DESCENDING_FILE_SIZE_TAG = "By largest";
            static final Comparator<File> ASCENDING_FILE_SIZE = (f1, f2) -> DESCENDING_FILE_SIZE.compare(f2, f1);
            static final String ASCENDING_FILE_SIZE_TAG = "By smallest";
        }

        static final class Mode {

            static final byte IDLE = 0;
            static final byte SELECT = 1;
            static final byte PASTE = 2;
            static final byte OPEN_FILE = 3;
        }

        void enterIdle() {
            setViewVisibility(R.id.paste_operation, View.GONE);
            setViewVisibility(R.id.select_operation, View.GONE);
            setViewVisibility(R.id.quick_selection, View.GONE);
            setViewVisibility(R.id.single_select_operation, View.GONE);
            forEachItem(item -> item.setBackgroundColor(Color.TRANSPARENT));
            currentSelectedFiles.clear();
        }

        void enterSelect() {
            setViewVisibility(R.id.paste_operation, View.GONE);
            setViewVisibility(R.id.select_operation, View.VISIBLE);
            setViewVisibility(R.id.quick_selection, View.VISIBLE);
            setViewVisibility(R.id.menu_list, View.GONE);
        }

        void enterPaste() {
            setViewVisibility(R.id.select_operation, View.GONE);
            setViewVisibility(R.id.quick_selection, View.GONE);
            setViewVisibility(R.id.paste_operation, View.VISIBLE);
            setViewVisibility(R.id.single_select_operation, View.GONE);
        }

        void enterOpenFile() {
            // empty
        }

        AppState() {
            mode = Mode.IDLE;
        }

        public void changeTo(byte state) {
            mode = state;
            MainThread.run(() -> {
                switch (state) {
                    case Mode.SELECT -> enterSelect();
                    case Mode.PASTE -> enterPaste();
                    case Mode.OPEN_FILE -> enterOpenFile();
                    // case idle -> enterIdle();
                    default -> enterIdle();
                }
            });
        }
    }

    private class ShortTabOnButton implements View.OnClickListener {
        @Override
        public void onClick(final View view) {
            final File file = ((ItemView) view).file;
            if (!checkPermission())
                return;

            if (currentState.mode == AppState.Mode.SELECT) {
                selectFiles(file, view);
            } else /*if (currentState.mode == AppState.Mode.IDLE)*/ {
                if (file.isDirectory()) {
                    listItem(file);
                } else /*if (file.isFile())*/ {
                    currentState.changeTo(AppState.Mode.OPEN_FILE);
                    currentSelectedFiles.clear();
                    currentSelectedFiles.add(file);

                    openListDialog.setTitle(file.getName());
                    openListDialog.show();
                }
            }
        }
    }

    private class LongPressOnButton implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(final View view) {
            if (fopen.isRequestDocument) return true;
            if (currentState.mode == AppState.Mode.IDLE || currentState.mode == AppState.Mode.SELECT) {
                currentState.changeTo(AppState.Mode.SELECT);
                final File file = ((ItemView) view).file;
                selectFiles(file, view);
            }
            return true;
        }
    }
}
