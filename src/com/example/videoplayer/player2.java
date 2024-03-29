package com.example.videoplayer;

import android.app.Activity;
import android.content.Context;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;

import android.media.MediaFormat;

import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.MediaController;
import android.widget.MediaController.MediaPlayerControl;
import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.MediaPlayer.OnBufferingUpdateListener;
import io.vov.vitamio.MediaPlayer.OnCompletionListener;
import io.vov.vitamio.MediaPlayer.OnPreparedListener;
import io.vov.vitamio.MediaPlayer.OnInfoListener;
import io.vov.vitamio.MediaPlayer.OnErrorListener;
import java.io.InputStream;
import java.util.Map;
import java.util.Vector;

/**
 * Displays a video file.  The TextureVideoView class
 * can load images from various sources (such as resources or content
 * providers), takes care of computing its measurement from the video so that
 * it can be used in any layout manager, and provides various display options
 * such as scaling and tinting.
 *
 * <em>Note: VideoView does not retain its full state when going into the
 * background.</em>  In particular, it does not restore the current play state,
 * play position or selected tracks.  Applications should
 * save and restore these on their own in
 * {@link android.app.Activity#onSaveInstanceState} and
 * {@link android.app.Activity#onRestoreInstanceState}.<p>
 * Also note that the audio session id (from {@link #getAudioSessionId}) may
 * change from its previously returned value when the VideoView is restored.<p>
 *
 * This code is based on the official Android sources for 4.4.2_r3 with the following differences:
 * <ol>
 *     <li>extends {@link android.view.TextureView} instead of a {@link android.view.SurfaceView} allowing proper
 *     view animations</li>
 *     <li>removes code that uses hidden APIs and thus is not available</li>
 * </ol>
 */
public class player2 extends TextureView
    implements MediaPlayerControl {
    private String TAG = "TextureVideoView", path;
    // settable by the client
    private Uri         mUri;

    // all possible internal states
    private static final int STATE_ERROR              = -1;
    private static final int STATE_IDLE               = 0;
    private static final int STATE_PREPARING          = 1;
    private static final int STATE_PREPARED           = 2;
    private static final int STATE_PLAYING            = 3;
    private static final int STATE_PAUSED             = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
   

    private boolean mIsVideoSizeKnown = false;
    private boolean mIsVideoReadyToBePlayed = false;
	
	// Create Handler to call View updates on the main UI thread.
	private final Handler handler = new Handler();
	
    // mCurrentState is a TextureVideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the TextureVideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private static int mCurrentState;
    private static int mTargetState;

    public static Surface sf1;
    // All the stuff we need for playing and showing a video
    static public SurfaceTexture sf;
    static public MediaPlayer mMediaPlayer;
    private int         mAudioSession;
    private int         mVideoWidth;
    private int         mVideoHeight;
    private int         mSurfaceWidth;
    private int         mSurfaceHeight;
    private  MediaController mMediaController;
    private OnCompletionListener mOnCompletionListener;
    private OnPreparedListener mOnPreparedListener;
    private static int         mCurrentBufferPercentage;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener  mOnInfoListener;
    private int         mSeekWhenPrepared;  // recording the seek position while preparing
    private static boolean     mCanPause;
    private static boolean     mCanSeekBack;
    private static boolean     mCanSeekForward;
    private Context mContext;

    public player2(Context context) {
        super(context);
       
    }

    public player2(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
       
    }

    public player2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initVideoView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.i("@@@@", "onMeasure(" + MeasureSpec.toString(widthMeasureSpec) + ", "
        //        + MeasureSpec.toString(heightMeasureSpec) + ")");

        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        if (mVideoWidth > 0 && mVideoHeight > 0) {

            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                // the size is fixed
                width = widthSpecSize;
                height = heightSpecSize;

                // for compatibility, we adjust size based on aspect ratio
                if ( mVideoWidth * height  < width * mVideoHeight ) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                } else if ( mVideoWidth * height  > width * mVideoHeight ) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height to match aspect ratio if possible
                width = widthSpecSize;
                height = width * mVideoHeight / mVideoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                // only the height is fixed, adjust the width to match aspect ratio if possible
                height = heightSpecSize;
                width = height * mVideoWidth / mVideoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    width = widthSpecSize;
                }
            } else {
                // neither the width nor the height are fixed, try to use actual video size
                width = mVideoWidth;
                height = mVideoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall, decrease both width and height
                    height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide, decrease both width and height
                    width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(player2.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(player2.class.getName());
    }

    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        return getDefaultSize(desiredSize, measureSpec);
    }

    private void initVideoView() {
        mContext = getContext();
        mVideoWidth = 0;
        mVideoHeight = 0;
        setSurfaceTextureListener(mSurfaceTextureListener);
        if (mMediaPlayer!=null) {
        	mVideoWidth = mMediaPlayer.getVideoWidth();
            mVideoHeight = mMediaPlayer.getVideoHeight();
            if (mVideoWidth != 0 && mVideoHeight != 0 && sf!=null) {
            	sf.setDefaultBufferSize(mVideoWidth, mVideoHeight);
                requestLayout();
            }} else {
            	mVideoWidth = 0;
            	mVideoHeight = 0;
            	mCurrentState = STATE_IDLE;
            	mTargetState  = STATE_IDLE;
        }
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    public void setVideoPath(String path1, Activity activity) {
    	mContext=activity.getApplicationContext();
        setVideoURI(Uri.parse(path1));
        path=path1;
    }

    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
    }

    /**
     * @hide
     */
    private void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
        mSeekWhenPrepared = 0;
    }

    private Vector<Pair<InputStream, MediaFormat>> mPendingSubtitleTracks;

    private void setListeners() {
    	mMediaPlayer.setOnPreparedListener(mPreparedListener);
        mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
        mMediaPlayer.setOnCompletionListener(mCompletionListener);
        mMediaPlayer.setOnErrorListener(mErrorListener);
        mMediaPlayer.setOnInfoListener(mInfoListener);
        mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
        mCurrentBufferPercentage = 0;
    }
    private void openVideo() {
        if ((sf == null)||(mUri==null)) {
            // not ready for playback just yet, will try again later
            return;
        } 
        // Tell the music playback service to pause
        // TODO: these constants need to be published somewhere in the framework.
       /* Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mContext.sendBroadcast(i);*/
      //  if (mMediaPlayer==null) {
        //doCleanUp();
        mVideoWidth = 0;
        mVideoHeight = 0;
        mIsVideoReadyToBePlayed = false;
        mIsVideoSizeKnown = false;
        try {
            mMediaPlayer = new MediaPlayer(mContext, true);

            /*if (mAudioSession != 0) {
                mMediaPlayer.setAudioSessionId(mAudioSession);
            } else {
                mAudioSession = mMediaPlayer.getAudioSessionId();
            }*/
            
            //setVolumeControlStream(AudioManager.STREAM_MUSIC);
            
            mMediaPlayer.setDataSource(path);
            if (sf1==null) {
            sf1=new Surface (sf);}
            mMediaPlayer.setSurface(sf1);
            //mMediaPlayer.setDataSource(mContext, mUri);
            //mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.prepareAsync();
            setListeners();
            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            attachMediaController();
    } catch (Exception e) {
        Log.e(TAG, "error: " + e.getMessage(), e);
      }//} else {
        //	setListeners();
       // }
    }

 ///////////////////////////////////////////////////////////////////////////////////
    public void setMediaController(MediaController controller) {
    	if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ?
                (View)this.getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
        new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
               /* mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                if (mVideoWidth != 0 && mVideoHeight != 0) {
                    getSurfaceTexture().setDefaultBufferSize(mVideoWidth, mVideoHeight);
                    requestLayout();
                }*/
            }
        };

    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            mCanPause = mCanSeekBack = mCanSeekForward = true;

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

           /* int seekToPosition = mSeekWhenPrepared;
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                //Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                getSurfaceTexture().setDefaultBufferSize(mVideoWidth, mVideoHeight);
                requestLayout();
                if (mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                    // We didn't actually change the size (it was already at the size
                    // we need), so we won't get a "surface changed" callback, so
                    // start the video here instead of in the callback.
                    if (mTargetState == STATE_PLAYING) {
                        start();
                        if (mMediaController != null) {
                            mMediaController.show();
                        }
                    } else if (!isPlaying() &&
                        (seekToPosition != 0 || getCurrentPosition() > 0)) {
                        if (mMediaController != null) {
                            // Show the media controls when we're paused into a video and make 'em stick.
                            mMediaController.show(0);
                        }
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }*/
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
        new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
            if (mCurrentState == STATE_PLAYBACK_COMPLETED) {
                return;
            }
                mCurrentState = STATE_PLAYBACK_COMPLETED;
                mTargetState = STATE_PLAYBACK_COMPLETED;
                if (mMediaController != null) {
                    mMediaController.hide();
                }
                if (mOnCompletionListener != null) {
                    mOnCompletionListener.onCompletion(mMediaPlayer);
                }
            }
        };

    private MediaPlayer.OnInfoListener mInfoListener =
        new MediaPlayer.OnInfoListener() {
            public  boolean onInfo(MediaPlayer mp, int arg1, int arg2) {
                if (mOnInfoListener != null) {
                    mOnInfoListener.onInfo(mp, arg1, arg2);
                }
                return true;
            }
        };

    private MediaPlayer.OnErrorListener mErrorListener =
        new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
                Log.d(TAG, "Error: " + framework_err + "," + impl_err);
                mCurrentState = STATE_ERROR;
                mTargetState = STATE_ERROR;
                if (mMediaController != null) {
                    mMediaController.hide();
                }

            /* If an error handler has been supplied, use it and finish. */
                if (mOnErrorListener != null) {
                    if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                        return true;
                    }
                }

            /* Otherwise, pop up an error dialog so the user knows that
             * something bad has happened. Only try and pop up the dialog
             * if we're attached to a window. When we're going away and no
             * longer have a window, don't bother showing the user an error.
             */
               /* if (getWindowToken() != null) {
                   // Resources r = mContext.getResources();
                    int messageId;

                    if (framework_err == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                        messageId = R.string.VideoView_error_text_invalid_progressive_playback;
                    } else {
                        messageId = R.string.VideoView_error_text_unknown;
                    }

                    new AlertDialog.Builder(mContext)
                        .setMessage(messageId)
                        .setPositiveButton(R.string.VideoView_error_button,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                         If we get here, there is no onError listener, so
                                         * at least inform them that the video is over.
                                         
                                    if (mOnCompletionListener != null) {
                                        mOnCompletionListener.onCompletion(mMediaPlayer);
                                    }
                                }
                            })
                        .setCancelable(false)
                        .show();
                }*/
                return true;
            }
        };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
        new MediaPlayer.OnBufferingUpdateListener() {
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                mCurrentBufferPercentage = percent;
            }
        };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l)
    {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener l)
    {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, TextureVideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l)
    {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(OnInfoListener l) {
        mOnInfoListener = l;
    }
    void startVideoPlayback() {
        Log.v(TAG, "startVideoPlayback");
        adjustAspectRatio(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
        mMediaPlayer.start();
      }
    
    /**
     * Sets the TextureView transform to preserve the aspect ratio of the video.
     */
    private void adjustAspectRatio(int videoWidth, int videoHeight) {
      int viewWidth = getWidth();
      int viewHeight = getHeight();
      double aspectRatio = (double) videoHeight / videoWidth;

      int newWidth, newHeight;
      if (viewHeight > (int) (viewWidth * aspectRatio)) {
        // limited by narrow width; restrict height
        newWidth = viewWidth;
        newHeight = (int) (viewWidth * aspectRatio);
      } else {
        // limited by short height; restrict width
        newWidth = (int) (viewHeight / aspectRatio);
        newHeight = viewHeight;
      }
      int xoff = (viewWidth - newWidth) / 2;
      int yoff = (viewHeight - newHeight) / 2;
      Log.v(TAG, "video=" + videoWidth + "x" + videoHeight + " view=" + viewWidth + "x" + viewHeight
          + " newView=" + newWidth + "x" + newHeight + " off=" + xoff + "," + yoff);

      Matrix txform = new Matrix();
      getTransform(txform);
      txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
      //txform.postRotate(10);          // just for fun
      txform.postTranslate(xoff, yoff);
      setTransform(txform);
    }
    
    
    
    TextureView.SurfaceTextureListener mSurfaceTextureListener = new SurfaceTextureListener()
    {
        @Override
        public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
        	/*if (mMediaPlayer!=null) {
            	mVideoWidth = mMediaPlayer.getVideoWidth();
                mVideoHeight = mMediaPlayer.getVideoHeight();
                if (mVideoWidth != 0 && mVideoHeight != 0) {
                	surface.setDefaultBufferSize(mVideoWidth, mVideoHeight);
                    requestLayout();
                }}*/
        }

        @Override
        public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
           sf=surface;
           mSurfaceWidth = width;
           mSurfaceHeight = height;
            if (mMediaPlayer!=null) { 
            	if (sf1==null) {
                    sf1=new Surface (sf);}
            	mMediaPlayer.setSurface(sf1);
          } 
          openVideo();
        }

        
        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
        	return false;
        }
        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture surface) {}
    };

    /*
     * release the media player in any state
     */
    private void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mPendingSubtitleTracks.clear();
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState  = STATE_IDLE;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
            keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
            keyCode != KeyEvent.KEYCODE_MENU &&
            keyCode != KeyEvent.KEYCODE_CALL &&
            keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    @Override
    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void suspend() {
        release(false);
    }

    public void resume() {
        openVideo();
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return (int)mMediaPlayer.getDuration();
        }

        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return (int)mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
            mCurrentState != STATE_ERROR &&
            mCurrentState != STATE_IDLE &&
            mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    } 

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    public int getAudioSessionId() {
        return mMediaPlayer.getAudioTrack();
    }

}