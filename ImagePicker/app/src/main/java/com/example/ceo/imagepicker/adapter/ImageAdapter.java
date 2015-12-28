package com.example.ceo.imagepicker.adapter;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.example.ceo.imagepicker.R;
import com.example.ceo.imagepicker.utils.ImageLoader;
import com.example.ceo.imagepicker.utils.ImageLoader.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by 123 on 2015/12/25.
 */
public class ImageAdapter extends BaseAdapter {

    private List<String> mImgPaths;
    private String mDirPath;
    private LayoutInflater inflater;
    private final static Set<String> mSelectedImg = new HashSet<>();
    private int mScreenWidth;

    public ImageAdapter(Context context, List<String> mDatas, String dirPath) {

        inflater = LayoutInflater.from(context);
        this.mDirPath = dirPath;
        this.mImgPaths = mDatas;

        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;

    }

    @Override
    public int getCount() {
        return mImgPaths.size();
    }

    @Override
    public Object getItem(int position) {
        return mImgPaths.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        ViewHolder viewHolder = null;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_gridview, parent, false);

            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.id_item_image);
            viewHolder.imageButton = (ImageButton) convertView.findViewById(R.id.id_item_select);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        /**
         *重置状态
         */
        viewHolder.imageView.setImageResource(R.drawable.pictures_no);
        viewHolder.imageButton.setImageResource(R.drawable.picture_unselected);
        viewHolder.imageView.setColorFilter(null);

        viewHolder.imageView.setMaxWidth(mScreenWidth / 3);

        ImageLoader.getInstance(3, Type.LIFO).
                loadImage(mDirPath + "/" + mImgPaths.get(position), viewHolder.imageView);

        final String filepath = mDirPath + "/" + mImgPaths.get(position);
        final ViewHolder finalViewHolder = viewHolder;
        final ViewHolder finalViewHolder1 = viewHolder;
        viewHolder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mSelectedImg.contains(filepath)) {
                    mSelectedImg.remove(filepath);
                    finalViewHolder1.imageView.setColorFilter(null);
                    finalViewHolder1.imageButton.setImageResource(R.drawable.picture_unselected);

                } else {
                    mSelectedImg.add(filepath);
                    finalViewHolder.imageView.setColorFilter(Color.parseColor("#77000000"));
                    finalViewHolder.imageButton.setImageResource(R.drawable.pictures_selected);
                }
            }
        });

        if (mSelectedImg.contains(filepath)) {
            viewHolder.imageView.setColorFilter(Color.parseColor("#77000000"));
            viewHolder.imageButton.setImageResource(R.drawable.pictures_selected);
        }

        return convertView;
    }

    class ViewHolder {
        ImageView imageView;
        ImageButton imageButton;
    }
}
