//
// Created by xiongzhuang on 2021/10/8.
//

#ifndef LIGHTTRACK_LIGHTTRACK_H
#define LIGHTTRACK_LIGHTTRACK_H

#include <vector>
#include <map>
#include <opencv2/opencv.hpp>
#include "net.h"
#include "layer_type.h"

#define USE_GPU false

#include <android/log.h>

#define LOG_TAG "LightTrack"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


using namespace cv;

typedef struct Bbox {
    float x0;
    float y0;
    float x1;
    float y1;
} Bbox;

class LightTrack {
public:
    LightTrack(const char *model_init, const char *model_update);
    LightTrack(AAssetManager* mgr);


    ~LightTrack();

    void init(const uint8_t *img, Bbox &box, int im_h , int im_w);
    void track(const uint8_t *img);

    cv::Point target_pos = {0, 0};
    cv::Point2f target_sz = {0.f, 0.f};

    ncnn::Net net_init, net_update;
    ncnn::Mat zf;

    const float mean_vals[3] = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};  // RGB
    const float norm_vals[3] = {1 / 0.229f / 255.f, 1 / 0.224f / 255.f, 1 / 0.225f / 255.f};

private:
    int stride = 16;
    int even = 0;
    int exemplar_size = 127;
    int instance_size = 288;
    float lr = 0.616;
    float ratio = 1;
    float penalty_tk = 0.007;
    float context_amount = 0.5;
    float window_influence = 0.225;
    int score_size;
    int total_stride = 16;

    int ori_img_w = 960;
    int ori_img_h = 640;


    void grids();

    cv::Mat get_subwindow_tracking(cv::Mat im, cv::Point2f pos, int model_sz, int original_sz);
    void load_model(std::string model_init, std::string model_update);
    void load_model(AAssetManager* mgr);

    void update(const cv::Mat &x_crops, float scale_z);

    std::vector<float> window;
    std::vector<float> grid_to_search_x;
    std::vector<float> grid_to_search_y;
};


#endif //LIGHTTRACK_LIGHTTRACK_H
