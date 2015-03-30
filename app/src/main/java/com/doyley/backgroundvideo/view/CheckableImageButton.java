package com.doyley.backgroundvideo.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.ImageButton;

public class CheckableImageButton extends ImageButton implements Checkable {

	private boolean mChecked;

	private static final int[] CHECKED_STATE_SET = {
			android.R.attr.state_checked
	};

	public CheckableImageButton(Context context) {
		super(context);
	}

	public CheckableImageButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked()) {
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		}
		return drawableState;
	}


	@Override
	public void setChecked(boolean checked) {
		if (mChecked != checked) {
			mChecked = checked;
			refreshDrawableState();
		}
	}

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	@Override
	public void toggle() {
		setChecked(!isChecked());
	}

	@Override
	public boolean performClick() {
		toggle();
		return super.performClick();
	}
}
