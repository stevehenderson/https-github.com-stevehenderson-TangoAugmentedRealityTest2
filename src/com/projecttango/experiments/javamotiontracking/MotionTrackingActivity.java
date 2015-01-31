/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.projecttango.experiments.javamotiontracking;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * 
 * Modified Main Activity class from the Original Tango SDK  Motion Tracking API Sample. 
 * 
 * Creates a GLSurfaceView for the OpenGL scene, which displays a cube
 * Then adds a SurfaceView for the camera image.  The surface is connected 
 * to the Tango camera.  This is neccesary if one wants to get point cloud
 * data from the Tango AND use the camera for video-see through Augmented Reality.
 * 
 * Lessons learned:  Ensure your onPause and onResume actions are handled correctly
 * in terms of disconnecting and reconnecting the Tango!!  If the Tango is not
 * disconnected and reconnected properly, you will get a black background and
 * may think the issue is something else.
 * 
 */
public class MotionTrackingActivity extends Activity implements View.OnClickListener, SurfaceHolder.Callback  {

	private static final String TAG = MotionTrackingActivity.class.getSimpleName();
	private static final int SECS_TO_MILLISECS = 1000;
	private Tango mTango;
	private TangoConfig mConfig;
	private TextView mDeltaTextView;
	private TextView mPoseCountTextView;
	private TextView mPoseTextView;
	private TextView mQuatTextView;
	private TextView mPoseStatusTextView;
	private TextView mTangoServiceVersionTextView;
	private TextView mApplicationVersionTextView;
	private TextView mTangoEventTextView;
	private Button mMotionResetButton;
	private float mPreviousTimeStamp;
	private int mPreviousPoseStatus;
	private int count;
	private float mDeltaTime;
	private boolean mIsAutoRecovery;
	//private GLClearRenderer mRenderer;
	private MainRenderer mMainRenderer;
	private GLClearRenderer mClearRenderer;
	
	private GLSurfaceView mGLView;
	private SurfaceHolder surfaceHolder;
	private SurfaceView surfaceView;

	boolean first_initialized = false;


	/*
	 * Static primitive to set the OPENGL Version.
	 * Values = {1.0, 2.0}
	 */
	private final static double OPENGL_VERSION = 2.0;

	/**
	 * Set up the activity using OpenGL 10
	 */
	private void setUpOpenGL10() {

		///////////////////////
		//Create GLSurface
		///////////////////////
		// OpenGL view where all of the graphics are drawn
		//mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
		mGLView = new GLSurfaceView(this);
		mGLView.setEGLConfigChooser(8,8,8,8,16,0);
		surfaceHolder = mGLView.getHolder();
		surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
		// Configure OpenGL renderer
		mClearRenderer = new GLClearRenderer();
		//mGLView.setEGLContextClientVersion(2);
		mGLView.setRenderer(mClearRenderer);
		setContentView(mGLView);

		////////////////////////////////////
		// Instantiate the Tango service
		///////////////////////////////////
		mTango = new Tango(this);
		// Create a new Tango Configuration and enable the MotionTrackingActivity API
		mConfig = new TangoConfig();
		mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);

		try {
			setTangoListeners();
		} catch (TangoErrorException e) {
			Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
		} catch (SecurityException e) {
			Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
					Toast.LENGTH_SHORT).show();
		}

		//////////////////////////
		// Create Camera Surface
		//////////////////////////
		surfaceView = new SurfaceView(this);
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback(this);
		addContentView( surfaceView, new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );

		/////////////////////////
		//Create UI Objects 
		////////////////////////
		LayoutInflater inflater = getLayoutInflater();
		View tmpView;
		tmpView = inflater.inflate(R.layout.activity_motion_tracking, null);
		getWindow().addContentView(tmpView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
				ViewGroup.LayoutParams.FILL_PARENT)); 

		Intent intent = getIntent();
		mIsAutoRecovery = intent.getBooleanExtra(StartActivity.KEY_MOTIONTRACKING_AUTORECOVER,
				false);
		// Text views for displaying translation and rotation data
		mPoseTextView = (TextView) findViewById(R.id.pose);
		mQuatTextView = (TextView) findViewById(R.id.quat);
		mPoseCountTextView = (TextView) findViewById(R.id.posecount);
		mDeltaTextView = (TextView) findViewById(R.id.deltatime);
		mTangoEventTextView = (TextView) findViewById(R.id.tangoevent);

		// Buttons for selecting camera view and Set up button click listeners
		//NOTE:  BUTTONS ARE NOT USED IN THE CODE
		findViewById(R.id.first_person_button).setOnClickListener(this);
		findViewById(R.id.third_person_button).setOnClickListener(this);
		findViewById(R.id.top_down_button).setOnClickListener(this);

		// Button to reset motion tracking
		mMotionResetButton = (Button) findViewById(R.id.resetmotion);

		// Text views for the status of the pose data and Tango library versions
		mPoseStatusTextView = (TextView) findViewById(R.id.status);
		mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
		mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);

		// Set up button click listeners
		mMotionResetButton.setOnClickListener(this);

		// The Auto-Recovery ToggleButton sets a boolean variable to determine
		// if the
		// Tango service should automatically attempt to recover when
		// / MotionTrackingActivity enters an invalid state.
		if (mIsAutoRecovery) {
			mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
			Log.i(TAG, "Auto Reset On!!!");
		} else {
			mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, false);
			Log.i(TAG, "Auto Reset Off!!!");
		}
		
		mApplicationVersionTextView.setText("OpenGL 1.0");

	
		// Display the library version for debug purposes
		mTangoServiceVersionTextView.setText(mConfig.getString("tango_service_library_version"));

	}



	/**
	 * Set up the activity using OpenGL 20
	 */
	private void setUpOpenGL20() {

		///////////////////////
		//Create GLSurface
		///////////////////////
		// OpenGL view where all of the graphics are drawn
		//mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
		mGLView = new GLSurfaceView(this);
		mGLView.setEGLContextClientVersion(2);
		//mGLView.setZOrderOnTop(true);
		mGLView.setEGLConfigChooser(8,8,8,8,16,0);
		surfaceHolder = mGLView.getHolder();
		surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
		// Configure OpenGL renderer
		//mRenderer = new GLClearRenderer();
		mMainRenderer = new MainRenderer(this);


		mGLView.setRenderer(mMainRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		//setContentView(mGLView);

		////////////////////////////////////
		// Instantiate the Tango service
		///////////////////////////////////
		mTango = new Tango(this);
		// Create a new Tango Configuration and enable the MotionTrackingActivity API
		mConfig = new TangoConfig();
		mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);

		try {
			setTangoListeners();
		} catch (TangoErrorException e) {
			Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
		} catch (SecurityException e) {
			Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
					Toast.LENGTH_SHORT).show();
		}

		//////////////////////////
		// Create Camera Surface
		//////////////////////////
		surfaceView = new SurfaceView(this);
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback(this);
		//setContentView(mGLView);
		//addContentView( surfaceView, new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );
		setContentView(surfaceView);
		addContentView( mGLView, new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );



		/////////////////////////
		//Create UI Objects 
		////////////////////////
		LayoutInflater inflater = getLayoutInflater();
		View tmpView;
		tmpView = inflater.inflate(R.layout.activity_motion_tracking, null);
		getWindow().addContentView(tmpView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
				ViewGroup.LayoutParams.FILL_PARENT)); 

		Intent intent = getIntent();
		mIsAutoRecovery = intent.getBooleanExtra(StartActivity.KEY_MOTIONTRACKING_AUTORECOVER,
				false);
		// Text views for displaying translation and rotation data
		mPoseTextView = (TextView) findViewById(R.id.pose);
		mQuatTextView = (TextView) findViewById(R.id.quat);
		mPoseCountTextView = (TextView) findViewById(R.id.posecount);
		mDeltaTextView = (TextView) findViewById(R.id.deltatime);
		mTangoEventTextView = (TextView) findViewById(R.id.tangoevent);

		// Buttons for selecting camera view and Set up button click listeners
		//NOTE:  BUTTONS ARE NOT USED IN THE CODE
		findViewById(R.id.first_person_button).setOnClickListener(this);
		findViewById(R.id.third_person_button).setOnClickListener(this);
		findViewById(R.id.top_down_button).setOnClickListener(this);

		// Button to reset motion tracking
		mMotionResetButton = (Button) findViewById(R.id.resetmotion);

		// Text views for the status of the pose data and Tango library versions
		mPoseStatusTextView = (TextView) findViewById(R.id.status);
		mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
		mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);

		// Set up button click listeners
		mMotionResetButton.setOnClickListener(this);

		// The Auto-Recovery ToggleButton sets a boolean variable to determine
		// if the
		// Tango service should automatically attempt to recover when
		// / MotionTrackingActivity enters an invalid state.
		if (mIsAutoRecovery) {
			mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
			Log.i(TAG, "Auto Reset On!!!");
		} else {
			mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, false);
			Log.i(TAG, "Auto Reset Off!!!");
		}

		mApplicationVersionTextView.setText("OpenGL 2.0");
		// Display the library version for debug purposes
		mTangoServiceVersionTextView.setText(mConfig.getString("tango_service_library_version"));

	}


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(OPENGL_VERSION==1.0) setUpOpenGL10();
		if(OPENGL_VERSION==2.0) setUpOpenGL20();
		

	}



	private void motionReset() {
		mTango.resetMotionTracking();
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.i(TAG, "OnPause");
		try {
			mTango.disconnect();
			Log.i(TAG,"Pausing..TANGO disconnected");
		} catch (TangoErrorException e) {
			Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
		}

	}

	protected void onResume() {
		super.onResume();
		Log.i(TAG, "OnResume");

		try {
			//setTangoListeners();
		} catch (TangoErrorException e) {
			Log.e(TAG,e.toString());
		} catch (SecurityException e) {
			Log.e(TAG,e.toString());
		}
		try {           
			if(first_initialized)mTango.connect(mConfig);
		} catch (TangoOutOfDateException e) {
			Log.e(TAG,e.toString());
		} catch (TangoErrorException e) {
			Log.e(TAG,e.toString());
		}
		try {
			//setUpExtrinsics();
		} catch (TangoErrorException e) {
			Log.e(TAG,e.toString());
		} catch (SecurityException e) {
			Log.e(TAG,e.toString());
		}

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.first_person_button:
			//mRenderer.setFirstPersonView();
			break;
		case R.id.top_down_button:
			//mRenderer.setTopDownView();
			break;
		case R.id.third_person_button:
			//mRenderer.setThirdPersonView();
			break;
		case R.id.resetmotion:
			motionReset();
			break;
		default:
			Log.w(TAG, "Unknown button click");
			return;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return false; 
	}


	/**
	 * Set up the TangoConfig and the listeners for the Tango service, then begin using the Motion
	 * Tracking API. This is called in response to the user clicking the 'Start' Button.
	 */
	private void setTangoListeners() {
		// Lock configuration and connect to Tango
		// Select coordinate frame pair
		final ArrayList<TangoCoordinateFramePair> framePairs = 
				new ArrayList<TangoCoordinateFramePair>();
		framePairs.add(new TangoCoordinateFramePair(
				TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
				TangoPoseData.COORDINATE_FRAME_DEVICE));
		// Listen for new Tango data
		mTango.connectListener(framePairs, new OnTangoUpdateListener() {

			@Override
			public void onPoseAvailable(final TangoPoseData pose) {
				// Log whenever Motion Tracking enters a n invalid state
				if (!mIsAutoRecovery && (pose.statusCode == TangoPoseData.POSE_INVALID)) {
					Log.w(TAG, "Invalid State");
				}
				if (mPreviousPoseStatus != pose.statusCode) {
					count = 0;
				}
				count++;
				mPreviousPoseStatus = pose.statusCode;
				mDeltaTime = (float) (pose.timestamp - mPreviousTimeStamp) * SECS_TO_MILLISECS;
				mPreviousTimeStamp = (float) pose.timestamp;
				// Update the OpenGL renderable objects with the new Tango Pose
				// data
				float[] translation = pose.getTranslationAsFloats();

				//mGLView.requestRender();

				// Update the UI with TangoPose information
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						DecimalFormat threeDec = new DecimalFormat("0.000");
						String translationString = "[" + threeDec.format(pose.translation[0])
								+ ", " + threeDec.format(pose.translation[1]) + ", "
								+ threeDec.format(pose.translation[2]) + "] ";
						String quaternionString = "[" + threeDec.format(pose.rotation[0]) + ", "
								+ threeDec.format(pose.rotation[1]) + ", "
								+ threeDec.format(pose.rotation[2]) + ", "
								+ threeDec.format(pose.rotation[3]) + "] ";

						// Display pose data on screen in TextViews
						//Log.i(TAG,translationString);
						mPoseTextView.setText(translationString);
						mQuatTextView.setText(quaternionString);
						mPoseCountTextView.setText(Integer.toString(count));
						mDeltaTextView.setText(threeDec.format(mDeltaTime));
						if (pose.statusCode == TangoPoseData.POSE_VALID) {
							mPoseStatusTextView.setText(R.string.pose_valid);
						} else if (pose.statusCode == TangoPoseData.POSE_INVALID) {
							mPoseStatusTextView.setText(R.string.pose_invalid);
						} else if (pose.statusCode == TangoPoseData.POSE_INITIALIZING) {
							mPoseStatusTextView.setText(R.string.pose_initializing);
						} else if (pose.statusCode == TangoPoseData.POSE_UNKNOWN) {
							mPoseStatusTextView.setText(R.string.pose_unknown);
						}
					}
				});
			}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData arg0) {
				// We are not using TangoXyzIjData for this application
			}

			@Override
			public void onTangoEvent(final TangoEvent event) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mTangoEventTextView.setText(event.eventKey + ": " + event.eventValue);
					}
				});
			}
		});
	}


	private void setUpExtrinsics() {
		// Get device to imu matrix.
		TangoPoseData device2IMUPose = new TangoPoseData();
		TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
		framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
		framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
		device2IMUPose = mTango.getPoseAtTime(0.0, framePair);
		// mRenderer.getModelMatCalculator().SetDevice2IMUMatrix(
		//         device2IMUPose.getTranslationAsFloats(), device2IMUPose.getRotationAsFloats());

		// Get color camera to imu matrix.
		TangoPoseData color2IMUPose = new TangoPoseData();
		framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
		framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
		color2IMUPose = mTango.getPoseAtTime(0.0, framePair);

		// mRenderer.getModelMatCalculator().SetColorCamera2IMUMatrix(
		//        color2IMUPose.getTranslationAsFloats(), color2IMUPose.getRotationAsFloats());
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Surface surface = holder.getSurface();
		if (surface.isValid()) {
			TangoConfig config = new TangoConfig();
			config =  mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
			mTango.connectSurface(0, surface);
			first_initialized=true;
			mTango.connect(config);

		}

	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mTango.disconnectSurface(0);

	}

}
