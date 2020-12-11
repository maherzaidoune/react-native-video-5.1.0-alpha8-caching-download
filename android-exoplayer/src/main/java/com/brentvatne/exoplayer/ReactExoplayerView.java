package com.brentvatne.exoplayer;

import android.annotation.SuppressLint;
import android.app.Activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;


import android.view.View.OnClickListener;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.brentvatne.react.R;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;

import com.google.android.exoplayer2.drm.FrameworkMediaDrm;


import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.Downloader;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloader;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.lang.reflect.Array;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;


import com.brentvatne.receiver.AudioBecomingNoisyReceiver;
import com.brentvatne.receiver.BecomingNoisyListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Dynamic;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.PlayerControlView;

import org.json.JSONException;

import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressLint("ViewConstructor")
class ReactExoplayerView extends FrameLayout implements
        LifecycleEventListener,
        BecomingNoisyListener,
        SimpleExoPlayer.EventListener,
        AnalyticsListener,
        BandwidthMeter.EventListener,
        DownloadTracker.Listener,
        AudioManager.OnAudioFocusChangeListener,
        MetadataOutput {

    private static final String TAG = "ReactExoplayerView";
    private static final CookieManager DEFAULT_COOKIE_MANAGER;
    private static final int SHOW_PROGRESS = 1;

    static {
        DEFAULT_COOKIE_MANAGER = new CookieManager();
        DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    protected SimpleExoPlayer player;

    MediaSourceFactory mediaSourceFactory;
    private boolean useExtensionRenderers;
    PlaybackStatsListener playbackStatsListener;
    private DownloadTracker downloadTracker;
    private DataSource.Factory dataSourceFactory;
    private List<MediaItem> mediaItems;
    private List<String> links;
    private int downloadingRN;
    private int linksSize;
    private DefaultTrackSelector.Parameters trackSelectorParameters;
    private DebugTextViewHelper debugViewHelper;
    private TrackGroupArray lastSeenTrackGroupArray;
    float global = 0;
    float current = 0;
    private boolean startAutoPlay;
    private int startWindow;
    private long startPosition;
    private final VideoEventEmitter eventEmitter;
    private PlayerControlView playerControlView;
    private View playPauseControlContainer;
    private Player.EventListener eventListener;
    private ExoPlayerView exoPlayerView;
    private DefaultTrackSelector trackSelector;
    private boolean playerNeedsSource;

    private int resumeWindow;
    private long resumePosition;
    private boolean loadVideoStarted;
    private boolean isFullscreen;
    private boolean isInBackground;
    private boolean isPaused;
    private boolean isBuffering;
    private boolean muted = false;
    private float rate = 1f;
    Handler mHandler ;
    Runnable runnable = null;
    private float audioVolume = 1f;
    private int minLoadRetryCount = 3;
    private int maxBitRate = 0;
    private long seekTime = C.TIME_UNSET;

    private Handler mainHandler;


    private AdsLoader adsLoader;
    private Uri loadedAdTagUri;


    // Props from React
    private Uri srcUri;
    private String extension;
    private boolean repeat;
    private String audioTrackType;
    private Dynamic audioTrackValue;
    private String videoTrackType;
    private Dynamic videoTrackValue;
    private String textTrackType;
    private Dynamic textTrackValue;
    private ReadableArray textTracks;
    private boolean disableFocus;
    private boolean preventsDisplaySleepDuringVideoPlayback = true;
    private float mProgressUpdateInterval = 250.0f;
    private boolean playInBackground = false;
    private Map<String, String> requestHeaders;
    private boolean mReportBandwidth = false;
    private UUID drmUUID = null;
    private MenuItem preferExtensionDecodersMenuItem;
    private String drmLicenseUrl = null;
    private String[] drmLicenseHeader = null;
    private boolean controls;
    private final ReactExoplayerConfig config;
    private final DefaultBandwidthMeter bandwidthMeter;
    // \ End props

    private int minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
    private int maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
    private int bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    private int bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;

    // React
    private final ThemedReactContext themedReactContext;
    private final AudioManager audioManager;
    private final AudioBecomingNoisyReceiver audioBecomingNoisyReceiver;
    private final Handler progressHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (player != null && player.getPlaybackState() == Player.STATE_READY
                            && player.getPlayWhenReady()) {
                        WritableMap payload = Arguments.createMap();
                        payload.putDouble("getTotalPlayTimeMs", playbackStatsListener.getPlaybackStats().getTotalPlayTimeMs());
                        themedReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("onPlayedTime", payload);
                        Log.d(TAG, "handleMessage: getTotalPlayTimeMs "+playbackStatsListener.getPlaybackStats().getTotalPlayTimeMs());
                        long pos = player.getCurrentPosition();
                        long bufferedDuration = player.getBufferedPercentage() * player.getDuration() / 100;
                        Log.d(TAG, "handleMessage: "+bufferedDuration);
                        eventEmitter.progressChanged(pos, bufferedDuration, player.getDuration(), getPositionInFirstPeriodMsForCurrentWindow(pos));
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, Math.round(mProgressUpdateInterval));
                    }
                    break;
            }
        }
    };

    public double getPositionInFirstPeriodMsForCurrentWindow(long currentPosition) {
        Timeline.Window window = new Timeline.Window();
        if (!player.getCurrentTimeline().isEmpty()) {
            player.getCurrentTimeline().getWindow(player.getCurrentWindowIndex(), window);
        }
        return window.windowStartTimeMs + currentPosition;
    }

    public ReactExoplayerView(ThemedReactContext context, ReactExoplayerConfig config) {
        super(context);
        this.themedReactContext = context;
        this.eventEmitter = new VideoEventEmitter(context);
        this.config = config;
        this.bandwidthMeter = config.getBandwidthMeter();

        createViews();
        downloadSetup();

        themedReactContext.addLifecycleEventListener(this);
        audioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver(themedReactContext);
        exoPlayerView.requestFocus();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

    }



    @Override
    public void setId(int id) {
        super.setId(id);
        eventEmitter.setViewId(id);

    }

    private void createViews() {
        clearResumePosition();
        dataSourceFactory = buildDataSourceFactory(true);
        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
        }

        LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        exoPlayerView = new ExoPlayerView(themedReactContext);
        exoPlayerView.setLayoutParams(layoutParams);
        addView(exoPlayerView, 0, layoutParams);
        DefaultTrackSelector.ParametersBuilder builder =
                new DefaultTrackSelector.ParametersBuilder(/* context= */ themedReactContext);
        trackSelectorParameters = builder.build();
        clearStartPosition();
    }


    protected void clearStartPosition() {
        startAutoPlay = true;
        startWindow = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.d(TAG, "onAttachedToWindow: ");
        initializePlayer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        /* We want to be able to continue playing audio when switching tabs.
         * Leave this here in case it causes issues.
         */
        // stopPlayback();
    }

    // LifecycleEventListener implementation

    @Override
    public void onHostResume() {
        downloadTracker.addListener(this);
        if (!playInBackground || !isInBackground) {
            setPlayWhenReady(!isPaused);
        }
        isInBackground = false;
    }

    @Override
    public void onHostPause() {
        isInBackground = true;
        if (playInBackground) {
            return;
        }
        setPlayWhenReady(false);
    }

    @Override
    public void onHostDestroy() {
        stopPlayback();
        downloadTracker.removeListener(this);
    }

    public void cleanUpResources() {
        stopPlayback();
    }



    private void initializePlayer() {
        Log.d(TAG, "initializePlayer: ");
        ReactExoplayerView self = this;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {


            mediaItems = createMediaItems();
            if (mediaItems.isEmpty()) {
                return;
            }
            if (player == null) {

            TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
            trackSelector = new DefaultTrackSelector(themedReactContext,videoTrackSelectionFactory);
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setMaxVideoBitrate(maxBitRate == 0 ? Integer.MAX_VALUE : maxBitRate));

            DefaultAllocator allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
            DefaultLoadControl.Builder defaultLoadControlBuilder = new DefaultLoadControl.Builder();
            defaultLoadControlBuilder.setAllocator(allocator);
            defaultLoadControlBuilder.setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs);
            defaultLoadControlBuilder.setTargetBufferBytes(-1);
            defaultLoadControlBuilder.setPrioritizeTimeOverSizeThresholds(true);
            DefaultLoadControl defaultLoadControl = defaultLoadControlBuilder.build();
            DefaultRenderersFactory renderersFactory =
                    new DefaultRenderersFactory(themedReactContext)
                            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
             playbackStatsListener= new PlaybackStatsListener(true, null);
            //trackSelector = new DefaultTrackSelector(/* context= */ themedReactContext);
          //  trackSelector.setParameters(trackSelectorParameters);
            lastSeenTrackGroupArray = null;
            player =
                    new SimpleExoPlayer.Builder(/* context= */ themedReactContext, renderersFactory)
                           // .setMediaSourceFactory(mediaSourceFactory)
                            .setTrackSelector(trackSelector)
                            .setLoadControl(defaultLoadControl)
                            .setBandwidthMeter(bandwidthMeter)
                            .build();
            player.addAnalyticsListener(new EventLogger(trackSelector));
            player.addAnalyticsListener(playbackStatsListener);
            player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ false);
            player.addListener(self);
            player.addMetadataOutput(self);
            exoPlayerView.setPlayer(player);
            audioBecomingNoisyReceiver.setListener(self);
            bandwidthMeter.addEventListener(new Handler(), self);
            setPlayWhenReady(!isPaused);
            playerNeedsSource = true;
            PlaybackParameters params = new PlaybackParameters(rate, 1f);
            player.setPlaybackParameters(params);
            //   debugViewHelper = new DebugTextViewHelper(player, debugTextView);
            //  debugViewHelper.start();

        }
                if (playerNeedsSource && srcUri != null) {
                    exoPlayerView.invalidateAspectRatio();
                    mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory)
                                    //.setAdsLoaderProvider(this::getAdsLoader)
                                    .setAdViewProvider(exoPlayerView);
                  //  boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
                  /*  Log.d(TAG, "Have Resume position ? : "+haveResumePosition);
                    if (haveResumePosition) {
                        player.seekTo(resumeWindow, resumePosition);
                    }
                    */
                    //player.seekTo(0);
                    playerNeedsSource = false;
                    eventEmitter.loadStart();
                    loadVideoStarted = true;
                    player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItems.get(0)),true);
                    //player.setMediaItems(mediaItems, /* resetPosition= */ !haveResumePosition);
                    player.prepare();
//                    Log.d(TAG, "Current Keysetid : "+new String(mediaItems.get(0).playbackProperties.drmConfiguration.getKeySetId()));
                }
        applyModifiers();
            }
        }, 1);
    }
    private void releasePlayer() {
        if (player != null) {
            updateResumePosition();
            player.release();
            player.removeMetadataOutput(this);
            trackSelector = null;
            player = null;
        }
        progressHandler.removeMessages(SHOW_PROGRESS);
        themedReactContext.removeLifecycleEventListener(this);
        audioBecomingNoisyReceiver.removeListener();
        bandwidthMeter.removeEventListener(this);
    }

    private boolean requestAudioFocus() {
        if (disableFocus || srcUri == null) {
            return true;
        }
        int result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void setPlayWhenReady(boolean playWhenReady) {
//        if (player == null) {
//            return;
//        }
//        player.setPlayWhenReady(playWhenReady);

        if (player == null) {
            return;
        }

        if (playWhenReady) {
            boolean hasAudioFocus = requestAudioFocus();
            if (hasAudioFocus) {
                player.setPlayWhenReady(true);
            }
        } else {
            player.setPlayWhenReady(false);
        }
    }

    private void startPlayback() {
        if (player != null) {
            switch (player.getPlaybackState()) {
                case Player.STATE_IDLE:
                case Player.STATE_ENDED:
                    initializePlayer();
                    break;
                case Player.STATE_BUFFERING:
                case Player.STATE_READY:
                    if (!player.getPlayWhenReady()) {
                        setPlayWhenReady(true);
                    }
                    break;
                default:
                    break;
            }

        } else {
            initializePlayer();
        }
        if (!disableFocus) {
            setKeepScreenOn(preventsDisplaySleepDuringVideoPlayback);
        }
        eventEmitter.playbackRateChange(1);
    }

    private void pausePlayback() {
        if (player != null) {
            if (player.getPlayWhenReady()) {
                setPlayWhenReady(false);
            }
        }
        setKeepScreenOn(false);
        eventEmitter.playbackRateChange(0);
    }


    private void stopPlayback() {
        onStopPlayback();
        releasePlayer();
        audioManager.abandonAudioFocus(this);
    }

    private void onStopPlayback() {
        if (isFullscreen) {
            setFullscreen(false);
        }

    }

    private void updateResumePosition() {
        resumeWindow = player.getCurrentWindowIndex();
        resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition())
                : C.TIME_UNSET;
    }

    private void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        if (mReportBandwidth) {
            if (player == null) {
                eventEmitter.bandwidthReport(bitrate, 0, 0, "-1");
            } else {
                Format videoFormat = player.getVideoFormat();
                int width = videoFormat != null ? videoFormat.width : 0;
                int height = videoFormat != null ? videoFormat.height : 0;
                String trackId = videoFormat != null ? videoFormat.id : "-1";
                eventEmitter.bandwidthReport(bitrate, height, width, trackId);
            }
        }
    }


    @Override
    public void onAudioBecomingNoisy() {
        eventEmitter.audioBecomingNoisy();
    }
                
@Override
public void onDownloadsChanged(Download download) {
    if(Download.STATE_COMPLETED == download.state || Download.STATE_FAILED == download.state ){
        downloadingRN++;
    }
    WritableMap payload = Arguments.createMap();
    payload.putDouble("downloadState",download.state);
    payload.putDouble("chapterDownloaded",downloadingRN);
    this.themedReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("onDownload", payload);
    Log.d(TAG, "downloadState:  " + download.state);
    Log.d(TAG, "links:  " + links+"downloadingRN"+downloadingRN);
    eventEmitter.setDownloadState(download.state);

}
                
@Override
public void onProgressChanged(float download) {
Log.d(TAG, "Data : File number :  " + downloadingRN + ",  Over all Progress : " + download);
    WritableMap payload = Arguments.createMap();
    payload.putDouble("progress",global / (linksSize * 100) * 100);
    payload.putDouble("chapter",downloadingRN);
    this.themedReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("onProgress", payload);
}

public void Timer(Download download){

}
    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if(isPlaying){
            eventEmitter.ready();
            onBuffering(false);
            startProgressHandler();
            videoLoaded();
        }else{
            onBuffering(true);
            clearProgressMessageHandler();
            setKeepScreenOn(preventsDisplaySleepDuringVideoPlayback);
            //TODO: 10/19/20
            //Show Played time in MS.
            long seconds = TimeUnit.MILLISECONDS.toSeconds(playbackStatsListener.getPlaybackStats().getTotalPlayTimeMs());
            long millis = TimeUnit.MILLISECONDS.toMillis(playbackStatsListener.getPlaybackStats().getTotalPlayTimeMs());

            //showToast("played time : "+seconds +" seconds and "+millis+" milliseconds ");

          //  Log.d(TAG, "played time : "+playbackStatsListener.getPlaybackStats().getTotalPlayTimeMs());

        }
    }

    @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
            String text = "onStateChanged: playbackState="+playbackState;
            switch (playbackState) {
                case Player.STATE_IDLE:
                    text += "idle";
                    eventEmitter.idle();
                    clearProgressMessageHandler();
                    setKeepScreenOn(false);
                    break;
                case Player.STATE_BUFFERING:
                    text += "buffering";
                    onBuffering(true);
                   clearProgressMessageHandler();
                    setKeepScreenOn(preventsDisplaySleepDuringVideoPlayback);
                    break;

                case Player.STATE_READY:
                    text += "ready";
                    eventEmitter.ready();
                    onBuffering(false);
                    startProgressHandler();
                    videoLoaded();
                    setKeepScreenOn(preventsDisplaySleepDuringVideoPlayback);
                    break;
                case Player.STATE_ENDED:
                    text += "ended";
                    eventEmitter.end();
                    onStopPlayback();
                    setKeepScreenOn(false);
                    break;
                default:
                    text += "unknown";
                    break;
            }
            Log.d(TAG, text);
        }


        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(
                @NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
            updateButtonVisibility();
            if (trackGroups != lastSeenTrackGroupArray) {
                MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
                if (mappedTrackInfo != null) {
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
                            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                        showToast(R.string.error_unsupported_video);
                    }
                    if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
                            == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                        showToast(R.string.error_unsupported_audio);
                    }
                }
                lastSeenTrackGroupArray = trackGroups;
            }
        }





    private void startProgressHandler() {
        progressHandler.sendEmptyMessage(SHOW_PROGRESS);
    }

    /*
        The progress message handler will duplicate recursions of the onProgressMessage handler
        on change of player state from any state to STATE_READY with playWhenReady is true (when
        the video is not paused). This clears all existing messages.
     */
    private void clearProgressMessageHandler() {
        progressHandler.removeMessages(SHOW_PROGRESS);
    }

    private void videoLoaded() {
        if (loadVideoStarted) {
            loadVideoStarted = false;
            setSelectedAudioTrack(audioTrackType, audioTrackValue);
            setSelectedVideoTrack(videoTrackType, videoTrackValue);
            setSelectedTextTrack(textTrackType, textTrackValue);
            Format videoFormat = player.getVideoFormat();
            int width = videoFormat != null ? videoFormat.width : 0;
            int height = videoFormat != null ? videoFormat.height : 0;
            String trackId = videoFormat != null ? videoFormat.id : "-1";
            eventEmitter.load(player.getDuration(), player.getCurrentPosition(), width, height,
                    getAudioTrackInfo(), getTextTrackInfo(), getVideoTrackInfo(), trackId);
        }
    }

    private WritableArray getAudioTrackInfo() {
        WritableArray audioTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_AUDIO);
        if (info == null || index == C.INDEX_UNSET) {
            return audioTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            WritableMap audioTrack = Arguments.createMap();
            audioTrack.putInt("index", i);
            audioTrack.putString("title", format.id != null ? format.id : "");
            audioTrack.putString("type", format.sampleMimeType);
            audioTrack.putString("language", format.language != null ? format.language : "");
            audioTrack.putString("bitrate", format.bitrate == Format.NO_VALUE ? ""
                    : String.format(Locale.US, "%.2fMbps", format.bitrate / 1000000f));
            audioTracks.pushMap(audioTrack);
        }
        return audioTracks;
    }

    private WritableArray getVideoTrackInfo() {
        WritableArray videoTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_VIDEO);
        if (info == null || index == C.INDEX_UNSET) {
            return videoTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            TrackGroup group = groups.get(i);

            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                Format format = group.getFormat(trackIndex);
                WritableMap videoTrack = Arguments.createMap();
                videoTrack.putInt("width", format.width == Format.NO_VALUE ? 0 : format.width);
                videoTrack.putInt("height", format.height == Format.NO_VALUE ? 0 : format.height);
                videoTrack.putInt("bitrate", format.bitrate == Format.NO_VALUE ? 0 : format.bitrate);
                videoTrack.putString("codecs", format.codecs != null ? format.codecs : "");
                videoTrack.putString("trackId",
                        format.id == null ? String.valueOf(trackIndex) : format.id);
                videoTracks.pushMap(videoTrack);
            }
        }
        return videoTracks;
    }


    public void setDownloadListOriginalLength(int size) {
   //     Log.d(TAG, "setDownloadListOriginalLength: ");
        SharedPreferences.Editor editor = getContext().getSharedPreferences("globaldata", MODE_PRIVATE).edit();
        editor.putInt("globalsize", size);
        editor.apply();
        }

    public void setDownloadList(List<String> links) {
/*
            if(links.size()!=0) {
                if(setDownload(links.get(0))){
                 downloadingRN++;
                    links.remove(0);
                    global +=  100/(linksSize*100)*100 ;
                    //Log.d(TAG, "Data : File number :  "+downloadingRN +",  Over all Progress : "+(global/(linksSize*100)*100));
                    if(links.get(0)!=null){
                        setDownload(links.get(0));
                    }

                }else{
                    links.remove(0);
                    this.links = links;
                }

            }
            */

    }
    public void setDownload(String link) {
        setUpMedia(Uri.parse(link));
        boolean canDownload = getDownloadUnsupportedStringId() == 0;
        Log.d(TAG, "canDownload: " + canDownload);
        Log.d(TAG, "setDownload: " + mediaItems.get(0));
        boolean isDownloaded = downloadTracker.isDownloaded(mediaItems.get(0));
        Log.d(TAG, "isdownloaded:  " + isDownloaded);
       // downloadTracker.licenseRenew(mediaItems.get(0));
        if(!isDownloaded){
            Log.d(TAG, "entering Download: ");
            downloadTracker.addListener(this);
            onDownloadButtonClicked();
       //     downloadTracker.licenseRenew(mediaItems.get(0));
        }
    }


    /*
    Download checking

     */



    public void getAllDownloadsList() throws JSONException {
        downloadTracker = PlayerUtil.getDownloadTracker(/* context= */ themedReactContext);
        eventEmitter.getalldownloads(downloadTracker.loadallDownloads());
        Log.d(TAG, "getAllDownloadsList: "+downloadTracker.loadallDownloads());
    }

    public Boolean getDownloadById(MediaItem mediaItem){
        downloadTracker = PlayerUtil.getDownloadTracker(/* context= */ themedReactContext);
        if(downloadTracker.loadDownloads().get(checkNotNull(mediaItem.playbackProperties).uri)!=null){
        return true;
        }
        return false;
    }

    /*

EndDwnload checking
     */




    public void downloadSetup() {
        useExtensionRenderers = PlayerUtil.useExtensionRenderers();
        downloadTracker = PlayerUtil.getDownloadTracker(/* context= */ themedReactContext);
        try {
            DownloadService.start(themedReactContext, PlayerDownloadService.class);
        } catch (IllegalStateException e) {
            DownloadService.startForeground(themedReactContext, PlayerDownloadService.class);
        }

    }

    private void onDownloadButtonClicked() {
        int downloadUnsupportedStringId = getDownloadUnsupportedStringId();
        if (downloadUnsupportedStringId != 0) {
            Toast.makeText(themedReactContext, downloadUnsupportedStringId, Toast.LENGTH_LONG)
                    .show();
        } else {
            RenderersFactory renderersFactory = PlayerUtil.buildRenderersFactory(/* context= */ themedReactContext, isNonNullAndChecked(preferExtensionDecodersMenuItem));
            downloadTracker.toggleDownload(themedReactContext.getCurrentActivity().getFragmentManager(), mediaItems.get(0), renderersFactory);
        }

    }




    private static boolean isNonNullAndChecked(@Nullable MenuItem menuItem) {
        return menuItem != null && menuItem.isChecked();
    }

    private int getDownloadUnsupportedStringId() {

        MediaItem.PlaybackProperties playbackProperties =
                checkNotNull(mediaItems.get(0).playbackProperties);
        if (playbackProperties.adTagUri != null) {
            return R.string.download_ads_unsupported;
        }
        String scheme = playbackProperties.uri.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme))) {
            return R.string.download_scheme_unsupported;
        }
        return 0;
    }

    private WritableArray getTextTrackInfo() {
        WritableArray textTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_TEXT);
        if (info == null || index == C.INDEX_UNSET) {
            return textTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            WritableMap textTrack = Arguments.createMap();
            textTrack.putInt("index", i);
            textTrack.putString("title", format.id != null ? format.id : "");
            textTrack.putString("type", format.sampleMimeType);
            textTrack.putString("language", format.language != null ? format.language : "");
            textTracks.pushMap(textTrack);
        }
        return textTracks;
    }

    private void onBuffering(boolean buffering) {
        if (isBuffering == buffering) {
            return;
        }

        isBuffering = buffering;
        if (buffering) {
            eventEmitter.buffering(true);
        } else {
            eventEmitter.buffering(false);
        }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.d(TAG, "onPositionDiscontinuity: called");
        if (playerNeedsSource) {
            // This will only occur if the user has performed a seek whilst in the error state. Update the
            // resume position so that if the user then retries, playback will resume from the position to
            // which they seeked.
            updateResumePosition();
        }
        // When repeat is turned on, reaching the end of the video will not cause a state change
        // so we need to explicitly detect it.
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            eventEmitter.seek(player.getCurrentPosition(), seekTime);
            seekTime = C.TIME_UNSET;
        }else if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
                && player.getRepeatMode() == Player.REPEAT_MODE_ONE) { eventEmitter.end(); }



    }





    @Override
    public void onPlaybackParametersChanged(PlaybackParameters params) {
        eventEmitter.playbackRateChange(params.speed);
    }
    @Override
    public void onPlayerError(ExoPlaybackException e) {
        String errorString = "ExoPlaybackException type : " + e.type;
        Exception ex = e;
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                        (MediaCodecRenderer.DecoderInitializationException) cause;
                if (decoderInitializationException.codecInfo.name == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = getResources().getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = getResources().getString(R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType);
                    } else {
                        errorString = getResources().getString(R.string.error_no_decoder,
                                decoderInitializationException.mimeType);
                    }
                } else {
                    errorString = getResources().getString(R.string.error_instantiating_decoder,
                            decoderInitializationException.codecInfo.name);
                }
            }
        }
        else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
            errorString = getResources().getString(R.string.unrecognized_media_format);
        }
        eventEmitter.error(errorString, ex);
        playerNeedsSource = true;
        if (isBehindLiveWindow(e)) {
            clearResumePosition();
            initializePlayer();
        } else {
            updateResumePosition();
        }
    }


    private void showToast(String msg) {
        Toast.makeText(themedReactContext, msg,
                Toast.LENGTH_LONG).show();
    }
    private void showToast(int id) {
        Toast.makeText(themedReactContext, id,
                Toast.LENGTH_LONG).show();
    }




    private void updateButtonVisibility() {

    }

    private List<MediaItem> createMediaItems() {
            setUpMedia(srcUri);
        boolean hasAds = false;
        for (int i = 0; i < mediaItems.size(); i++) {
            MediaItem mediaItem = mediaItems.get(i);
            Log.d(TAG, "createMediaItems: " + mediaItem.playbackProperties.uri.toString());

            if (Util.maybeRequestReadExternalStoragePermission(/* activity= */ themedReactContext.getCurrentActivity(), mediaItem)) {
                // The player will be reinitialized if the permission is granted.
                return Collections.emptyList();
            }
            if (!Util.checkCleartextTrafficPermitted(mediaItem)) {
                showToast(R.string.error_cleartext_not_permitted);
                return Collections.emptyList();
            }
            
            MediaItem.DrmConfiguration drmConfiguration =
                    checkNotNull(mediaItem.playbackProperties).drmConfiguration;
            if (drmConfiguration != null) {
                Log.d(TAG, "createMediaItems: Has DRM config ");
                if (Util.SDK_INT < 18) {
                    showToast(R.string.error_drm_unsupported_before_api_18);
                    //  getContext().finish();
                    return Collections.emptyList();
                } else if (!FrameworkMediaDrm.isCryptoSchemeSupported(drmConfiguration.uuid)) {
                    showToast(R.string.error_drm_unsupported_scheme);
                    //  finish();
                    return Collections.emptyList();
                }
            }else{
                Log.d(TAG, "createMediaItems: Doesn't have drm config");
            }
            hasAds |= mediaItem.playbackProperties.adTagUri != null;
        }
        if (!hasAds) {
            releaseAdsLoader();
        }
        return mediaItems;
    }



    private void releaseAdsLoader() {
        if (adsLoader != null) {
            adsLoader.release();
            adsLoader = null;
            loadedAdTagUri = null;
        }
    }

    private static boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    public int getTrackRendererIndex(int trackType) {
        if (player != null) {
            int rendererCount = player.getRendererCount();
            for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
                if (player.getRendererType(rendererIndex) == trackType) {
                    return rendererIndex;
                }
            }
        }
        return C.INDEX_UNSET;
    }

    @Override
    public void onMetadata(Metadata metadata) {
        eventEmitter.timedMetadata(metadata);
    }


    public void setSrc(final Uri uri, final String extension, Map<String, String> headers) {

        boolean isOriginalSourceNull = srcUri == null;
        boolean isSourceEqual = uri.equals(srcUri);
        Log.d(TAG, "setSrc: "+uri);
        this.srcUri = uri;
        this.extension = extension;
        this.requestHeaders = headers;
        this.dataSourceFactory = PlayerUtil.getDataSourceFactory(this.themedReactContext, bandwidthMeter,
                        this.requestHeaders);
        if (!isOriginalSourceNull && !isSourceEqual) {
            reloadSource();
        }


    }


    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return PlayerUtil.getDataSourceFactory(this.themedReactContext,
                useBandwidthMeter ? bandwidthMeter : null, requestHeaders);
    }


    public void setUpMedia(Uri uri){
        if (uri != null) {

            String title = uri.toString();
            mediaItems = new ArrayList<>();
            //mediaItems.clear();

            MediaItem.Builder mediaItem = new MediaItem.Builder();

            //title = "SatoriTest (MP4)"; //name of the stream
            // uri = Uri.parse("https://swannmediaservice-euwe.streaming.media.azure.net/aed5ba74-597d-40bb-8454-09e15068a9c3/stern.ism/manifest(format=mpd-time-csf,encryption=cenc)"); //stream url
            //     extension = "mpd"; //stream extension mpd

//Could be useful for invite guest role
            //mediaItem.setClipStartPositionMs(0000000);//empty
            //mediaItem.setClipEndPositionMs(600000);//mpty

            //mediaItem.setAdTagUri(reader.nextString());//empty
            //Log.d(TAG, "setUpMedia: DRMUUID"+drmUUID.toString());
            if (drmUUID != null) {
                Log.d(TAG, "setUpMedia: DRM IS NOT NULL"+drmUUID.toString());
                mediaItem.setDrmUuid(drmUUID); //widevine
                mediaItem.setDrmLicenseUri(drmLicenseUrl);
                Map<String, String> requestHeaders = new HashMap<>();
                requestHeaders.put(drmLicenseHeader[0], drmLicenseHeader[1]);
                mediaItem.setDrmLicenseRequestHeaders(requestHeaders);
              //  drmUUID = null;
               // drmLicenseHeader = null;
                //drmLicenseUrl= null;
            }

      /* to check yes or no
              mediaItem.setDrmSessionForClearTypes(
                  ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO));
*/

            //mediaItem.setDrmMultiSession(true); //multi session to revise but now no
            Log.d(TAG, "setUpMedia: "+extension);
            @Nullable
            String adaptiveMimeType =
                    Util.getAdaptiveMimeTypeForContentType(Util.inferContentType(uri, extension));
            mediaItem
                    .setUri(uri)
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
                    .setMimeType(adaptiveMimeType);
            MediaItem md = mediaItem.build();
            Log.d(TAG, "downloadRequest: " + md.playbackProperties.uri);
            DownloadRequest downloadRequest = downloadTracker.getDownloadRequest(checkNotNull(md.playbackProperties).uri);
            if (downloadRequest != null) {
                MediaItem.Builder builder = md.buildUpon();
                builder.setMediaId(downloadRequest.id)
                        .setUri(downloadRequest.uri)
                        .setCustomCacheKey(downloadRequest.customCacheKey)
                        .setMimeType(downloadRequest.mimeType)
                        .setStreamKeys(downloadRequest.streamKeys)
                        .setDrmKeySetId(downloadRequest.keySetId);

                mediaItems.add(builder.build());
                Log.d(TAG, "mediaItems: "+mediaItems.get(0).mediaMetadata.title);
            } else {
                Log.d(TAG, "setSrc: not Downloaded");
                mediaItems.add(md);
            }

        }
    }

    public void setProgressUpdateInterval(final float progressUpdateInterval) {
        mProgressUpdateInterval = progressUpdateInterval;
    }

    public void setReportBandwidth(boolean reportBandwidth) {
        mReportBandwidth = reportBandwidth;
    }

    public void setRawSrc(final Uri uri, final String extension) {
        if (uri != null) {
            Log.d(TAG, "setRawSrc: ");

            String title = null;

            MediaItem.Builder mediaItem = new MediaItem.Builder();

            title = "SatoriTest (MP4)"; //name of the stream

            // uri = Uri.parse("https://swannmediaservice-euwe.streaming.media.azure.net/aed5ba74-597d-40bb-8454-09e15068a9c3/stern.ism/manifest(format=mpd-time-csf,encryption=cenc)"); //stream url

            //     extension = "mpd"; //stream extension mpd
/*
Could be useful for invite guest role
            mediaItem.setClipStartPositionMs(reader.nextLong());//empty

            mediaItem.setClipEndPositionMs(reader.nextLong());//mpty

 */
            //mediaItem.setAdTagUri(reader.nextString());//empty
            //mediaItem.setDrmUuid(Util.getDrmUuid("widevine")); //widevine
            //mediaItem.setDrmLicenseUri("");
            //Map<String, String> requestHeaders = new HashMap<>();
            //requestHeaders.put("Authorization","Bearer=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwczovL3d3dy5zYXRvcmlwb3AuY29tLyIsImF1ZCI6InVybjpzYXRvcmlwb3AiLCJleHAiOjE3MTA4MDczODksIm5iZiI6MTYwMTMwMDA3OH0.O_41HbAcE8kFDivOM9Q4AL2z-4TMUTLchuUoyxCdDKY"); //add athoriwisation and token
//            mediaItem.setDrmLicenseRequestHeaders(requestHeaders);

      /* to check yes or no
              mediaItem.setDrmSessionForClearTypes(
                  ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO));
            */

            //    mediaItem.setDrmMultiSession(reader.nextBoolean()); //multi session to revise but now no


            @Nullable
            String adaptiveMimeType =
                    Util.getAdaptiveMimeTypeForContentType(Util.inferContentType(uri, extension));
            mediaItem
                    .setUri(uri)
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
                    .setMimeType(adaptiveMimeType);

            DownloadRequest downloadRequest =
                    PlayerUtil.getDownloadTracker(/* context= */ themedReactContext).getDownloadRequest(checkNotNull(mediaItem.build().playbackProperties).uri);
            if (downloadRequest != null) {
                MediaItem.Builder builder = mediaItem.build().buildUpon();
                builder
                        .setMediaId(downloadRequest.id)
                        .setUri(downloadRequest.uri)
                        .setCustomCacheKey(downloadRequest.customCacheKey)
                        .setMimeType(downloadRequest.mimeType)
                        .setStreamKeys(downloadRequest.streamKeys)
                        .setDrmKeySetId(downloadRequest.keySetId);
                mediaItems.add(builder.build());
            } else {
                mediaItems.add(mediaItem.build());
            }

        /*   boolean isOriginalSourceNull = srcUri == null;
            boolean isSourceEqual = uri.equals(srcUri);
*/
            this.srcUri = uri;
            this.extension = extension;
            // this.requestHeaders = headers;
            /*
            this.mediaDataSourceFactory =
                    DataSourceUtil.getDefaultDataSourceFactory(this.themedReactContext, bandwidthMeter,
                            this.requestHeaders);
*/

            reloadSource();

        }
    }

    public void setTextTracks(ReadableArray textTracks) {
        this.textTracks = textTracks;
        reloadSource();
    }

    private void reloadSource() {
        playerNeedsSource = true;
        initializePlayer();
    }

    public void setResizeModeModifier(@ResizeMode.Mode int resizeMode) {
        exoPlayerView.setResizeMode(resizeMode);
    }

    private void applyModifiers() {
        setRepeatModifier(repeat);
        setMutedModifier(muted);
    }

    public void setRepeatModifier(boolean repeat) {
        if (player != null) {
            if (repeat) {
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
            } else {
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
            }
        }
        this.repeat = repeat;
    }

    public void setPreventsDisplaySleepDuringVideoPlayback(boolean preventsDisplaySleepDuringVideoPlayback) {
        this.preventsDisplaySleepDuringVideoPlayback = preventsDisplaySleepDuringVideoPlayback;
    }

    public void setSelectedTrack(int trackType, String type, Dynamic value) {
        if (player == null) return;
        int rendererIndex = getTrackRendererIndex(trackType);
        if (rendererIndex == C.INDEX_UNSET) {
            return;
        }

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        if (info == null) {
            return;
        }

        TrackGroupArray groups = info.getTrackGroups(rendererIndex);
        int groupIndex = C.INDEX_UNSET;
        int[] tracks = {0};

        if (TextUtils.isEmpty(type)) {
            type = "default";
        }

        DefaultTrackSelector.Parameters disableParameters = trackSelector.getParameters()
                .buildUpon()
                .setRendererDisabled(rendererIndex, true)
                .build();

        if (type.equals("disabled")) {
            trackSelector.setParameters(disableParameters);
            return;
        } else if (type.equals("language")) {
            for (int i = 0; i < groups.length; ++i) {
                Format format = groups.get(i).getFormat(0);
                if (format.language != null && format.language.equals(value.asString())) {
                    groupIndex = i;
                    break;
                }
            }
        } else if (type.equals("title")) {
            for (int i = 0; i < groups.length; ++i) {
                Format format = groups.get(i).getFormat(0);
                if (format.id != null && format.id.equals(value.asString())) {
                    groupIndex = i;
                    break;
                }
            }
        } else if (type.equals("index")) {
            if (value.asInt() < groups.length) {
                groupIndex = value.asInt();
            }
        } else if (type.equals("resolution")) {
            int height = value.asInt();
            for (int i = 0; i < groups.length; ++i) { // Search for the exact height
                TrackGroup group = groups.get(i);
                for (int j = 0; j < group.length; j++) {
                    Format format = group.getFormat(j);
                    if (format.height == height) {
                        groupIndex = i;
                        tracks[0] = j;
                        break;
                    }
                }
            }
        } else if (rendererIndex == C.TRACK_TYPE_TEXT && Util.SDK_INT > 18) { // Text default
            // Use system settings if possible
            CaptioningManager captioningManager
                    = (CaptioningManager) themedReactContext.getSystemService(Context.CAPTIONING_SERVICE);
            if (captioningManager != null && captioningManager.isEnabled()) {
                groupIndex = getGroupIndexForDefaultLocale(groups);
            }
        } else if (rendererIndex == C.TRACK_TYPE_AUDIO) { // Audio default
            groupIndex = getGroupIndexForDefaultLocale(groups);
        }

        if (groupIndex == C.INDEX_UNSET && trackType == C.TRACK_TYPE_VIDEO && groups.length != 0) { // Video auto
            // Add all tracks as valid options for ABR to choose from
            TrackGroup group = groups.get(0);
            tracks = new int[group.length];
            groupIndex = 0;
            for (int j = 0; j < group.length; j++) {
                tracks[j] = j;
            }
        }

        if (groupIndex == C.INDEX_UNSET) {
            trackSelector.setParameters(disableParameters);
            return;
        }

        DefaultTrackSelector.Parameters selectionParameters = trackSelector.getParameters()
                .buildUpon()
                .setRendererDisabled(rendererIndex, false)
                .setSelectionOverride(rendererIndex, groups,
                        new DefaultTrackSelector.SelectionOverride(groupIndex, tracks))
                .build();
        trackSelector.setParameters(selectionParameters);
    }

    private int getGroupIndexForDefaultLocale(TrackGroupArray groups) {
        if (groups.length == 0) {
            return C.INDEX_UNSET;
        }

        int groupIndex = 0; // default if no match
        String locale2 = Locale.getDefault().getLanguage(); // 2 letter code
        String locale3 = Locale.getDefault().getISO3Language(); // 3 letter code
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            String language = format.language;
            if (language != null && (language.equals(locale2) || language.equals(locale3))) {
                groupIndex = i;
                break;
            }
        }
        return groupIndex;
    }

    public void setSelectedVideoTrack(String type, Dynamic value) {
        videoTrackType = type;
        videoTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_VIDEO, videoTrackType, videoTrackValue);
    }

    public void setSelectedAudioTrack(String type, Dynamic value) {
        audioTrackType = type;
        audioTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_AUDIO, audioTrackType, audioTrackValue);
    }

    public void setSelectedTextTrack(String type, Dynamic value) {
        textTrackType = type;
        textTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_TEXT, textTrackType, textTrackValue);
    }

    public void setPausedModifier(boolean paused) {
        isPaused = paused;
        if (player != null) {
            if (!paused) {
                if (player.getPlaybackState() == Player.STATE_ENDED) {
                    player.seekTo(0);
                }
                startPlayback();
            } else {

                pausePlayback();
            }
        }
    }

    public void setMutedModifier(boolean muted) {
        this.muted = muted;
        audioVolume = muted ? 0.f : 1.f;
        if (player != null) {
            player.setVolume(audioVolume);
        }
    }


    public void setVolumeModifier(float volume) {
        audioVolume = volume;
        if (player != null) {
            player.setVolume(audioVolume);
        }
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            seekTime = positionMs;
            player.getAnalyticsCollector().onSeekProcessed();
            player.seekTo(positionMs);
        }
    }

    public void setRateModifier(float newRate) {
        rate = newRate;
        if (player != null) {
            PlaybackParameters params = new PlaybackParameters(rate, 1f);
            player.setPlaybackParameters(params);
        }
    }

    public void setMaxBitRateModifier(int newMaxBitRate) {
        maxBitRate = newMaxBitRate;
        if (player != null) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setMaxVideoBitrate(maxBitRate == 0 ? Integer.MAX_VALUE : maxBitRate));
        }
    }

    public void setMinLoadRetryCountModifier(int newMinLoadRetryCount) {
        minLoadRetryCount = newMinLoadRetryCount;
        releasePlayer();
        initializePlayer();
    }

    public void setPlayInBackground(boolean playInBackground) {
        this.playInBackground = playInBackground;
    }

    public void setDisableFocus(boolean disableFocus) {
        this.disableFocus = disableFocus;
    }

    public void setFullscreen(boolean fullscreen) {
        if (fullscreen == isFullscreen) {
            return; // Avoid generating events when nothing is changing
        }
        isFullscreen = fullscreen;

        Activity activity = themedReactContext.getCurrentActivity();
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        int uiOptions;
        if (isFullscreen) {
            if (Util.SDK_INT >= 19) { // 4.4+
                uiOptions = SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | SYSTEM_UI_FLAG_FULLSCREEN;
            } else {
                uiOptions = SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | SYSTEM_UI_FLAG_FULLSCREEN;
            }
            eventEmitter.fullscreenWillPresent();
            decorView.setSystemUiVisibility(uiOptions);
            eventEmitter.fullscreenDidPresent();
        } else {
            uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
            eventEmitter.fullscreenWillDismiss();
            decorView.setSystemUiVisibility(uiOptions);
            eventEmitter.fullscreenDidDismiss();
        }
    }

    public void setUseTextureView(boolean useTextureView) {
        boolean finallyUseTextureView = useTextureView && this.drmUUID == null;
        exoPlayerView.setUseTextureView(finallyUseTextureView);
    }

    public void setHideShutterView(boolean hideShutterView) {
        exoPlayerView.setHideShutterView(hideShutterView);
    }


    public void setDrmType(UUID drmType) {

        Log.d(TAG, "setDrmType: ");
        this.drmUUID = drmType;
    }

    public void setDrmLicenseUrl(String licenseUrl) {
        this.drmLicenseUrl = licenseUrl;
        Log.d(TAG, "setDrmLicenseUrl: ");
    }

    public void setDrmLicenseHeader(String[] header) {
        this.drmLicenseHeader = header;
        Log.d(TAG, "setDrmLicenseHeader: ");
    }


    public void setBufferConfig(int newMinBufferMs, int newMaxBufferMs, int newBufferForPlaybackMs, int newBufferForPlaybackAfterRebufferMs) {
        minBufferMs = newMinBufferMs;
        maxBufferMs = newMaxBufferMs;
        bufferForPlaybackMs = newBufferForPlaybackMs;
        bufferForPlaybackAfterRebufferMs = newBufferForPlaybackAfterRebufferMs;
        releasePlayer();
        initializePlayer();
    }


    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                eventEmitter.audioFocusChanged(false);
                pausePlayback();
                audioManager.abandonAudioFocus(this);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                eventEmitter.audioFocusChanged(true);
                startPlayback();
                break;
            default:
                break;
        }

        if (player != null) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                // Lower the volume
                if (!muted) {
                    player.setVolume(audioVolume * 0.8f);
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // Raise it back to normal
                if (!muted) {
                    player.setVolume(audioVolume * 1);
                }
            }
        }
    }
}
