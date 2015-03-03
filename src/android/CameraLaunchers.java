

package com.vmbc.customcamera;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.ExifHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.RelativeLayout;

/**
 * This class launches the camera view, allows the user to take a picture,
 * closes the camera view, and returns the captured image. When the camera view
 * is closed, the screen displayed before the camera view was shown is
 * redisplayed.
 */
@SuppressLint("NewApi")
public class CameraLaunchers extends org.apache.cordova.camera.CameraLauncher {

	private static final String GET_PICTURE = "getPicture";
	private static final String TAKE_PICTURE = "takePicture";
	private static final String DESTROY_CAMERA = "destroyCamera";

	@SuppressWarnings("unused")
	private int mQuality;
	private int targetWidth;
	private int targetHeight;
	private int targetX;
	private int targetY;

	private Uri imageUri;
	private File photo;

	public String callbackId;
	private int numPics;

	private Activity activity;
	private ViewGroup root;

	private Camera mCamera;

	private CameraPreview mPreview;
	private CallbackContext callbackContext;

	/**
	* Constructor.
	*/
	public CameraLaunchers() {
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		activity = cordova.getActivity();
		root = (ViewGroup) webView.getParent();
	}

	/**
	* Executes the request and returns PluginResult.
	* 
	* @param action
	*            The action to execute.
	* @param args
	*            JSONArry of arguments for the plugin.
	* @param callbackContext
	*            The callback id used when calling back into JavaScript.
	* @return A PluginResult object with a status and message.
	*/
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {

		this.callbackContext = callbackContext;

		if (action.equals(TAKE_PICTURE)) {
			this.targetHeight = 0;
			this.targetWidth = 0;
			this.mQuality = 80;
			this.targetX = 0;
			this.targetY = 0;

			this.mQuality = ((JSONObject) args.get(0)).optInt("quality");
			this.targetWidth = ((JSONObject) args.get(0)).optInt("targetWidth");
			this.targetHeight = ((JSONObject) args.get(0))
					.optInt("targetHeight");
			this.targetX = ((JSONObject) args.get(0)).optInt("targetX");
			this.targetY = ((JSONObject) args.get(0)).optInt("targetY");

			// If the user specifies a 0 or smaller width/height
			// make it -1 so later comparisons succeed
			if (this.targetWidth < 1) {
				this.targetWidth = -1;
			}
			if (this.targetHeight < 1) {
				this.targetHeight = -1;
			}
			activity.runOnUiThread(new Runnable() {

				public void run() {
					takePicture();
				}
			});

			callbackContext.success();
			return true;
		} else if (action.equals(GET_PICTURE)) {
			// get an image from the camera
			mCamera.autoFocus(new AutoFocusCallback() {

				public void onAutoFocus(boolean success, Camera camera) {
					mCamera.takePicture(null, null, mPicture);
				}
			});
			return true;
		} else if (action.equals(DESTROY_CAMERA)) {
			try {
				activity.runOnUiThread(new Runnable() {

					public void run() {
						destryoCamera();
					}
				});
				callbackContext.success();
			} catch (Exception e) {
				callbackContext.error(e.getMessage());
				;
			}
			return true;
		}
		return false;
	}

	private PictureCallback mPicture = new PictureCallback() {

		@SuppressWarnings("deprecation")
		public void onPictureTaken(byte[] data, Camera camera) {

			File pictureFile = new File(imageUri.getPath());

			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();
			} catch (FileNotFoundException e) {
				// Log.d(TAG, "File not found: " + e.getMessage());
			} catch (IOException e) {
				// Log.d(TAG, "Error accessing file: " + e.getMessage());

			}
			try {
				// Create an ExifHelper to save the exif data that is lost
				// during compression
				ExifHelper exif = new ExifHelper();
				exif.createInFile(getTempDirectoryPath(activity
						.getApplicationContext()) + "/Pic.jpg");
				exif.readExifData();

				// Read in bitmap of captured image
				Bitmap bitmap;
				try {
					bitmap = android.provider.MediaStore.Images.Media
							.getBitmap(activity.getContentResolver(), imageUri);
				} catch (FileNotFoundException e) {
					android.content.ContentResolver resolver = activity
							.getContentResolver();
					bitmap = android.graphics.BitmapFactory
							.decodeStream(resolver.openInputStream(imageUri));
				}

				// bitmap = scaleBitmap(bitmap);

				int orientation = getCameraPhotoOrientation(activity);
				bitmap = RotateBitmap(bitmap, orientation);

				int rate = 100;
				ByteArrayOutputStream byteArrayOutputStream;
				byte[] byteArray;
				do {
					byteArrayOutputStream = new ByteArrayOutputStream();
					bitmap.compress(Bitmap.CompressFormat.JPEG, rate,
							byteArrayOutputStream);
					byteArray = byteArrayOutputStream.toByteArray();
					rate -= 5;
				} while (byteArrayOutputStream.size() > 50000 && rate > 10);

				String encoded = Base64.encodeToString(byteArray,
						Base64.DEFAULT);
				Log.i("BASE64-IMG", encoded);
				System.out.println(encoded);
				callbackContext.success(encoded);

				bitmap.recycle();
				bitmap = null;
				System.gc();

				checkForDuplicateImage();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mPreview.refreshPreview();
		}
	};

	// --------------------------------------------------------------------------
	// LOCAL METHODS
	// --------------------------------------------------------------------------

	/**
	* Take a picture with the camera. When an image is captured or the camera
	* view is cancelled, the result is returned in
	* CordovaActivity.onActivityResult, which forwards the result to
	* this.onActivityResult.
	* 
	* The image can either be returned as a base64 string or a URI that points
	* to the file. To display base64 string in an img tag, set the source to:
	* img.src="data:image/jpeg;base64,"+result; or to display URI in an img tag
	* img.src=result;
	* 
	*/
	public void takePicture() {
		if (mCamera != null) {
			return;
		}

		// Save the number of images currently on disk for later
		this.numPics = queryImgDB().getCount();

		this.photo = createCaptureFile();
		this.imageUri = Uri.fromFile(photo);

		// window layout
		RelativeLayout windowLayer = new RelativeLayout(activity);
		windowLayer.setPadding(0, 0, 0, 0);
		LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT);
		layoutParams.gravity = Gravity.TOP | Gravity.START;
		windowLayer.setLayoutParams(layoutParams);

		((ViewGroup) webView.getParent()).removeView(webView);
		root.removeAllViews();
		FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
				webView.getLayoutParams());
		webView.setLayoutParams(webParams);
		windowLayer.addView(webView);

		mCamera = getCameraInstance();

		if (mPreview == null) {
			// Create a Preview and set it as the content of activity.
			mPreview = new CameraPreview(activity, mCamera);
		} else {
			((ViewGroup) mPreview.getParent()).removeView(mPreview);
		}
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				targetWidth, targetHeight);
		lp.topMargin = targetY;
		lp.leftMargin = targetX;
		mPreview.setLayoutParams(lp);
		windowLayer.addView(mPreview);

		root.addView(windowLayer);
	}

	private void destryoCamera() {
		if (mCamera == null) {
			return;
		}

		this.numPics = 0;

		this.photo = null;
		this.imageUri = null;

		((ViewGroup) webView.getParent()).removeView(webView);
		root.removeAllViews();
		root.addView(webView);

		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;

		mPreview = null;

	}

	/**
	* Create a file in the applications temporary directory based upon the
	* supplied encoding.
	* 
	* @return a File object pointing to the temporary picture
	*/
	private File createCaptureFile() {
		File photo = new File(getTempDirectoryPath(this.cordova.getActivity()
				.getApplicationContext()), "Pic.jpg");
		return photo;
	}

	private int getCameraPhotoOrientation(Context context) {
		// Fix orientation issue
		int count = Camera.getNumberOfCameras();
		int cameraId = count == 1 ? CameraInfo.CAMERA_FACING_BACK
				: CameraInfo.CAMERA_FACING_FRONT;
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
		int rotation = ((Activity) context).getWindowManager()
				.getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;
		case Surface.ROTATION_90:
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		} else { // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		return result;
	}

	/**
	* Scales the bitmap according to the requested size.
	* 
	* @param bitmap
	*            The bitmap to scale.
	* @return Bitmap A new Bitmap object of the same bitmap after scaling.
	*/
	public Bitmap scaleBitmap(Bitmap bitmap) {
		int newWidth = this.targetWidth;
		int newHeight = this.targetHeight;
		int origWidth = bitmap.getWidth();
		int origHeight = bitmap.getHeight();

		// If no new width or height were specified return the original bitmap
		if (newWidth <= 0 && newHeight <= 0) {
			return bitmap;
		}
		// Only the width was specified
		else if (newWidth > 0 && newHeight <= 0) {
			newHeight = (newWidth * origHeight) / origWidth;
		}
		// only the height was specified
		else if (newWidth <= 0 && newHeight > 0) {
			newWidth = (newHeight * origWidth) / origHeight;
		}
		// If the user specified both a positive width and height
		// (potentially different aspect ratio) then the width or height is
		// scaled so that the image fits while maintaining aspect ratio.
		// Alternatively, the specified width and height could have been
		// kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
		// would result in whitespace in the new image.
		else {
			double newRatio = newWidth / (double) newHeight;
			double origRatio = origWidth / (double) origHeight;

			if (origRatio > newRatio) {
				newHeight = (newWidth * origHeight) / origWidth;
			} else if (origRatio < newRatio) {
				newWidth = (newHeight * origWidth) / origHeight;
			}
		}

		return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
	}

	/**
	* Creates a cursor that can be used to determine how many images we have.
	* 
	* @return a cursor
	*/
	private Cursor queryImgDB() {
		return this.cordova
				.getActivity()
				.getContentResolver()
				.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						new String[] { MediaStore.Images.Media._ID }, null,
						null, null);
	}

	/**
	* Used to find out if we are in a situation where the Camera Intent adds to
	* images to the content store. If we are using a FILE_URI and the number of
	* images in the DB increases by 2 we have a duplicate, when using a
	* DATA_URL the number is 1.
	*/
	private void checkForDuplicateImage() {
		int diff = 2;
		Cursor cursor = queryImgDB();
		int currentNumOfImages = cursor.getCount();

		// delete the duplicate file if the difference is 2 for file URI or 1
		// for Data URL
		if ((currentNumOfImages - numPics) == diff) {
			cursor.moveToLast();
			int id = Integer.valueOf(cursor.getString(cursor
					.getColumnIndex(MediaStore.Images.Media._ID))) - 1;
			Uri uri = Uri.parse(MediaStore.Images.Media.EXTERNAL_CONTENT_URI
					+ "/" + id);
			this.cordova.getActivity().getContentResolver()
					.delete(uri, null, null);
		}
	}

	/**
	* Determine if we can use the SD Card to store the temporary file. If not
	* then use the internal cache directory.
	* 
	* @return the absolute path of where to store the file
	*/
	private String getTempDirectoryPath(Context ctx) {
		File cache = null;

		// SD Card Mounted
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			cache = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ "/Android/data/"
					+ ctx.getPackageName() + "/cache/");
		}
		// Use internal storage
		else {
			cache = ctx.getCacheDir();
		}

		// Create the cache directory if it doesn't exist
		if (!cache.exists()) {
			cache.mkdirs();
		}

		return cache.getAbsolutePath();
	}

	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			int count = Camera.getNumberOfCameras();
			int cameraId = count == 1 ? CameraInfo.CAMERA_FACING_BACK
					: CameraInfo.CAMERA_FACING_FRONT;
			c = Camera.open(cameraId); // attempt to get a
										// Camera
										// instance
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	public static Bitmap RotateBitmap(Bitmap source, float angle) {
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		matrix.preScale(-1, 1);
		return Bitmap.createBitmap(source, 0, 0, source.getWidth(),
				source.getHeight(), matrix, true);
	}
}
