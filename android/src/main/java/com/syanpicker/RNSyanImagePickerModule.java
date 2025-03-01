
package com.syanpicker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;


import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.PictureConfig;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.config.SelectModeConfig;
import com.luck.picture.lib.engine.CompressFileEngine;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.interfaces.OnKeyValueResultCallbackListener;
import com.luck.picture.lib.interfaces.OnResultCallbackListener;
import com.luck.picture.lib.utils.PictureFileUtils;
import com.luck.picture.lib.utils.SdkVersionUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import top.zibin.luban.Luban;
import top.zibin.luban.OnNewCompressListener;

public class RNSyanImagePickerModule extends ReactContextBaseJavaModule {

    private static String SY_SELECT_IMAGE_FAILED_CODE = "0"; // 失败时，Promise用到的code

    private final ReactApplicationContext reactContext;

    private List<LocalMedia> selectList = new ArrayList<>();

    private Callback mPickerCallback; // 保存回调

    private Promise mPickerPromise; // 保存Promise

    private ReadableMap cameraOptions; // 保存图片选择/相机选项

    public RNSyanImagePickerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(mActivityEventListener);
    }

    @Override
    public String getName() {
        return "RNSyanImagePicker";
    }

    @ReactMethod
    public void showImagePicker(ReadableMap options, Callback callback) {
        this.cameraOptions = options;
        this.mPickerPromise = null;
        this.mPickerCallback = callback;
        this.openImagePicker();
    }

    @ReactMethod
    public void asyncShowImagePicker(ReadableMap options, Promise promise) {
        this.cameraOptions = options;
        this.mPickerCallback = null;
        this.mPickerPromise = promise;
        this.openImagePicker();
    }

    @ReactMethod
    public void openCamera(ReadableMap options, Callback callback) {
        this.cameraOptions = options;
        this.mPickerPromise = null;
        this.mPickerCallback = callback;
        this.openCamera();
    }

    @ReactMethod
    public void asyncOpenCamera(ReadableMap options, Promise promise) {
        this.cameraOptions = options;
        this.mPickerCallback = null;
        this.mPickerPromise = promise;
        this.openCamera();
    }

    /**
     * 缓存清除
     * 包括裁剪和压缩后的缓存，要在上传成功后调用，注意：需要系统sd卡权限
     */
    @ReactMethod
    public void deleteCache() {
        Activity currentActivity = getCurrentActivity();
        PictureFileUtils.deleteAllCacheDirFile(currentActivity);
    }

    /**
     * 移除选中的图片
     * index 要移除的图片下标
     */
    @ReactMethod
    public void removePhotoAtIndex(int index) {
        if (selectList != null && selectList.size() > index) {
            selectList.remove(index);
        }
    }

    /**
     * 移除所有选中的图片
     */
    @ReactMethod
    public void removeAllPhoto() {
        if (selectList != null) {
            //selectList.clear();
            selectList = null;
        }
    }

    @ReactMethod
    public void openVideo(ReadableMap options, Callback callback) {
        this.cameraOptions = options;
        this.mPickerPromise = null;
        this.mPickerCallback = callback;
        this.openVideo();
    }

    @ReactMethod
    public void openVideoPicker(ReadableMap options, Callback callback) {
        this.cameraOptions = options;
        this.mPickerPromise = null;
        this.mPickerCallback = callback;
        this.openVideoPicker();
    }

    /**
     * 打开相册选择
     */
    private void openImagePicker() {
        int imageCount = this.cameraOptions.getInt("imageCount");
        boolean isCamera = this.cameraOptions.getBoolean("isCamera");
        boolean isCrop = this.cameraOptions.getBoolean("isCrop");
        int CropW = this.cameraOptions.getInt("CropW");
        int CropH = this.cameraOptions.getInt("CropH");
        boolean isGif = this.cameraOptions.getBoolean("isGif");
        boolean showCropCircle = this.cameraOptions.getBoolean("showCropCircle");
        boolean showCropFrame = this.cameraOptions.getBoolean("showCropFrame");
        boolean showCropGrid = this.cameraOptions.getBoolean("showCropGrid");
        boolean compress = this.cameraOptions.getBoolean("compress");
        boolean freeStyleCropEnabled = this.cameraOptions.getBoolean("freeStyleCropEnabled");
        boolean rotateEnabled = this.cameraOptions.getBoolean("rotateEnabled");
        boolean scaleEnabled = this.cameraOptions.getBoolean("scaleEnabled");
        int minimumCompressSize = this.cameraOptions.getInt("minimumCompressSize");
        int quality = this.cameraOptions.getInt("quality");
        boolean isWeChatStyle = this.cameraOptions.getBoolean("isWeChatStyle");
        boolean showSelectedIndex = this.cameraOptions.getBoolean("showSelectedIndex");
        boolean compressFocusAlpha = this.cameraOptions.getBoolean("compressFocusAlpha");

        int modeValue;
        if (imageCount == 1) {
            modeValue = 1;
        } else {
            modeValue = 2;
        }

        Boolean isAndroidQ = SdkVersionUtils.isQ();
        
        Activity currentActivity = getCurrentActivity();
        PictureSelector.create(currentActivity)
                .openGallery(SelectMimeType.ofImage())//全部.PictureMimeType.ofAll()、图片.ofImage()、视频.ofVideo()、音频.ofAudio()
                .setImageEngine(GlideEngine.createGlideEngine())
                .setMaxSelectNum(imageCount)// 最大图片选择数量 int
                .setMinSelectNum(0)// 最小选择数量 int
                .setImageSpanCount(4)// 每行显示个数 int
                .setSelectionMode(modeValue)// 多选 or 单选 PictureConfig.MULTIPLE or PictureConfig.SINGLE
                .isPreviewImage(true)// 是否可预览图片 true or false
                .isPreviewVideo(false)// 是否可预览视频 true or false
                .isPreviewAudio(false) // 是否可播放音频 true or false
                .isDisplayCamera(isCamera)// 是否显示拍照按钮 true or false
                .setCameraImageFormat(isAndroidQ ? PictureMimeType.PNG_Q : PictureMimeType.PNG)// 拍照保存图片格式后缀,默认jpeg
                .isPreviewZoomEffect(true)// 图片列表点击 缩放效果 默认true
                .isGif(isGif)// 是否显示gif图片 true or false
                .isOpenClickSound(false)// 是否开启点击声音 true or false
                .isCameraRotateImage(rotateEnabled) // 裁剪是否可旋转图片 true or false
                .setSelectedData(selectList) // 当前已选中的图片 List
                .setCompressEngine(createCompressEngine())
                .forResult(PictureConfig.CHOOSE_REQUEST); //结果回调onActivityResult code
    }

    private CompressFileEngine createCompressEngine() {
        return new CompressFileEngine() {

            @Override
            public void onStartCompress(Context context, ArrayList<Uri> source, OnKeyValueResultCallbackListener call) {
                Luban.with(context).load(source).ignoreBy(100).setCompressListener(
                        new OnNewCompressListener() {
                            @Override
                            public void onStart() {

                            }

                            @Override
                            public void onSuccess(String source, File compressFile) {
                                call.onCallback(source, compressFile.getAbsolutePath());
                            }

                            @Override
                            public void onError(String source, Throwable e) {

                            }
                        }
                ).launch();
            }
        };
    }

    /**
     * 打开相机
     */
    private void openCamera() {
        boolean rotateEnabled = this.cameraOptions.getBoolean("rotateEnabled");


        Boolean isAndroidQ = SdkVersionUtils.isQ();

        Activity currentActivity = getCurrentActivity();
        PictureSelector.create(currentActivity)
                .openCamera(SelectMimeType.ofImage())
                .setCameraImageFormat(isAndroidQ ? PictureMimeType.PNG_Q : PictureMimeType.PNG)// 拍照保存图片格式后缀,默认jpegs
                .isCameraRotateImage(rotateEnabled) // 裁剪是否可旋转图片 true or false
                .forResult(new OnResultCallbackListener<LocalMedia>() {
                    @Override
                    public void onResult(ArrayList<LocalMedia> result) {
                        // 结果回调
                        onOpenCameraResult(result);
                    }

                    @Override
                    public void onCancel() {

                    }
                });//结果回调onActivityResult code
    }

    /**
     * 拍摄视频
     */
    private void openVideo() {
        int quality = this.cameraOptions.getInt("quality");
        int MaxSecond = this.cameraOptions.getInt("MaxSecond");
        int MinSecond = this.cameraOptions.getInt("MinSecond");
        int recordVideoSecond = this.cameraOptions.getInt("recordVideoSecond");
        int imageCount = this.cameraOptions.getInt("imageCount");
        Activity currentActivity = getCurrentActivity();
        PictureSelector.create(currentActivity)
                .openCamera(SelectMimeType.ofVideo())//全部.PictureMimeType.ofAll()、图片.ofImage()、视频.ofVideo()、音频.ofAudio()
                .setSelectedData(selectList) // 当前已选中的图片 List
                .setRecordVideoMaxSecond(MaxSecond)// 显示多少秒以内的视频or音频也可适用 int
                .setRecordVideoMinSecond(MinSecond)// 显示多少秒以内的视频or音频也可适用 int
                .forResult(new OnResultCallbackListener<LocalMedia>() {
                    @Override
                    public void onResult(ArrayList<LocalMedia> result) {

                    }

                    @Override
                    public void onCancel() {

                    }
                });//结果回调onActivityResult code
    }

    /**
     * 选择视频
     */
    private void openVideoPicker() {
        int quality = this.cameraOptions.getInt("quality");
        int MaxSecond = this.cameraOptions.getInt("MaxSecond");
        int MinSecond = this.cameraOptions.getInt("MinSecond");
        int recordVideoSecond = this.cameraOptions.getInt("recordVideoSecond");
        int videoCount = this.cameraOptions.getInt("imageCount");
        boolean isCamera = this.cameraOptions.getBoolean("allowTakeVideo");

        Activity currentActivity = getCurrentActivity();
        PictureSelector.create(currentActivity)
                .openGallery(SelectMimeType.ofVideo())//全部.PictureMimeType.ofAll()、图片.ofImage()、视频.ofVideo()、音频.ofAudio()
                .setImageEngine(GlideEngine.createGlideEngine())
                .setSelectedData(selectList) // 当前已选中的视频 List
                .isOpenClickSound(false)// 是否开启点击声音 true or false
                .isDisplayCamera(isCamera)// 是否显示拍照按钮 true or false
                .setMaxSelectNum(videoCount)// 最大视频选择数量 int
                .setMinSelectNum(1)// 最小选择数量 int
                .setImageSpanCount(4)// 每行显示个数 int
                .setSelectionMode(SelectModeConfig.MULTIPLE)// 多选 or 单选 PictureConfig.MULTIPLE or PictureConfig.SINGLE
                .isPreviewVideo(true)// 是否可预览视频 true or false
                .setRecordVideoMaxSecond(MaxSecond)// 显示多少秒以内的视频or音频也可适用 int
                .setRecordVideoMinSecond(MinSecond)// 显示多少秒以内的视频or音频也可适用 int
                .forResult(PictureConfig.REQUEST_CAMERA);//结果回调onActivityResult code
    }

    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, final Intent data) {
            if (resultCode == -1) {
                if (requestCode == PictureConfig.CHOOSE_REQUEST) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            onGetResult(data);
                        }
                    }).run();
                } else if (requestCode == PictureConfig.REQUEST_CAMERA) {
                    onGetVideoResult(data);
                }
            } else {
                invokeError(resultCode);
            }

        }
    };

    private void onGetVideoResult(Intent data) {
        List<LocalMedia> mVideoSelectList = PictureSelector.obtainSelectorList(data);
        if (cameraOptions != null) {
            boolean isRecordSelected = cameraOptions.getBoolean("isRecordSelected");
            if (!mVideoSelectList.isEmpty() && isRecordSelected) {
                selectList = mVideoSelectList;
            }
            WritableArray videoList = new WritableNativeArray();

            for (LocalMedia media : mVideoSelectList) {
                if (TextUtils.isEmpty(media.getPath())) {
                    continue;
                }

                WritableMap videoMap = new WritableNativeMap();

                Boolean isAndroidQ = SdkVersionUtils.isQ();
                Boolean isAndroidR = SdkVersionUtils.isR();
                String filePath = media.getPath();
                if (isAndroidQ){
                    filePath = media.getSandboxPath();
                }
                if (isAndroidR){
                    filePath = media.getRealPath();
                }

                videoMap.putString("uri", "file://" + filePath);
                videoMap.putString("coverUri", "file://" + this.getVideoCover(filePath));
                videoMap.putString("fileName", new File(media.getPath()).getName());
                videoMap.putDouble("size", new File(media.getPath()).length());
                videoMap.putDouble("duration", media.getDuration() / 1000.00);
                videoMap.putInt("width", media.getWidth());
                videoMap.putInt("height", media.getHeight());
                videoMap.putString("type", "video");
                videoMap.putString("mime", media.getMimeType());
                videoList.pushMap(videoMap);
            }

            invokeSuccessWithResult(videoList);
        }
    }

    private void onGetResult(Intent data) {
        List<LocalMedia> tmpSelectList = PictureSelector.obtainSelectorList(data);
        if (cameraOptions != null) {
            boolean isRecordSelected = cameraOptions.getBoolean("isRecordSelected");
            if (!tmpSelectList.isEmpty() && isRecordSelected) {
                selectList = tmpSelectList;
            }

            WritableArray imageList = new WritableNativeArray();
            boolean enableBase64 = cameraOptions.getBoolean("enableBase64");

            for (LocalMedia media : tmpSelectList) {
                imageList.pushMap(getImageResult(media, enableBase64));
            }
            invokeSuccessWithResult(imageList);
        }
    }

    private void onOpenCameraResult(ArrayList<LocalMedia> result) {
        if (cameraOptions != null) {
            boolean isRecordSelected = cameraOptions.getBoolean("isRecordSelected");
            if (!result.isEmpty() && isRecordSelected) {
                selectList = result;
            }

            WritableArray imageList = new WritableNativeArray();
            boolean enableBase64 = cameraOptions.getBoolean("enableBase64");

            for (LocalMedia media : result) {
                imageList.pushMap(getImageResult(media, enableBase64));
            }
            invokeSuccessWithResult(imageList);
        }
    }

    private WritableMap getImageResult(LocalMedia media, Boolean enableBase64) {
        WritableMap imageMap = new WritableNativeMap();
        String path = media.getPath();

        if (media.isCompressed() || media.isCut()) {
            path = media.getCompressPath();
        }

        if (media.isCut()) {
            path = media.getCutPath();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        imageMap.putDouble("width", options.outWidth);
        imageMap.putDouble("height", options.outHeight);
        imageMap.putString("type", "image");
        imageMap.putString("uri", "file://" + path);
        imageMap.putString("original_uri", "file://" + media.getPath());
        imageMap.putInt("size", (int) new File(path).length());

        if (enableBase64) {
            String encodeString = getBase64StringFromFile(path);
            imageMap.putString("base64", encodeString);
        }

        return imageMap;
    }

    /**
     * 获取图片base64编码字符串
     *
     * @param absoluteFilePath 文件路径
     * @return base64字符串
     */
    private String getBase64StringFromFile(String absoluteFilePath) {
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(new File(absoluteFilePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bytes = output.toByteArray();
        if(absoluteFilePath.toLowerCase().endsWith("png")){
          return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }


    /**
     * 获取视频封面图片
     * @param videoPath 视频地址
     */
    private String getVideoCover(String videoPath) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            Bitmap bitmap = retriever.getFrameAtTime();
            FileOutputStream outStream = null;
            final String uuid = "thumb-" + UUID.randomUUID().toString();
            final String localThumb = reactContext.getExternalCacheDir().getAbsolutePath() + "/" + uuid + ".jpg";
            outStream = new FileOutputStream(new File(localThumb));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outStream);
            outStream.close();
            retriever.release();

            return localThumb;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception err) {
            err.printStackTrace();
        }

        return null;
    }

    /**
     * 选择照片成功时触发
     *
     * @param imageList 图片数组
     */
    private void invokeSuccessWithResult(WritableArray imageList) {
        if (this.mPickerCallback != null) {
            this.mPickerCallback.invoke(null, imageList);
            this.mPickerCallback = null;
        } else if (this.mPickerPromise != null) {
            this.mPickerPromise.resolve(imageList);
        }
    }

    /**
     * 取消选择时触发
     */
    private void invokeError(int resultCode) {
        String message = "取消";
        if (resultCode != 0) {
            message = String.valueOf(resultCode);
        }
        if (this.mPickerCallback != null) {
            this.mPickerCallback.invoke(message);
            this.mPickerCallback = null;
        } else if (this.mPickerPromise != null) {
            this.mPickerPromise.reject(SY_SELECT_IMAGE_FAILED_CODE, message);
        }
    }
}
