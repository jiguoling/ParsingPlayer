package com.hustunique.parsingplayer.player;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hustunique.parsingplayer.LogUtil;
import com.hustunique.parsingplayer.R;

import java.util.Formatter;
import java.util.Locale;

/**
 * Created by JianGuo on 1/20/17.
 * Custom media controller view for video view.
 */
public class ParsingMediaController implements IMediaController {
    private IMediaPlayerControl mPlayer;
    private static final int sDefaultTimeOut = 5000;
    private static final String TAG = "ParsingMediaController";
    private View mRoot;
    private Context mContext;
    private View mAnchor;
    private ImageButton mPauseButton, mQualityButton;
    private SeekBar mProgress;
    private TextView mCurrentTime, mEndTime;
    private StringBuilder mFormatBuilder;
    private Formatter mFormatter;
    private int mX, mY;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mParams;
    private boolean mIsShowing = false;


    public ParsingMediaController(Context context, AttributeSet attrs) {
        mContext = context;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        initControllerView();
        initPopupWindow();
    }

    public ParsingMediaController(Context context) {
        this(context, null);
    }


    private void initPopupWindow() {
        mParams = createLayoutParams(mRoot.getWindowToken());
    }

    private WindowManager.LayoutParams createLayoutParams(IBinder windowToken) {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        p.token = windowToken;
        p.format = PixelFormat.TRANSLUCENT;
        p.gravity = Gravity.END | Gravity.TOP;
        p.packageName = mContext.getPackageName();
        p.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        return p;
    }

    private void initControllerView() {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRoot = inflater.inflate(R.layout.media_controller, new FrameLayout(mContext), false);
        mPauseButton = (ImageButton) mRoot.findViewById(R.id.pause);
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
            mPauseButton.setOnClickListener(mPauseListener);
        }
        mQualityButton = (ImageButton) mRoot.findViewById(R.id.quality);
        if (mQualityButton != null) {
            mQualityButton.setOnClickListener(mQualityListener);
        }
        mProgress = (SeekBar) mRoot.findViewById(R.id.mediacontroller_progress);
        if (mProgress != null) {
            mProgress.setOnSeekBarChangeListener(mSeekListener);
            mProgress.setMax(1000);
        }
        mEndTime = (TextView) mRoot.findViewById(R.id.time);
        mCurrentTime = (TextView) mRoot.findViewById(R.id.time_current);
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
        mRoot.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        show(0);
                        break;
                    case MotionEvent.ACTION_UP:
                        show(sDefaultTimeOut);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        hide();
                        break;
                    default:
                        break;
                 }
                return true;
            }
        });
    }


    private final View.OnClickListener mQualityListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mPlayer != null && isShowing()) {
                if (mPlayer.isQualityViewShown())
                    mPlayer.hideQualityView();
                else
                    mPlayer.showQualityView();
            }
        }
    };

    private final View.OnClickListener mPauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            doPauseResume();
            show(0);
        }
    };

    private boolean mDragging;

    private final Runnable mShowProgress = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            if (!mDragging && mPlayer.isPlaying()) {
                mRoot.postDelayed(mShowProgress, 1000 - (pos % 1000));
            }
        }
    };

    private final Runnable mFadeOut = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private int setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }
        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mProgress.setProgress((int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            mProgress.setSecondaryProgress(percent * 10);
        }

        if (mEndTime != null)
            mEndTime.setText(stringForTime(duration));
        if (mCurrentTime != null)
            mCurrentTime.setText(stringForTime(position));

        return position;
    }

    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = mPlayer.getDuration();
            long newposition = (duration * progress) / 1000L;
            mPlayer.seekTo((int) newposition);
            if (mCurrentTime != null)
                mCurrentTime.setText(stringForTime((int) newposition));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            show(3600000);

            mDragging = true;
            mRoot.removeCallbacks(mShowProgress);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            setProgress();
            updatePausePlay();
            show(sDefaultTimeOut);

            // Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            mRoot.post(mShowProgress);
        }
    };

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }


    private void doPauseResume() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        updatePausePlay();
    }

    private void updatePausePlay() {
        if (mRoot == null || mPauseButton == null)
            return;
        if (mPlayer.isPlaying()) {
            mPauseButton.setImageResource(R.drawable.ic_pause_white_24dp);
        } else {
            mPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
        }
    }


    @Override
    public void hide() {
        if (mAnchor == null)
            return;

        if (mIsShowing) {
            if (mRoot.getParent() != null) {
                mWindowManager.removeViewImmediate(mRoot);
            }
            mIsShowing = false;
            mRoot.removeCallbacks(mShowProgress);
        }
    }



    private View.OnLayoutChangeListener mOnLayoutListener = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            updateAnchorViewLayout();
        }
    };

    @Override
    public void setAnchorView(View view) {
        view.removeOnLayoutChangeListener(mOnLayoutListener);
        mAnchor = view;
        mAnchor.addOnLayoutChangeListener(mOnLayoutListener);

    }

    @Override
    public void setEnabled(boolean enabled) {
        mRoot.setEnabled(enabled);
    }

    @Override
    public void setMediaPlayer(IMediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }


    private void showPopupWindowLayout() {
        updateAnchorViewLayout();
        LogUtil.i(TAG, "show popupWindow at top-left pos:" + "(" + mX + ", " + mY + ")");
        mWindowManager.addView(mRoot, mParams);
    }

    // FIXME: 2/8/17 Wrong position when in wrap_content mode
    private void updateAnchorViewLayout() {
        assert mAnchor != null;
        int[] anchorPos = new int[2];
        LogUtil.i(TAG, "anchorView: " + mAnchor);
        mAnchor.getLocationOnScreen(anchorPos);
        mRoot.measure(View.MeasureSpec.makeMeasureSpec(mAnchor.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(mAnchor.getHeight(), View.MeasureSpec.AT_MOST));
        int width = mAnchor.getWidth();
        LogUtil.i(TAG, "anchorView height: " + mAnchor.getHeight());
        LogUtil.i(TAG, "contentView height: " + mRoot.getMeasuredHeight());
        mX = anchorPos[0] + (mAnchor.getWidth() - width) / 2;
        mY = anchorPos[1] + mAnchor.getHeight() - mRoot.getMeasuredHeight();
        mParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mParams.width = width;
        mParams.x = mX;
        mParams.y = mY;
        LogUtil.i(TAG, "update mX: " + mX + ", mY: " + mY);
    }

    @Override
    public void show(int timeout) {
        if (!mIsShowing && mAnchor != null) {
            mIsShowing = true;
            setProgress();
            if (mPauseButton != null) {
                mPauseButton.requestFocus();
            }

            showPopupWindowLayout();
        }
        updatePausePlay();


        // cause the progress bar to be updated even if mShowing
        // was already true.  This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        mRoot.post(mShowProgress);

    }


    @Override
    public void show() {
        show(sDefaultTimeOut);
    }

    @Override
    public boolean isShowing() {
        return mIsShowing;
    }


}
