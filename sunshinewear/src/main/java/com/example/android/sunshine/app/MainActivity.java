package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MainActivity extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MainActivity.Engine> mWeakReference;

        public EngineHandler(MainActivity.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine{

        private static final String TAG = "EngineWatchFace";
        private static final String WEATHER_PATH = "/weather";
        private static final String WEATHER_TEMP_HIGH_KEY = "weather_temp_high_key";
        private static final String WEATHER_TEMP_LOW_KEY = "weather_temp_low_key";
        private static final String WEATHER_TEMP_ICON_KEY = "weather_temp_icon_key";

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        String weatherTempHigh;
        String weatherTempLow;
        Bitmap weatherTempIcon = null;

        boolean mRegisteredTimeZoneReceiver = false;

        Paint paintBackground;
        Paint paintLine;
        Paint paintTime;
        Paint paintTimeBold;
        Paint paintDate;
        Paint paintTemp;
        Paint paintTempBold;

        Rect textBounds = new Rect();
        boolean mAmbient;

        SimpleDateFormat mDateFormat;
        float mXOffset;
        float mYOffset;
        Calendar mCalendar;
        Date mDate;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
                invalidate();
            }
        };

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private GoogleApiClient googleApiClient;

        DataApi.DataListener dataListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
//                Log.e(TAG, "onDataChanged(): " + dataEvents);

                for (DataEvent event : dataEvents) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        String path = event.getDataItem().getUri().getPath();
                        if (WEATHER_PATH.equals(path)) {
                            try {
                                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                                weatherTempHigh = dataMapItem.getDataMap().getString(WEATHER_TEMP_HIGH_KEY);
                                weatherTempLow = dataMapItem.getDataMap().getString(WEATHER_TEMP_LOW_KEY);
                                Asset photo = dataMapItem.getDataMap().getAsset(WEATHER_TEMP_ICON_KEY);
                                weatherTempIcon = loadBitmapFromAsset(googleApiClient, photo);
                            } catch (Exception e) {
                                Log.e(TAG, "Exception   ", e);
                                weatherTempIcon = null;
                            }

                        }
                    }
                }
            }

            private Bitmap loadBitmapFromAsset(GoogleApiClient apiClient, Asset asset) {
                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(apiClient, asset).await().getInputStream();

                if (assetInputStream == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                return BitmapFactory.decodeStream(assetInputStream);
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            googleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle bundle) {
                            Log.e(TAG, "onConnected: Successfully connected to Google API client");
                            Wearable.DataApi.addListener(googleApiClient, dataListener);
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.e(TAG, "onConnectionSuspended");
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.e(TAG, "onConnectionFailed(): Failed to connect, with result : " + connectionResult);
                        }
                    })
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MainActivity.this)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MainActivity.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            paintBackground = new Paint();
            paintBackground.setColor(ContextCompat.getColor(MainActivity.this, R.color.digital_background));

            paintTime = new Paint();
            paintTime = createTextPaint(ContextCompat.getColor(MainActivity.this, R.color.main_text));

            paintTimeBold = new Paint();
            paintTimeBold = createTextPaint(ContextCompat.getColor(MainActivity.this, R.color.main_text));

            paintDate = new Paint();
            paintDate = createTextPaint(ContextCompat.getColor(MainActivity.this, R.color.second_text));

            paintTemp = new Paint();
            paintTemp = createTextPaint(ContextCompat.getColor(MainActivity.this, R.color.second_text));
            paintTempBold = new Paint();
            paintTempBold = createTextPaint(ContextCompat.getColor(MainActivity.this, R.color.main_text));

            paintLine = new Paint();
            paintLine.setColor(ContextCompat.getColor(MainActivity.this, R.color.second_text));
            paintLine.setStrokeWidth(0.5f);
            paintLine.setAntiAlias(true);

            mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);

            googleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat("ccc, MMM d yyyy", Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MainActivity.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MainActivity.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MainActivity.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            paintTime.setTextSize(resources.getDimension(R.dimen.time_text_size));
            paintTimeBold.setTextSize(resources.getDimension(R.dimen.time_text_size));
            paintDate.setTextSize(resources.getDimension(R.dimen.date_text_size));
            paintTemp.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            paintTempBold.setTextSize(resources.getDimension(R.dimen.temp_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            if(properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false)) {
                mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                paintBackground.setColor(inAmbientMode ? getResources().getColor(R.color.digital_background_ambient) : getResources().getColor(R.color.digital_background));
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    paintTime.setAntiAlias(!inAmbientMode);
                    paintDate.setAntiAlias(!inAmbientMode);
                    paintTemp.setAntiAlias(!inAmbientMode);
                    paintLine.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), paintBackground);

            int spaceY = 20;
            int spaceX = 10;
            int spaceYTemp;

            String text;

            int centerX = bounds.width() / 2;
            int centerY = bounds.height() / 2;

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            text = mDateFormat.format(mDate).toUpperCase();
            paintDate.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, centerX - textBounds.width() / 2, centerY, paintDate);
            spaceYTemp = textBounds.height();

            text = String.format("%02d", mCalendar.get(Calendar.HOUR_OF_DAY)) + ":" + String.format("%02d", mCalendar.get(Calendar.MINUTE));
            paintTimeBold.getTextBounds(text, 0, text.length(), textBounds);
            canvas.drawText(text, centerX - textBounds.width() / 2, centerY - spaceY + 4 - spaceYTemp, paintTimeBold);

            if (!mAmbient) {
                spaceYTemp = spaceY;
                canvas.drawLine(centerX - 20, centerY + spaceY, centerX + 20, centerY + spaceYTemp, paintLine);

                if (weatherTempHigh != null && weatherTempLow != null) {
                    text = weatherTempHigh;
                    paintTempBold.getTextBounds(text, 0, text.length(), textBounds);
                    spaceYTemp = textBounds.height() + spaceY + spaceYTemp;
                    canvas.drawText(text, centerX - textBounds.width() / 2, centerY + spaceYTemp, paintTempBold);

                    text = weatherTempLow;
                    canvas.drawText(text, centerX + textBounds.width() / 2 + spaceX, centerY + spaceYTemp, paintTemp);

                    if (weatherTempIcon != null) {
                        // draw weather icon
                        canvas.drawBitmap(weatherTempIcon,
                                centerX - textBounds.width() / 2 - spaceX - weatherTempIcon.getWidth(),
                                centerY + spaceYTemp - weatherTempIcon.getHeight() / 2 - textBounds.height() / 2, null);
                    }
                } else {
                    // draw temperature high
                    text = getString(R.string.info_not_available);
                    paintDate.getTextBounds(text, 0, text.length(), textBounds);
                    spaceYTemp = textBounds.height() + spaceY + spaceYTemp;
                    canvas.drawText(text, centerX - textBounds.width() / 2, centerY + spaceYTemp, paintDate);

                }
            }
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
