#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <jni.h>
#include <pthread.h>
#include <string>

#include "LightTrack.h"
// ncnn
#include "layer.h"
#include "net.h"
#include "benchmark.h"



static inline double compareHist(cv::Mat src_origin_1, cv::Mat src_origin_2)
{
    // 转换到 HSV , 图片是RGB格式用CV_RGB2HSV
    cv::Mat src_1, src_2;
    cv::cvtColor( src_origin_1 , src_1 , cv::COLOR_BGR2HSV );
    cv::cvtColor( src_origin_2, src_2, cv::COLOR_BGR2HSV );
//    cv::cvtColor( src_origin_1 , src_1 , cv::COLOR_RGB2HSV );
//    cv::cvtColor( src_origin_2, src_2, cv::COLOR_RGB2HSV );

    // 对hue通道使用30个bin,对saturatoin通道使用32个bin
    int h_bins = 50; int s_bins = 60;
    int histSize[] = { h_bins, s_bins };

    // hue的取值范围从0到256, saturation取值范围从0到180
    float h_ranges[] = { 0, 256 };
    float s_ranges[] = { 0, 180 };

    const float* ranges[] = { h_ranges, s_ranges };
    // 使用第0和第1通道
    int channels[] = { 0, 1 };

    // 直方图
    cv::MatND src_1_hist,src_2_hist;
    // 计算HSV图像的直方图
    cv::calcHist( &src_1 , 1, channels, Mat(), src_1_hist, 2, histSize, ranges, true, false );
    cv::normalize( src_1_hist, src_1_hist, 0, 1, cv::NORM_MINMAX, -1, Mat() );
    cv::calcHist( &src_2 , 1, channels, Mat(), src_2_hist, 2, histSize, ranges, true, false );
    cv::normalize( src_2_hist, src_2_hist, 0, 1, cv::NORM_MINMAX, -1, Mat() );

    //对比方法
    double result = cv::compareHist( src_1_hist, src_2_hist, 0 );
    return result;
}

static inline void cxy_wh_2_rect(const cv::Point& pos, const cv::Point2f& sz, cv::Rect &rect)
{
    rect.x = max(0, pos.x - int(sz.x / 2));
    rect.y = max(0, pos.y - int(sz.y / 2));
    rect.width = int(sz.x);
    rect.height = int(sz.y);
}



extern "C" {

// FIXME DeleteGlobalRef is missing for objCls
static jclass objCls = NULL;
static jmethodID constructortorId;
static jfieldID xId;
static jfieldID yId;
static jfieldID wId;
static jfieldID hId;
static LightTrack* siam_tracker;
static cv::Mat init_window;
static cv::Rect last_rect;
static float similarity_threshold = 0.3;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;


JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("JNI_OnLoad");
    ncnn::create_gpu_instance();
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    LOGI("JNI_OnUnload");
    ncnn::destroy_gpu_instance();
}

JNIEXPORT jboolean JNICALL
Java_com_zbgd_lighttrack_LightTrackNcnn_Init(JNIEnv *env, jobject thiz, jobject assetManager) {
    LOGI("init");
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = 4;
//    opt.blob_allocator = &g_blob_pool_allocator;
//    opt.workspace_allocator = &g_workspace_pool_allocator;
    opt.use_packing_layout = true;

    // use vulkan compute
    if (ncnn::get_gpu_count() != 0)
        opt.use_vulkan_compute = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    // Build tracker.
//    LightTrack* siam_tracker;
    siam_tracker = new LightTrack(mgr);
    // init jni glue
    jclass localObjCls = env->FindClass("com/zbgd/lighttrack/LightTrackNcnn$Obj");
    objCls = reinterpret_cast<jclass>(env->NewGlobalRef(localObjCls));
    constructortorId = env->GetMethodID(objCls, "<init>", "(Lcom/zbgd/lighttrack/LightTrackNcnn;)V");
    xId = env->GetFieldID(objCls, "x", "F");
    yId = env->GetFieldID(objCls, "y", "F");
    wId = env->GetFieldID(objCls, "w", "F");
    hId = env->GetFieldID(objCls, "h", "F");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_zbgd_lighttrack_LightTrackNcnn_SetTemplate(JNIEnv *env, jobject thiz, jobject bitmap,
                                                    jobject obj) {
    LOGI("SetTemplate");
    double start_time = ncnn::get_current_time();
    void *pixels;
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    const int width = info.width;
    const int height = info.height;
    LOGI("width: %d height: %d",width,height);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888){
        LOGE("info.format != ANDROID_BITMAP_FORMAT_RGBA_8888");
        return JNI_FALSE;
    }
    // Lock the bitmap to get the raw pixels
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("AndroidBitmap_lockPixels");
        return JNI_FALSE;
    }

    Mat mat;
    // Convert to OpenCV Mat
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
//        mat.create(height, width, CV_8UC3);
//        memcpy(mat.data, pixels, info.height * info.width * 3);
        mat = cv::Mat(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(mat, mat, cv::COLOR_BGRA2BGR);
    }

    // Unlock the bitmap
    AndroidBitmap_unlockPixels(env, bitmap);
    jclass objClass = env->GetObjectClass(obj);
    Bbox box;
    box.x0 = env->GetFloatField(obj, xId);
    box.y0 = env->GetFloatField(obj, yId);
    box.x1 = box.x0 + env->GetFloatField(obj, wId);
    box.y1 = box.y0 + env->GetFloatField(obj, hId);
    LOGI("x0: %f y0: %f w: %f h:%f",box.x0,box.y0, env->GetFloatField(obj, wId),env->GetFloatField(obj, hId));
    LOGI("x1: %f y1: %f",box.x1,box.y1);

    cv::Rect trackWindow;
    trackWindow.x = box.x0;
    trackWindow.y = box.y0;
    trackWindow.width = box.x1 - box.x0;
    trackWindow.height = box.y1 - box.y0;


    siam_tracker->init(mat.data, box, height, width);
    mat(trackWindow).copyTo(init_window);

    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_com_zbgd_lighttrack_LightTrackNcnn_Track(JNIEnv *env, jobject thiz, jobject bitmap) {
    LOGI("Track");
    double start_time = ncnn::get_current_time();
    void *pixels;
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    const int width = info.width;
    const int height = info.height;
    LOGI("width: %d height: %d",width,height);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888){
        LOGE("info.format != ANDROID_BITMAP_FORMAT_RGBA_8888");
        return NULL;
    }
    // Lock the bitmap to get the raw pixels
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return NULL;
    }

    Mat mat;
    // Convert to OpenCV Mat
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        mat = cv::Mat(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(mat, mat, cv::COLOR_BGRA2BGR);
    }

    // Unlock the bitmap
    AndroidBitmap_unlockPixels(env, bitmap);

    siam_tracker->track(mat.data);


    // Result to rect.
    cv::Rect rect;
    cxy_wh_2_rect(siam_tracker->target_pos, siam_tracker->target_sz, rect);
    double elasped = ncnn::get_current_time() - start_time;
    siam_tracker->set_fps(elasped);
    LOGI("%.2fms  track", elasped);
    // Boundary judgment.
    cv::Mat track_window;
    if(rect.x < 0 && rect.x + rect.width > 0) {
        rect.width += rect.x;
        rect.x = 0;
    }

    if(rect.y < 0 && rect.y + rect.height > 0) {
        rect.height += rect.y;
        rect.y = 0;
    }

    if(rect.x + rect.width > width){
        rect.width = width - rect.x;
    }

    if(rect.y + rect.height > height){
        rect.height = height - rect.y;
    }
    if (0 <= rect.x && 0 <= rect.width && rect.x + rect.width <= width && 0 <= rect.y &&
        0 <= rect.height && rect.y + rect.height <= height) {
        mat(rect).copyTo(track_window);
        // 对比初始框和跟踪框的相似度，从而判断是否跟丢（因为LighTrack的得分输出不具有判别性，所以通过后处理引入判断跟丢机制）
        double score = compareHist(init_window, track_window);
        LOGI("Similarity score= %f \n", score);
        // 相似度大于0.5的情况才进行矩形框标注
        pthread_mutex_lock(&lock);
        float threshold = similarity_threshold;
        pthread_mutex_unlock(&lock);
        if (score > threshold){
            // Draw rect.
            //cv::rectangle(mat, rect, cv::Scalar(0, 255, 0));
            last_rect = rect;
            jobject jObj = env->NewObject(objCls, constructortorId, thiz);
            env->SetFloatField(jObj, xId, static_cast<float >(rect.x));
            env->SetFloatField(jObj, yId, static_cast<float >(rect.y));
            env->SetFloatField(jObj, wId, static_cast<float >(rect.width));
            env->SetFloatField(jObj, hId, static_cast<float >(rect.height));
            return jObj;
        }else{
            LOGI("target out of range \n");
            siam_tracker->target_pos.x = last_rect.x;
            siam_tracker->target_pos.y = last_rect.y;
            siam_tracker->target_sz.x = float(last_rect.width);
            siam_tracker->target_sz.y = float(last_rect.height);
        }
        return NULL;
    } else{
        siam_tracker->target_pos.x = last_rect.x;
        siam_tracker->target_pos.y = last_rect.y;
        siam_tracker->target_sz.x = float(last_rect.width);
        siam_tracker->target_sz.y = float(last_rect.height);
    }
    LOGI("Target not in range");

    return NULL;

}

JNIEXPORT jfloat JNICALL
Java_com_zbgd_lighttrack_LightTrackNcnn_GetFPS(JNIEnv *env, jobject thiz){
    return siam_tracker->get_fps();
}

JNIEXPORT jboolean JNICALL
Java_com_zbgd_lighttrack_LightTrackNcnn_SetSimilarityThreshold(JNIEnv *env, jobject thiz, jfloat threshold){
    LOGI("set similarity_threshold %f \n",threshold);
    pthread_mutex_lock(&lock);
    similarity_threshold = threshold;
    pthread_mutex_unlock(&lock);
    return JNI_TRUE;
}

}

