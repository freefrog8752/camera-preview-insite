package com.cordovaplugincamerapreview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Base64;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Bundle;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import  java.io.FileOutputStream;
import java.io.File;
import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Exception;
import java.lang.Integer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CameraActivity extends Fragment {

  public interface CameraPreviewListener {
    void onPictureTaken(String originalPicture);
    void onPictureTakenError(String message);
  }

  private CameraPreviewListener eventListener;
  private static final String TAG = "CameraActivity";
  public FrameLayout mainLayout;
  public FrameLayout frameContainerLayout;

  private Preview mPreview;
  private boolean canTakePicture = true;

  private View view;
  private Camera.Parameters cameraParameters;
  private Camera mCamera;
  private int numberOfCameras;
  private int cameraCurrentlyLocked;

  // The first rear facing camera
  private int defaultCameraId;
  public String defaultCamera;
  public boolean tapToTakePicture;
  public boolean dragEnabled;

  public int width;
  public int height;
  public int x;
  public int y;
  public  String nameIndex = "";
  public String nameTs = "";
  public boolean pic_isdoc = false;
  public void setEventListener(CameraPreviewListener listener){
    eventListener = listener;
  }

  private String appResourcesPackage;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    appResourcesPackage = getActivity().getPackageName();

    // Inflate the layout for this fragment
    view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);
    createCameraPreview();
    return view;
  }

  public void setRect(int x, int y, int width, int height){
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  private void createCameraPreview(){
    if(mPreview == null) {
      setDefaultCameraId();

      //set box position and size
      FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
      layoutParams.setMargins(x, y, 0, 0);
      frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
      frameContainerLayout.setLayoutParams(layoutParams);

      //video view
      mPreview = new Preview(getActivity());
      mainLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
      mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
      mainLayout.addView(mPreview);
      mainLayout.setEnabled(false);

      final GestureDetector gestureDetector = new GestureDetector(getActivity().getApplicationContext(), new TapGestureDetector());

      getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          frameContainerLayout.setClickable(true);
          frameContainerLayout.setOnTouchListener(new View.OnTouchListener() {

            private int mLastTouchX;
            private int mLastTouchY;
            private int mPosX = 0;
            private int mPosY = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
              FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();


              boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
              if (event.getAction() != MotionEvent.ACTION_MOVE && isSingleTapTouch) {
                if (tapToTakePicture) {
                  takePicture(0, 0, 85,"0","0",false);
                }
                return true;
              } else {
                if (dragEnabled) {
                  int x;
                  int y;

                  switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                      if(mLastTouchX == 0 || mLastTouchY == 0) {
                        mLastTouchX = (int)event.getRawX() - layoutParams.leftMargin;
                        mLastTouchY = (int)event.getRawY() - layoutParams.topMargin;
                      }
                      else{
                        mLastTouchX = (int)event.getRawX();
                        mLastTouchY = (int)event.getRawY();
                      }
                      break;
                    case MotionEvent.ACTION_MOVE:

                      x = (int) event.getRawX();
                      y = (int) event.getRawY();

                      final float dx = x - mLastTouchX;
                      final float dy = y - mLastTouchY;

                      mPosX += dx;
                      mPosY += dy;

                      layoutParams.leftMargin = mPosX;
                      layoutParams.topMargin = mPosY;

                      frameContainerLayout.setLayoutParams(layoutParams);

                      // Remember this touch position for the next move event
                      mLastTouchX = x;
                      mLastTouchY = y;

                      break;
                    default:
                      break;
                  }
                }
              }
              return true;
            }
          });
        }
      });
    }
  }

  private void setDefaultCameraId(){
    // Find the total number of cameras available
    numberOfCameras = Camera.getNumberOfCameras();

    int camId = defaultCamera.equals("front") ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

    // Find the ID of the default camera
    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
    for (int i = 0; i < numberOfCameras; i++) {
      Camera.getCameraInfo(i, cameraInfo);
      if (cameraInfo.facing == camId) {
        defaultCameraId = camId;
        break;
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    mCamera = Camera.open(defaultCameraId);

    if (cameraParameters != null) {
      mCamera.setParameters(cameraParameters);
    }

    cameraCurrentlyLocked = defaultCameraId;

    if(mPreview.mPreviewSize == null){
      mPreview.setCamera(mCamera, cameraCurrentlyLocked);
    } else {
      mPreview.switchCamera(mCamera, cameraCurrentlyLocked);
      mCamera.startPreview();
    }

    Log.d(TAG, "cameraCurrentlyLocked:" + cameraCurrentlyLocked);

    final FrameLayout frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));

    ViewTreeObserver viewTreeObserver = frameContainerLayout.getViewTreeObserver();

    if (viewTreeObserver.isAlive()) {
      viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
          frameContainerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
          frameContainerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
          final RelativeLayout frameCamContainerLayout = (RelativeLayout) view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage));

          FrameLayout.LayoutParams camViewLayout = new FrameLayout.LayoutParams(frameContainerLayout.getWidth(), frameContainerLayout.getHeight());
          camViewLayout.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
          frameCamContainerLayout.setLayoutParams(camViewLayout);
        }
      });
    }
  }

  @Override
  public void onPause() {
    super.onPause();

    // Because the Camera object is a shared resource, it's very important to release it when the activity is paused.
    if (mCamera != null) {
      setDefaultCameraId();
      mPreview.setCamera(null, -1);
      mCamera.setPreviewCallback(null);
      mCamera.release();
      mCamera = null;
    }
  }

  public Camera getCamera() {
    return mCamera;
  }

  public void switchCamera() {
    // check for availability of multiple cameras
    if (numberOfCameras == 1) {
      //There is only one camera available
    }else{
      Log.d(TAG, "numberOfCameras: " + numberOfCameras);

      // OK, we have multiple cameras. Release this camera -> cameraCurrentlyLocked
      if (mCamera != null) {
        mCamera.stopPreview();
        mPreview.setCamera(null, -1);
        mCamera.release();
        mCamera = null;
      }

      Log.d(TAG, "cameraCurrentlyLocked := " + Integer.toString(cameraCurrentlyLocked));
      try {
        cameraCurrentlyLocked = (cameraCurrentlyLocked + 1) % numberOfCameras;
        Log.d(TAG, "cameraCurrentlyLocked new: " + cameraCurrentlyLocked);
      } catch (Exception exception) {
        Log.d(TAG, exception.getMessage());
      }

      // Acquire the next camera and request Preview to reconfigure parameters.
      mCamera = Camera.open(cameraCurrentlyLocked);

      if (cameraParameters != null) {
        Log.d(TAG, "camera parameter not null");

        // Check for flashMode as well to prevent error on frontward facing camera.
        List<String> supportedFlashModesNewCamera = mCamera.getParameters().getSupportedFlashModes();
        String currentFlashModePreviousCamera = cameraParameters.getFlashMode();
        if (supportedFlashModesNewCamera != null && supportedFlashModesNewCamera.contains(currentFlashModePreviousCamera)) {
          Log.d(TAG, "current flash mode supported on new camera. setting params");
         /* mCamera.setParameters(cameraParameters);
            The line above is disabled because parameters that can actually be changed are different from one device to another. Makes less sense trying to reconfigure them when changing camera device while those settings gan be changed using plugin methods.
         */
        } else {
          Log.d(TAG, "current flash mode NOT supported on new camera");
        }

      } else {
        Log.d(TAG, "camera parameter NULL");
      }

      mPreview.switchCamera(mCamera, cameraCurrentlyLocked);

      mCamera.startPreview();
    }
  }

  public void setCameraParameters(Camera.Parameters params) {
    cameraParameters = params;

    if (mCamera != null && cameraParameters != null) {
      mCamera.setParameters(cameraParameters);
    }
  }

  public boolean hasFrontCamera(){
    return getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
  }

  public Bitmap cropBitmap(Bitmap bitmap, Rect rect){
    int w = rect.right - rect.left;
    int h = rect.bottom - rect.top;
    Bitmap ret = Bitmap.createBitmap(w, h, bitmap.getConfig());
    Canvas canvas= new Canvas(ret);
    canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);
    return ret;
  }

  public static Bitmap rotateBitmap(Bitmap source, float angle, boolean mirror) {
    Matrix matrix = new Matrix();
    if (mirror) {
      matrix.preScale(-1.0f, 1.0f);
    }
    matrix.postRotate(angle);
    return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
  }

  ShutterCallback shutterCallback = new ShutterCallback(){
    public void onShutter(){
      // do nothing, availabilty of this callback causes default system shutter sound to work
    }
  };

  PictureCallback jpegPictureCallback = new PictureCallback(){
    public void onPictureTaken(byte[] data, Camera arg1){
      Log.d(TAG, "CameraPreview jpegPictureCallback");
      Camera.Parameters params = mCamera.getParameters();
      System.out.print("CameraPreview jpegPictureCallback");
      try {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0,data.length);
        Log.d(TAG, mPreview.getDisplayOrientation()+"");
        bitmap = rotateBitmap(bitmap, mPreview.getDisplayOrientation(), cameraCurrentlyLocked == Camera.CameraInfo.CAMERA_FACING_FRONT);
      
        if(pic_isdoc){
          bitmap = convertToBlackWhite(bitmap);
        }
        String name = nameIndex+".jpg";
        String thumbname = nameIndex+"_thumb.jpg";
        String filepath = getActivity().getApplicationContext().getFilesDir().toString()+"/answerImg/"+nameTs;
        Log.d("ttttt",filepath+"/"+name);
        System.out.print(filepath+"/"+name);
        File dir = new File(filepath);
        if(!dir.exists()){
          dir.mkdirs();
        }

        File file = new File(filepath+"/"+name);
        File filethumb = new File(filepath+"/"+thumbname);

        if(!file.exists()){
          file.createNewFile();
        }
//        byte[] byteArray = outputStream.toByteArray();
//        String encodedImage = Base64.encodeToString(byteArray, Base64.NO_WRAP);
        FileOutputStream fos = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG,90, fos);
        fos.flush();
        fos.close();
        Log.d("ttttt",filepath+"/"+name);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scalewidth = 0.25f;
        float scaleheight = 0.25f;
        Matrix matrix = new Matrix();
        matrix.postScale(scalewidth,scaleheight);
        Bitmap newbm = Bitmap.createBitmap(bitmap,0, 0 ,width,height,matrix,true);
        FileOutputStream fosthumb = new FileOutputStream(filethumb);
        newbm.compress(CompressFormat.JPEG,90,fosthumb);
        fosthumb.flush();
        fosthumb.close();
        bitmap.recycle();
        System.gc();
        eventListener.onPictureTaken(filepath+"/"+name);
        
        Log.d(TAG, "CameraPreview pictureTakenHandler called back");
      } catch (OutOfMemoryError e) {
        // most likely failed to allocate memory for rotateBitmap
        Log.d(TAG, "CameraPreview OutOfMemoryError");
        // failed to allocate memory
        eventListener.onPictureTakenError("Picture too large (memory)");
      } catch (Exception e) {
        Log.d(TAG, "CameraPreview onPictureTaken general exception");
      } finally {
        canTakePicture = true;
        mCamera.startPreview();
      }
    }
  };

  private Camera.Size getOptimalPictureSize(final int width, final int height, final Camera.Size previewSize, final List<Camera.Size> supportedSizes){
    /*
      get the supportedPictureSize that:
      - has the closest aspect ratio to the preview aspect ratio
      - has picture.width and picture.height closest to width and height
      - has the highest supported picture width and height up to 2 Megapixel if width == 0 || height == 0
    */
    Camera.Size size = mCamera.new Size(width, height);

    // convert to landscape if necessary
    if (size.width < size.height) {
      int temp = size.width;
      size.width = size.height;
      size.height = temp;
    }

    double previewAspectRatio  = (double)previewSize.width / (double)previewSize.height;

    if (previewAspectRatio < 1.0) {
      // reset ratio to landscape
      previewAspectRatio = 1.0 / previewAspectRatio;
    }

    Log.d(TAG, "CameraPreview previewAspectRatio " + previewAspectRatio);

    double aspectTolerance = 0.1;
    double bestDifference = Double.MAX_VALUE;

    for (int i = 0; i < supportedSizes.size(); i++) {
      Camera.Size supportedSize = supportedSizes.get(i);
      double difference = Math.abs(previewAspectRatio - ((double)supportedSize.width / (double)supportedSize.height));

      if (difference < bestDifference - aspectTolerance) {
        // better aspectRatio found
        if ((width != 0 && height != 0) || (supportedSize.width * supportedSize.height < 2048 * 1024)) {
          size.width = supportedSize.width;
          size.height = supportedSize.height;
          bestDifference = difference;
        }
      } else if (difference < bestDifference + aspectTolerance) {
        // same aspectRatio found (within tolerance)
        if (width == 0 || height == 0) {
          // set highest supported resolution below 2 Megapixel
          if ((size.width < supportedSize.width) && (supportedSize.width * supportedSize.height < 2048 * 1024)) {
            size.width = supportedSize.width;
            size.height = supportedSize.height;
          }
        } else {
          // check if this pictureSize closer to requested width and height
          if (Math.abs(width * height - supportedSize.width * supportedSize.height) < Math.abs(width * height - size.width * size.height)) {
            size.width = supportedSize.width;
            size.height = supportedSize.height;
          }
        }
      }
    }
    Log.d(TAG, "CameraPreview optimalPictureSize " + size.width + 'x' + size.height);
    return size;
  }

public static Bitmap convertToBlackWhite(Bitmap switchBitmap) {
         int width = bmp.getWidth();  
        int height = bmp.getHeight();  
        int[] pixels = new int[width * height];  
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);  
        int alpha = 0xFF << 24;  
        for (int i = 0; i < height; i++) {  
            for (int j = 0; j < width; j++) {  
                int grey = pixels[width * i + j];  
  
                // 分离三原色  
                int red = ((grey & 0x00FF0000) >> 16);  
                int green = ((grey & 0x0000FF00) >> 8);  
                int blue = (grey & 0x000000FF);  
  
                // 转化成灰度像素  
                grey = (int) (red * 0.3 + green * 0.59 + blue * 0.11);  
                grey = alpha | (grey << 16) | (grey << 8) | grey;  
                pixels[width * i + j] = grey;  
            }  
        }  
        // 新建图片  
        Bitmap newbmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);  
        newbmp.setPixels(pixels, 0, width, 0, 0, width, height);  
        // Bitmap resizeBmp = ThumbnailUtils.extractThumbnail(newbmp, width,  
        //         height);  
  
        return newbmp;  
    }


  public void takePicture(final int width, final int height, final int quality,String index, String ts,boolean isdoc){
    Log.d(TAG, "CameraPreview takePicture width: " + width + ", height: " + height + ", quality: " + quality +",index:"+index+",ts:"+ts );
    System.out.print("CameraPreview takePicture width: " + width + ", height: " + height + ", quality: " + quality +",index:"+index+",ts:"+ts );
    if(mPreview != null) {
      if(!canTakePicture){
        return;
      }
      nameIndex = index;
      nameTs = ts;
      canTakePicture = false;
      pic_isdoc = isdoc;
      new Thread() {
        public void run() {
          Camera.Parameters params = mCamera.getParameters();

          Camera.Size size = getOptimalPictureSize(width, height, params.getPreviewSize(), params.getSupportedPictureSizes());
          params.setPictureSize(size.width, size.height);
          params.setJpegQuality(quality);

          mCamera.setParameters(params);
          mCamera.takePicture(shutterCallback, null, jpegPictureCallback);
        }
      }.start();
    } else {
      canTakePicture = true;
    }
  }
}
