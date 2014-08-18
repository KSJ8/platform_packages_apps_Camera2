/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.android.camera.util.Gusterpolator;
import com.android.camera2.R;

/*
 * A toggle button that supports two or more states with images rendererd on top
 * for each state.
 * The button is initialized in an XML layout file with an array reference of
 * image ids (e.g. imageIds="@array/camera_flashmode_icons").
 * Each image in the referenced array represents a single integer state.
 * Every time the user touches the button it gets set to next state in line,
 * with the corresponding image drawn onto the face of the button.
 * State wraps back to 0 on user touch when button is already at n-1 state.
 */
public class MultiToggleImageButton extends ImageButton {
    /*
     * Listener interface for button state changes.
     */
    public interface OnStateChangeListener {
        /*
         * @param view the MultiToggleImageButton that received the touch event
         * @param state the new state the button is in
         */
        public abstract void stateChanged(View view, int state);
    }

    public static final int ANIM_DIRECTION_VERTICAL = 0;
    public static final int ANIM_DIRECTION_HORIZONTAL = 1;

    private static final int AINM_DURATION_MS = 250;
    private static final int UNSET = -1;

    private OnStateChangeListener mOnStateChangeListener;
    private int mState = UNSET;
    private int[] mImageIds;
    private int[] mDescIds;
    private int mLevel;
    private boolean mClickEnabled = true;
    private int mParentSize;
    private int mAnimDirection;

    public MultiToggleImageButton(Context context) {
        super(context);
        init();
    }

    public MultiToggleImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        parseAttributes(context, attrs);
        setState(0);
    }

    public MultiToggleImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
        parseAttributes(context, attrs);
        setState(0);
    }

    /*
     * Set the state change listener.
     *
     * @param onStateChangeListener the listener to set
     */
    public void setOnStateChangeListener(OnStateChangeListener onStateChangeListener) {
        mOnStateChangeListener = onStateChangeListener;
    }

    /*
     * Get the current button state.
     *
     */
    public int getState() {
        return mState;
    }

    /*
     * Set the current button state, thus causing the state change listener to
     * get called.
     *
     * @param state the desired state
     */
    public void setState(int state) {
        setState(state, true);
    }

    /*
     * Set the current button state.
     *
     * @param state the desired state
     * @param callListener should the state change listener be called?
     */
    public void setState(final int state, final boolean callListener) {
        if (mState == state || mState == UNSET) {
            setStateInternal(state, callListener);
            return;
        }

        if (mImageIds == null) {
            return;
        }

        Bitmap bitmap = combine(mState, state);
        if (bitmap == null) {
            setStateInternal(state, callListener);
            return;
        }

        setImageBitmap(bitmap);
        final Matrix matrix = new Matrix();

        int offset;
        if (mAnimDirection == ANIM_DIRECTION_VERTICAL) {
            offset = (mParentSize+getHeight())/2;
        } else if (mAnimDirection == ANIM_DIRECTION_HORIZONTAL) {
            offset = (mParentSize+getWidth())/2;
        } else {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(-offset, 0.0f);
        animator.setDuration(AINM_DURATION_MS);
        animator.setInterpolator(Gusterpolator.INSTANCE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                matrix.reset();
                if (mAnimDirection == ANIM_DIRECTION_VERTICAL) {
                    matrix.setTranslate(0.0f, (Float) animation.getAnimatedValue());
                } else if (mAnimDirection == ANIM_DIRECTION_HORIZONTAL) {
                    matrix.setTranslate((Float) animation.getAnimatedValue(), 0.0f);
                }

                setImageMatrix(matrix);
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setClickEnabled(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                setStateInternal(state, callListener);
                setClickEnabled(true);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                setStateInternal(state, callListener);
                setClickEnabled(true);
            }
        });
        animator.start();
    }

    /**
     * Enable or disable click reactions for this button
     * without affecting visual state.
     * For most cases you'll want to use {@link #setEnabled(boolean)}.
     * @param enabled True if click enabled, false otherwise.
     */
    public void setClickEnabled(boolean enabled) {
        mClickEnabled = enabled;
    }

    private void setStateInternal(int state, boolean callListener) {
        mState = state;
        if (mImageIds != null) {
            setImageByState(mState);
        }

        if (mDescIds != null) {
            String oldContentDescription = String.valueOf(getContentDescription());
            String newContentDescription = getResources().getString(mDescIds[mState]);
            if (oldContentDescription != null && !oldContentDescription.isEmpty()
                    && !oldContentDescription.equals(newContentDescription)) {
                setContentDescription(newContentDescription);
                String announceChange = getResources().getString(
                    R.string.button_change_announcement, newContentDescription);
                announceForAccessibility(announceChange);
            }
        }
        super.setImageLevel(mLevel);

        if (callListener && mOnStateChangeListener != null) {
            mOnStateChangeListener.stateChanged(MultiToggleImageButton.this, getState());
        }
    }

    private void nextState() {
        int state = mState + 1;
        if (state >= mImageIds.length) {
            state = 0;
        }
        setState(state);
    }

    protected void init() {
        this.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickEnabled) {
                    nextState();
                }
            }
        });
        setScaleType(ImageView.ScaleType.MATRIX);
    }

    private void parseAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.MultiToggleImageButton,
            0, 0);
        int imageIds = a.getResourceId(R.styleable.MultiToggleImageButton_imageIds, 0);
        if (imageIds > 0) {
            overrideImageIds(imageIds);
        }
        int descIds = a.getResourceId(R.styleable.MultiToggleImageButton_contentDescriptionIds, 0);
        if (descIds > 0) {
            overrideContentDescriptions(descIds);
        }
        a.recycle();
    }

    /**
     * Override the image ids of this button.
     */
    public void overrideImageIds(int resId) {
        TypedArray ids = null;
        try {
            ids = getResources().obtainTypedArray(resId);
            mImageIds = new int[ids.length()];
            for (int i = 0; i < ids.length(); i++) {
                mImageIds[i] = ids.getResourceId(i, 0);
            }
        } finally {
            if (ids != null) {
                ids.recycle();
            }
        }
    }

    /**
     * Override the content descriptions of this button.
     */
    public void overrideContentDescriptions(int resId) {
        TypedArray ids = null;
        try {
            ids = getResources().obtainTypedArray(resId);
            mDescIds = new int[ids.length()];
            for (int i = 0; i < ids.length(); i++) {
                mDescIds[i] = ids.getResourceId(i, 0);
            }
        } finally {
            if (ids != null) {
                ids.recycle();
            }
        }
    }

    /**
     * Set size info (either width or height, as necessary) of the view containing
     * this button. Used for offset calculations during animation.
     * @param s The size.
     */
    public void setParentSize(int s) {
        mParentSize = s;
    }

    /**
     * Set the animation direction.
     * @param d Either ANIM_DIRECTION_VERTICAL or ANIM_DIRECTION_HORIZONTAL.
     */
    public void setAnimDirection(int d) {
        mAnimDirection = d;
    }

    @Override
    public void setImageLevel(int level) {
        super.setImageLevel(level);
        mLevel = level;
    }

    private void setImageByState(int state) {
        if (mImageIds != null) {
            setImageResource(mImageIds[state]);
        }
        super.setImageLevel(mLevel);
    }

    private Bitmap combine(int oldState, int newState) {
        // in some cases, a new set of image Ids are set via overrideImageIds()
        // and oldState overruns the array.
        // check here for that.
        if (oldState >= mImageIds.length) {
            return null;
        }

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        int[] enabledState = new int[] {android.R.attr.state_enabled};

        // new state
        Drawable newDrawable = getResources().getDrawable(mImageIds[newState]).mutate();
        newDrawable.setState(enabledState);

        // old state
        Drawable oldDrawable = getResources().getDrawable(mImageIds[oldState]).mutate();
        oldDrawable.setState(enabledState);

        // combine 'em
        Bitmap bitmap = null;
        if (mAnimDirection == ANIM_DIRECTION_VERTICAL) {
            int bitmapHeight = (height*2) + ((mParentSize - height)/2);
            int oldBitmapOffset = height + ((mParentSize - height)/2);
            bitmap = Bitmap.createBitmap(width, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            newDrawable.setBounds(0, 0, newDrawable.getIntrinsicWidth(), newDrawable.getIntrinsicHeight());
            oldDrawable.setBounds(0, oldBitmapOffset, oldDrawable.getIntrinsicWidth(), oldDrawable.getIntrinsicHeight()+oldBitmapOffset);
            newDrawable.draw(canvas);
            oldDrawable.draw(canvas);
        } else if (mAnimDirection == ANIM_DIRECTION_HORIZONTAL) {
            int bitmapWidth = (width*2) + ((mParentSize - width)/2);
            int oldBitmapOffset = width + ((mParentSize - width)/2);
            bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            newDrawable.setBounds(0, 0, newDrawable.getIntrinsicWidth(), newDrawable.getIntrinsicHeight());
            oldDrawable.setBounds(oldBitmapOffset, 0, oldDrawable.getIntrinsicWidth()+oldBitmapOffset, oldDrawable.getIntrinsicHeight());
            newDrawable.draw(canvas);
            oldDrawable.draw(canvas);
        }

        return bitmap;
    }
}