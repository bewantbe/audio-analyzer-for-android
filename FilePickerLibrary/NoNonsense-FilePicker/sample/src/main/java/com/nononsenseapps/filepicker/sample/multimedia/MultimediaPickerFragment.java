/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.nononsenseapps.filepicker.sample.multimedia;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.nononsenseapps.filepicker.FilePickerFragment;
import com.nononsenseapps.filepicker.sample.R;

import java.io.File;

/**
 * A sample which demonstrates how appropriate methods
 * can be overwritten in order to enable enhanced
 * capabilities, in this case showing thumbnails of images.
 * <p/>
 * I am still listing all files, so I extend from the ready made
 * SD-card browser classes. This allows this class to focus
 * entirely on the image side of things.
 * <p/>
 * To load the image I am using the super great Glide library
 * which only requires a single line of code in this file.
 */
public class MultimediaPickerFragment extends FilePickerFragment {

    // Make sure these do not collide with LogicHandler.VIEWTYPE codes.
    // They are 1-2, so 11 leaves a lot of free space in between.
    private static final int VIEWTYPE_IMAGE_CHECKABLE = 11;
    private static final int VIEWTYPE_IMAGE = 12;

    private static final String[] MULTIMEDIA_EXTENSIONS =
            new String[]{".png", ".jpg", ".gif", ".mp4"};

    /**
     * An extremely simple method for identifying multimedia. This
     * could be improved, but it's good enough for this example.
     *
     * @param file which could be an image or a video
     * @return true if the file can be previewed, false otherwise
     */
    protected boolean isMultimedia(File file) {
        //noinspection SimplifiableIfStatement
        if (isDir(file)) {
            return false;
        }

        String path = file.getPath().toLowerCase();
        for (String ext : MULTIMEDIA_EXTENSIONS) {
            if (path.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Here we check if the file is an image, and if thus if we should create views corresponding
     * to our image layouts.
     *
     * @param position 0 - n, where the header has been subtracted
     * @param file     to check type of
     * @return the viewtype of the item
     */
    @Override
    public int getItemViewType(int position, @NonNull File file) {
        if (isMultimedia(file)) {
            if (isCheckable(file)) {
                return VIEWTYPE_IMAGE_CHECKABLE;
            } else {
                return VIEWTYPE_IMAGE;
            }
        } else {
            return super.getItemViewType(position, file);
        }
    }

    /**
     * We override this method and provide some special views for images.
     * This is necessary to work around a bug on older Android versions (4.0.3 for example)
     * where setting a "tint" would just make the entire image a square of solid color.
     * <p/>
     * So the special layouts used here are merely "untinted" copies from the library.
     *
     * @param parent   Containing view
     * @param viewType which the ViewHolder will contain
     * @return a DirViewHolder (or subclass thereof)
     */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEWTYPE_IMAGE_CHECKABLE:
                return new CheckableViewHolder(LayoutInflater.from(getActivity())
                        .inflate(R.layout.listitem_image_checkable, parent, false));
            case VIEWTYPE_IMAGE:
                return new DirViewHolder(LayoutInflater.from(getActivity())
                        .inflate(R.layout.listitem_image, parent, false));
            default:
                return super.onCreateViewHolder(parent, viewType);
        }
    }

    /**
     * Overriding this method allows us to inject a preview image
     * in the layout
     *
     * @param vh       to bind data from either a file or directory
     * @param position 0 - n, where the header has been subtracted
     * @param file     to show info about
     */
    @Override
    public void onBindViewHolder(@NonNull DirViewHolder vh, int position, @NonNull File file) {
        // Let the super method do its thing with checkboxes and text
        super.onBindViewHolder(vh, position, file);

        // Here we load the preview image if it is an image file
        final int viewType = getItemViewType(position, file);
        if (viewType == VIEWTYPE_IMAGE_CHECKABLE || viewType == VIEWTYPE_IMAGE) {
            // Need to set it to visible because the base code will set it to invisible by default
            vh.icon.setVisibility(View.VISIBLE);
            // Just load the image
            Glide.with(this).load(file).into((ImageView) vh.icon);
        }
    }
}
