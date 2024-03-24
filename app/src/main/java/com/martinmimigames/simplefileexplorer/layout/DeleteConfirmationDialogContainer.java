package com.martinmimigames.simplefileexplorer.layout;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.martinmimigames.simplefileexplorer.R;

public class DeleteConfirmationDialogContainer {

    public final LinearLayout base;

    public final TextView deleteList;

    public final Button delete;

    public final Button cancel;

    public DeleteConfirmationDialogContainer(Activity activity) {
        base = new LinearLayout(activity);

        base.setOrientation(LinearLayout.VERTICAL);
        base.setPadding(5, 5, 5, 5);
        base.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        base.setGravity(Gravity.BOTTOM);

        var scrollViewSizeLimiter = new LinearLayout(activity);
        scrollViewSizeLimiter.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
                1
        ));

        var wrapContent = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapContent.setMargins(10, 10, 10, 10);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        scrollView.setForegroundGravity(Gravity.TOP);

        deleteList = new TextView(activity);
        deleteList.setLayoutParams(wrapContent);
        deleteList.setPadding(5, 5, 5, 5);

        scrollView.addView(deleteList);
        scrollViewSizeLimiter.addView(scrollView);
        base.addView(scrollViewSizeLimiter);

        delete = new Button(new ContextThemeWrapper(activity, R.style.buttonStyle));
        delete.setLayoutParams(wrapContent);
        delete.setText("Delete");
        base.addView(delete);

        cancel = new Button(new ContextThemeWrapper(activity, R.style.buttonStyle));
        cancel.setLayoutParams(wrapContent);
        cancel.setText("Cancel");
        base.addView(cancel);
    }

}
