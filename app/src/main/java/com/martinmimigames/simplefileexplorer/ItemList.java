package com.martinmimigames.simplefileexplorer;

import android.app.Activity;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class ItemList implements ListAdapter {

  static ArrayList<View> items;
  DataSetObserver observer;

  Activity activity;
  AbsListView absListView;

  TextView placeholder;

  public ItemList(Activity activity) {
    items = new ArrayList<>(10);
    this.activity = activity;
  }

  void clearWithNewSize(int size) {
    removeAll();
    items = new ArrayList<>(size);
  }

  public void setListView(AbsListView absListView) {
    this.absListView = absListView;
  }

  public void add(View item) {
    activity.runOnUiThread(() -> items.add(item));
    onChanged();
  }

  public void removeAll() {
    activity.runOnUiThread(items::clear);
    onChanged();
  }

  void removeView(View view) {
    activity.runOnUiThread(() -> items.remove(view));
  }

  public View get(int position) {
    return items.get(position);
  }

  public void onChanged() {
    if (observer != null)
      activity.runOnUiThread(observer::onChanged);
  }

  @Override
  public boolean areAllItemsEnabled() {
    return false;
  }

  @Override
  public boolean isEnabled(int position) {
    return items.get(position) instanceof MainActivity.Item;
  }

  @Override
  public void registerDataSetObserver(DataSetObserver observer) {
    this.observer = observer;
  }

  @Override
  public void unregisterDataSetObserver(DataSetObserver observer) {
    if (observer == this.observer) {
      this.observer = null;
    }
  }

  @Override
  public int getCount() {
    return items.size();
  }

  @Override
  public Object getItem(int position) {
    return items.get(position);
  }

  @Override
  public long getItemId(int position) {
    return items.get(position).getId();
  }

  @Override
  public boolean hasStableIds() {
    return false;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    return items.get(position);
  }

  @Override
  public int getItemViewType(int position) {
    return 1;
  }

  @Override
  public int getViewTypeCount() {
    return 2;
  }

  @Override
  public boolean isEmpty() {
    return items.size() == 0;
  }
}
