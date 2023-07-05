package com.martinmimigames.simplefileexplorer.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.martinmimigames.simplefileexplorer.R;
import mg.utils.helper.MainThread;

import java.io.File;

@SuppressLint("ViewConstructor")
public class ItemView extends LinearLayout {
  public final File file;

  public ItemView(Context context, ViewGroup viewGroup, final ImageView imageView, final File file, int itemWidth, OnClickListener onClickListener, OnLongClickListener onLongClickListener) {
    super(context);

    this.file = file;
    this.setClickable(true);
    this.setOnClickListener(onClickListener);
    this.setOnLongClickListener(onLongClickListener);

    final TextView textView = new TextView(context);
    textView.setText(file.getName());
    textView.setBackgroundColor(Color.TRANSPARENT);
    textView.setWidth(itemWidth - (itemWidth / 8));
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); //material list title

    this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    MainThread.run(() -> {
      this.addView(imageView);
      this.addView(textView);
      viewGroup.addView(this);
    });
  }
}
