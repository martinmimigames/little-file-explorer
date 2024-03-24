package com.martinmimigames.simplefileexplorer.layout;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.martinmimigames.simplefileexplorer.R;

public final class DetailsDialogContainer {

    public final LinearLayout base;

    public final TextView size;

    public final TextView lastModified;

    public final TextView mime;

    public final TextView md5;

    private final LinearLayout buttonContainer;

    public final TextView copyMd5;

    public final TextView checkMd5;

    public DetailsDialogContainer(Activity activity) {
        base = new LinearLayout(activity);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setPadding(10, 10, 10, 10);
        base.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        var wrapContent = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapContent.setMargins(10, 10, 10, 10);

        size = new TextView(activity);
        size.setLayoutParams(wrapContent);
        base.addView(size);
        lastModified = new TextView(activity);
        lastModified.setLayoutParams(wrapContent);
        base.addView(lastModified);
        mime = new TextView(activity);
        mime.setLayoutParams(wrapContent);
        base.addView(mime);
        md5 = new TextView(activity);
        md5.setLayoutParams(wrapContent);
        base.addView(md5);

        buttonContainer = new LinearLayout(activity);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setLayoutParams(wrapContent);

        copyMd5 = new TextView(new ContextThemeWrapper(activity, R.style.buttonStyle));
        copyMd5.setText("copy");
        copyMd5.setLayoutParams(wrapContent);
        buttonContainer.addView(copyMd5);
        checkMd5 = new TextView(new ContextThemeWrapper(activity, R.style.buttonStyle));
        checkMd5.setText("check clipboard");
        checkMd5.setLayoutParams(wrapContent);
        buttonContainer.addView(checkMd5);

        base.addView(buttonContainer);
    }
}
