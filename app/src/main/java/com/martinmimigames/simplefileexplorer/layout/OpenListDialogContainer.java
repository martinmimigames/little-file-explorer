package com.martinmimigames.simplefileexplorer.layout;

import android.app.Activity;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.LinearLayout;
import com.martinmimigames.simplefileexplorer.R;

public class OpenListDialogContainer {

    public final LinearLayout base;

    public final Button open;

    public final Button details;

    public final Button share;

    public final Button cancel;

    public OpenListDialogContainer(Activity activity) {

        var wrapContent = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapContent.setMargins(10, 10, 10, 10);

        base = new LinearLayout(activity);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setLayoutParams(wrapContent);

        open = new Button(new ContextThemeWrapper(activity, R.style.buttonStyle));
        open.setLayoutParams(wrapContent);
        open.setText("Open");
        open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        base.addView(open);

        details = new Button(new ContextThemeWrapper(activity, R.style.buttonStyle));
        details.setLayoutParams(wrapContent);
        details.setText("Details");
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        base.addView(details);

        share = new Button(new ContextThemeWrapper(activity, R.style.buttonStyle));
        share.setLayoutParams(wrapContent);
        share.setText("Share");
        share.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        base.addView(share);

        cancel = new Button(new ContextThemeWrapper(activity, R.style.buttonStyle));
        cancel.setLayoutParams(wrapContent);
        cancel.setText("Cancel");
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        base.addView(cancel);

    }
}
