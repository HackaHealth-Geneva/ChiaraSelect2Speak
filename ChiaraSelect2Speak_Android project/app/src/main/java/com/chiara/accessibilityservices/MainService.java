// Copyright 2020 Luca Randazzo
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.chiara.accessibilityservices;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.util.Locale;

/* This service provides an entry point to the Chiara_Select2Speak service. */
public class MainService extends AccessibilityService implements View.OnTouchListener {
    // service name used inside logs
    private static final String DEBUG_TAG = " [Chiara_MainService]";

    // service status variables
    private boolean service_active = false;
    private boolean first_setup = true;

    // layout
    FrameLayout mLayout;
    // area selection
    int previous_action=0, current_action=0;
    int x0, x1, y0, y1, current_x, current_y;
    // GUI
    ImageView image_view;
    Bitmap bitmapDrawingPane;
    Canvas canvasDrawingPane;
    Paint paint;

    // screenshot
    private static String SCREENSHOTS_DIRECTORY;
    private static int DELAY_SCREENSHOT = 1000; // delay [ms] to wait for ScreenshotActivity to capture all images
    Bitmap latest_screenshot_bitmap;

    // TextRecognizer
    TextRecognizer text_recognizer;

    // TextToSpeech engine
    private TextToSpeech tts;
    boolean remove_newlines = true;

    // goodies
    String tts_welcome_message = "Ciao scimmiotta, ti voglio bene da Luca";

    // debug
    boolean verbose_ontouch = false;
    boolean lovely_start    = false;

    //
    //@SuppressLint("ResourceType")
    @Override protected void onServiceConnected() {
        // print first messages
        Log.i(DEBUG_TAG, "[onServiceConnected] Hello world! Setting-up...");;


        // ---------------------------------------------------------------
        // Setup OCR
        // ---------------------------------------------------------------
        text_recognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        if (!text_recognizer.isOperational()) {
            // Note: The first time that an app using a Vision API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any text,
            // barcodes, or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available. The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.e(DEBUG_TAG, "[onServiceConnected] Detector dependencies are not available");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "[onServiceConnected] Low storage space available", Toast.LENGTH_LONG).show();
                Log.e(DEBUG_TAG, "[onServiceConnected] Low storage space available");
            }
        }
        else {
            Log.i(DEBUG_TAG, "[onServiceConnected] OCR correctly setup");
        }


        // ---------------------------------------------------------------
        // Setup Screenshoter
        // ---------------------------------------------------------------
        File externalFilesDir = getExternalFilesDir(null);
        if (externalFilesDir != null) {
            SCREENSHOTS_DIRECTORY = externalFilesDir.getAbsolutePath() + "/";
        }
        else {
            Log.e(DEBUG_TAG, "[onServiceConnected] Failed to create file storage directory, getExternalFilesDir is null.");
            return;
        }
        // delete previous screenshots
        deleteFilesInFolder(SCREENSHOTS_DIRECTORY);
        // get first screenshot, to setup activity and ask for permissions
        takeScreenshot();


        // ---------------------------------------------------------------
        // Setup Layout and GUI
        // ---------------------------------------------------------------
        // create layout and set Overlay properties
        mLayout = new FrameLayout(this);
        setOverlayProperties(false);

        // setup the buttons
        LayoutInflater inflater = LayoutInflater.from(MainService.this);
        inflater.inflate(R.layout.ui, mLayout);
        // hide stop button
        final Button button_stop = (Button) mLayout.findViewById(R.id.stop);
        button_stop.setVisibility(View.GONE);
        Log.i(DEBUG_TAG, "[onServiceConnected] mLayout setup");

        // Wait for takeScreenshot() to finish his job
        Log.i(DEBUG_TAG, "[onServiceConnected] Waiting for takeScreenshot() to return...");
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // info
                Log.i(DEBUG_TAG, "[onServiceConnected] DELAY_SCREENSHOT elapsed");

                // load latest screenshot
                latest_screenshot_bitmap = loadScreenshotBitmap();

                // setup bitmaps and drawing panes
                if (latest_screenshot_bitmap != null) {
                    //Log.i(DEBUG_TAG, "[onServiceConnected] screenshot loaded successfully");

                    Bitmap.Config config;
                    if (latest_screenshot_bitmap.getConfig() != null) {
                        config = latest_screenshot_bitmap.getConfig();
                    }
                    else {
                        config = Bitmap.Config.ARGB_8888;
                    }

                    // Create bitmap of same size for drawing
                    bitmapDrawingPane = Bitmap.createBitmap(
                            latest_screenshot_bitmap.getWidth(),
                            latest_screenshot_bitmap.getHeight(),
                            config);
                    canvasDrawingPane = new Canvas(bitmapDrawingPane);
                    image_view.setImageBitmap(bitmapDrawingPane);

                    // paint
                    paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.BLUE);
                    paint.setStrokeWidth(10);

                    Log.i(DEBUG_TAG, "[onServiceConnected] Drawing objects setup");
                }
                else {
                    Log.e(DEBUG_TAG, "[onServiceConnected] screenshot not loaded successfully");
                }
            }//run handler
        }, 2000 );

        // setup images
        image_view = (ImageView) mLayout.findViewById(R.id.image_view);
        image_view.setVisibility(View.GONE);
        Log.i(DEBUG_TAG, "[onServiceConnected] Images setup");

        // configure buttons
        configureButtons();
        Log.i(DEBUG_TAG, "[onServiceConnected] Buttons setup");


        // ---------------------------------------------------------------
        // Setup OnTouchListener
        // ---------------------------------------------------------------
        mLayout.setOnTouchListener(this);
        Log.i(DEBUG_TAG, "[onServiceConnected] OnTouchListener setup");


        // ---------------------------------------------------------------
        // Setup TTS
        // ---------------------------------------------------------------
        // Set up the Text To Speech engine.
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(final int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Log.i(DEBUG_TAG, "[onServiceConnected] Text to speech engine started successfully");
                    tts.setLanguage(Locale.ITALIAN);
                }
                else {
                    Log.e(DEBUG_TAG, "[onServiceConnected] Error starting the Text to speech engine");
                }
            }
        };
        tts = new TextToSpeech(this.getApplicationContext(), listener);


        // ---------------------------------------------------------------
        // Finish setup
        // ---------------------------------------------------------------
        Log.i(DEBUG_TAG, "[onServiceConnected] Setup done");
        Toast.makeText(getBaseContext(),"Chiara_Select2Speak active!", Toast.LENGTH_SHORT).show();

        if (lovely_start) {
            // Wait for a delay, and speak first message
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // speak loud first message
                    tts.speak(tts_welcome_message, TextToSpeech.QUEUE_ADD, null, "DEFAULT");
                }//run handler
            }, DELAY_SCREENSHOT);
        }
    }

    private void configureButtons() {
        final Button button_start   = (Button) mLayout.findViewById(R.id.start);
        final Button button_stop    = (Button) mLayout.findViewById(R.id.stop);

        button_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(DEBUG_TAG, "[configureButtons::button_start::onClick] Pressed 'Start'");
                Log.i(DEBUG_TAG, "[configureButtons::button_start::onClick] Setting screenshot as background...");

                // Delete previous screenshots
                deleteFilesInFolder(SCREENSHOTS_DIRECTORY);

                // Take new screenshot
                takeScreenshot();

                // setup service status
                setupServiceStatus( !service_active );

                // Wait for a delay for takeScreenshot() to finish his job
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // info
                        Log.i(DEBUG_TAG, "[configureButtons::button_start::onClick] DELAY_SCREENSHOT elapsed");

                        // load latest screenshot
                        latest_screenshot_bitmap = loadScreenshotBitmap();

                        // set bitmap as background
                        if (latest_screenshot_bitmap != null) {
                            image_view.setImageBitmap(latest_screenshot_bitmap);
                        }

                        Bitmap.Config config;
                        if (latest_screenshot_bitmap.getConfig() != null) {
                            config = latest_screenshot_bitmap.getConfig();
                        }
                        else {
                            config = Bitmap.Config.ARGB_8888;
                        }

                        //Create bitmap of same size for drawing
                        bitmapDrawingPane = Bitmap.createBitmap(
                                latest_screenshot_bitmap.getWidth(),
                                latest_screenshot_bitmap.getHeight(),
                                config);
                        canvasDrawingPane = new Canvas(bitmapDrawingPane);
                        image_view.setImageBitmap(bitmapDrawingPane);

                        //
                        Log.i(DEBUG_TAG, "[configureButtons::button_start::onClick] Screenshot set as background");
                    }//run handler
                }, DELAY_SCREENSHOT);
            }
        });

        button_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(DEBUG_TAG, "[configureButtons::button_stop::onClick] Pressed 'Stop'");

                // setup service status
                //setupServiceStatus( !service_active );
                if (tts.isSpeaking()) {
                    tts.stop();
                }
            }
        });
    }

    private void setupServiceStatus(boolean status) {
        final Button button_start   = (Button) mLayout.findViewById(R.id.start);
        final Button button_stop    = (Button) mLayout.findViewById(R.id.stop);

        // change service status
        service_active = status;

        // change to full/minimized screen
        if (service_active == true) {
            //button_stop.setVisibility(View.VISIBLE);

            // change button color
            button_start.setBackgroundColor(Color.BLUE);
            button_start.setTextColor(Color.WHITE);
            //button_start.setLayoutParams (new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

            // set fullscreen
            setOverlayProperties(true);

            //
            canvasDrawingPane.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            image_view.setVisibility(View.VISIBLE);
        }
        else {
            //button_stop.setVisibility(View.GONE);

            // change button color
            button_start.setBackgroundColor(Color.LTGRAY);
            button_start.setTextColor(Color.BLACK);

            // set !fullscreen
            setOverlayProperties(false);

            //
            canvasDrawingPane.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            image_view.setVisibility(View.GONE);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override public boolean onTouch(View v, MotionEvent event) {
        //Log.i(DEBUG_TAG, "[onTouch] Triggered onTouch function");
        processMotionEvent(event, verbose_ontouch, v);
        return false;
    }

    void setOverlayProperties(boolean fullscreen) {
        WindowManager.LayoutParams lp;

        // first setup
        if (first_setup) {
            // set layout parameters
            lp = new WindowManager.LayoutParams();
            lp.type     = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            lp.format   = PixelFormat.TRANSLUCENT;
            lp.flags    |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.gravity  = Gravity.TOP | Gravity.LEFT;
        }
        else {
            // get existing LayoutParams
            lp = (WindowManager.LayoutParams) mLayout.getLayoutParams();
        }

        // set fullscreen
        if (fullscreen) {
            // set fullscreen layout
            lp.height   = WindowManager.LayoutParams.MATCH_PARENT;
            lp.width    = WindowManager.LayoutParams.MATCH_PARENT;
        }
        // set not fullscreen
        else {
            lp.width    = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height   = WindowManager.LayoutParams.WRAP_CONTENT;
        }

        // set parameters of layout
        mLayout.setLayoutParams(lp);

        // set parameters of window manager
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (!first_setup) wm.removeView(mLayout);
        wm.addView(mLayout, lp);

        // set first_setup to false
        first_setup = false;
    }

    void processMotionEvent (MotionEvent event, boolean print, View view) {
        if (print) printMotionEvent(event);

        // get actions
        previous_action = current_action;
        current_action = event.getAction();

        // get coordinates
        current_x = (int) event.getX();
        current_y = (int) event.getY();

        // threshold
        if (current_x < 0) current_x = 0;
        if (current_y < 0) current_y = 0;

        switch (current_action) {
            case MotionEvent.ACTION_DOWN:
                // store initial coordinates
                x0 = current_x;
                y0 = current_y;
                break;

            case MotionEvent.ACTION_MOVE:
                // draw rectangle
                drawRectangle();
                break;

            case MotionEvent.ACTION_UP :
                if (previous_action == MotionEvent.ACTION_MOVE) {
                    // store final coordinates
                    x1 = current_x;
                    y1 = current_y;

                    // show coordinates
                    /* Log.i(DEBUG_TAG, "[processMotionEvent] Touch action: MOVE + UP");
                    Log.i(DEBUG_TAG, "[processMotionEvent] x0 " + x0 + ", y0 " + y0 );
                    Log.i(DEBUG_TAG, "[processMotionEvent] x1 " + x1 + ", y1 " + y1 ); */


                    // ------------- UI
                    drawRectangle();


                    // ------------- OCR and TTS
                    if (latest_screenshot_bitmap != null) {
                        // Crop screenshot to selected area
                        Bitmap screenshot_bitmap_resized = resizeBitmap(latest_screenshot_bitmap);

                        // Recognize text and speak out loud
                        bitmapToSpeech(screenshot_bitmap_resized);
                    }
                    else {
                        Log.e(DEBUG_TAG, "[processMotionEvent] null bitmap");
                        tts.speak("No bitmap", TextToSpeech.QUEUE_ADD, null, "DEFAULT");
                    }

                    //
                    /*try { }
                    catch (java.lang.IllegalArgumentException e) {
                        e.printStackTrace();
                        Log.e(DEBUG_TAG,"Exception: " + e);
                        tts.speak("Errore", TextToSpeech.QUEUE_ADD, null, "DEFAULT");
                    }*/

                    // --- deactivate service

                    //final Button button_stop    = (Button) mLayout.findViewById(R.id.stop);
                    //final Button button_start   = (Button) mLayout.findViewById(R.id.start);
                    while ( tts.isSpeaking() ) {
                        //Log.i(DEBUG_TAG, ".");
                        //button_start.setVisibility(View.GONE);
                        //button_stop.setVisibility(View.VISIBLE);
                    }
                    //button_stop.setVisibility(View.GONE);
                    setupServiceStatus( !service_active );
                }
                break;

            default:
                Log.w(DEBUG_TAG, "[onTouch] Unknown action");;
        }//switch case
    }

    void printMotionEvent(MotionEvent event) {
        int current_action  = event.getAction();
        String current_action_string;

        switch (current_action) {
            case MotionEvent.ACTION_DOWN:
                current_action_string = "DOWN";
                break;

            case MotionEvent.ACTION_UP:
                current_action_string = "UP";
                break;

            case MotionEvent.ACTION_MOVE:
                current_action_string = "MOVE";
                break;

            default:
                current_action_string = String.valueOf(this.current_action);
                break;
        }

        //Log.i(DEBUG_TAG, "Action " + event.getAction() + " - x " + String.format("%.2f", event.getRawX()) + ", y " + String.format("%.2f", event.getRawY()) );
        Log.i(DEBUG_TAG, "[printMotionEvent] " + current_action_string + " - x " + String.format("%.2f", event.getRawX()) + ", y " + String.format("%.2f", event.getRawY()) );
        //Log.i(DEBUG_TAG, "[printMotionEvent] " + current_action_string + " - x " + event.getRawX() + ", y " + event.getRawY() );

        //
        /* final int pointer_count = event.getPointerCount();
        final int history_size = event.getHistorySize();

        for (int h = 0; h < history_size; h++) {
            for (int p = 0; p < pointer_count; p++) {
                //Log.i(DEBUG_TAG,"time: " + ev.getHistoricalEventTime(h) + ", pointer: " + ev.getPointerId(p) + ", x: " + ev.getHistoricalX(p, h) + ", y: " + ev.getHistoricalY(p, h) );
                Log.i(DEBUG_TAG,"(" + p +"," + h + "): " + "x " + String.format("%.2f", event.getHistoricalX(p, h)) + ", y " + String.format("%.2f", event.getHistoricalY(p, h)) );
            }
        }
        Log.i(DEBUG_TAG," "); */
    }

    void takeScreenshot() {
        Intent dialogIntent = new Intent(this, ScreenshotActivity.class);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(dialogIntent);
        Log.i(DEBUG_TAG, "[takeScreenshot] takeScreenshot() returned");
    }

    void deleteFilesInFolder(String folder) {
        File dir = new File(folder);
        if ( dir.isDirectory() ) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                new File(dir, children[i]).delete();
            }
            Log.i(DEBUG_TAG, "[deleteFilesInFolder] Deleted " + children.length + " files in folder: " + folder);
        }
    }

    Bitmap loadScreenshotBitmap() {
        File dir = new File(SCREENSHOTS_DIRECTORY); // get screenshots directory

        if ( dir.isDirectory() ) {
            String[] children = dir.list();         // get all files
            int Nscreenshots = children.length;     // get N of files
            Log.i(DEBUG_TAG, "[loadScreenshotBitmap] Found " + Nscreenshots + " screenshots");

            if (Nscreenshots > 0) {
                //String screenshot_filename = SCREENSHOTS_DIRECTORY + (Nscreenshots-1) + ".png";
                String screenshot_filename = SCREENSHOTS_DIRECTORY + "0" + ".png"; // get filename of first screenshot

                // get screenshot
                File screenshot_file = new File(screenshot_filename);

                if( screenshot_file.exists() ) {
                    Log.i(DEBUG_TAG, "[loadScreenshotBitmap] Loaded screenshot " +  screenshot_filename);
                    Bitmap screenshot_bitmap = BitmapFactory.decodeFile(screenshot_file.getAbsolutePath());
                    return screenshot_bitmap;
                }
                else {
                    Log.w(DEBUG_TAG, "[loadScreenshotBitmap] Screenshot does not exist.");
                }
            }
            else {
                Log.e(DEBUG_TAG, "[loadScreenshotBitmap] no screenshot found. Increase DELAY_SCREENSHOT!");
                tts.speak("No screenshot", TextToSpeech.QUEUE_ADD, null, "DEFAULT");
            }
        }
        else {
            Log.e(DEBUG_TAG, "[loadScreenshotBitmap] no screenshot directory found!");
        }
        return null;
    }

    Bitmap resizeBitmap(Bitmap original) {
        int x0_crop, y0_crop, width_crop, height_crop;

        if (x0<x1) {
            if (y0<y1) {
                //x0<x1, y0<y1
                x0_crop = x0;
                y0_crop = y0;
                width_crop  = x1-x0;
                height_crop = y1-y0;
            }
            else {
                //x0<x1, y0>y1
                x0_crop = x0;
                y0_crop = y1;
                width_crop  = x1-x0;
                height_crop = y0-y1;
            }
        }
        else {
            if (y0<y1) {
                //x0>x1, y0<y1
                x0_crop = x1;
                y0_crop = y0;
                width_crop  = x0-x1;
                height_crop = y1-y0;
            }
            else {
                //x0>x1, y0>y1
                x0_crop = x1;
                y0_crop = y1;
                width_crop  = x0-x1;
                height_crop = y0-y1;
            }
        }

        // threshold to screen size
        if ( y0_crop + height_crop <= original.getHeight() ) {}
        else {
            height_crop = original.getHeight() - y0_crop;
        }

        // resize
        Bitmap screenshot_bitmap_resized = Bitmap.createBitmap(
                original,
                x0_crop, y0_crop,
                width_crop, height_crop);

        // return
        return screenshot_bitmap_resized;
    }

    void bitmapToSpeech(Bitmap screenshot_bitmap) {
        // Create frame
        Frame screenshot_frame = new Frame.Builder().setBitmap(screenshot_bitmap).build();

        // Detect text
        SparseArray<TextBlock> text = text_recognizer.detect(screenshot_frame);

        // If text is available
        if (text.size() > 0) {

            // Speak out loud each item
            for (int i=0; i<text.size(); ++i) {
                TextBlock item = text.valueAt(i);

                // TTS
                if  (item != null && item.getValue() != null) {
                    // draw bounding box
                    /* RectF rect = new RectF( item.getBoundingBox() );
                    Log.i(DEBUG_TAG, rect.toString() );
                    canvasDrawingPane.drawRect(rect, paint);
                    imageResult.invalidate(); */

                    // get string
                    String current_string = item.getValue();

                    // remove newlines (typically due to visual text formatting) to make reading more fluid
                    if (remove_newlines)
                        current_string = current_string.replace("\n"," ");

                    // Speak the string
                    tts.speak(current_string, TextToSpeech.QUEUE_ADD, null, "DEFAULT");

                    //
                    Log.i(DEBUG_TAG, "[bitmapToSpeech] Text being spoken: " + current_string);
                    while ( tts.isSpeaking() ) {
                        // Log.i(DEBUG_TAG, "[bitmapToSpeech] TTS is speaking...");
                    }
                }
                else {
                    Log.w(DEBUG_TAG, "[bitmapToSpeech] Text data is null");
                }
            }
            Log.i(DEBUG_TAG, "[bitmapToSpeech] All selected text has been spoken");
        }
        else {
            Log.w(DEBUG_TAG, "[bitmapToSpeech] No text found");
            tts.speak("Nessun testo trovato", TextToSpeech.QUEUE_ADD, null, "DEFAULT");
        }
    }

    void drawRectangle() {
        // info
        if (verbose_ontouch) {
            Log.i(DEBUG_TAG, "[draw] x0 " + x0          + ", y0 " + y0 );
            Log.i(DEBUG_TAG, "[draw] xC " + current_x   + ", yC " + current_y );
        }

        // clear canvasDrawingPane
        canvasDrawingPane.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // draw rectangle
        canvasDrawingPane.drawRoundRect(x0, y0, current_x, current_y, 25, 25, paint);
        image_view.invalidate();
    }
}