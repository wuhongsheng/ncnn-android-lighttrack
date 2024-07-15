// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2020 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.
package com.zbgd.lighttrack

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log


class LightTrackNcnn {
    external fun Init(mgr: AssetManager?): Boolean
    external fun SetTemplate(bitmap: Bitmap,obj: Obj):Boolean
    external fun Track(bitmap: Bitmap):Obj?
    val obj = Obj()
    inner class Obj {
        var x: Float = 0f
        var y: Float = 0f
        var w: Float = 0f
        var h: Float = 0f
    }

    companion object {
        init {
            System.loadLibrary("lighttrack")
        }
    }
    fun setTemplate(bitmap: Bitmap,x:Float,y:Float,w:Float,h:Float): Boolean {
        obj.x = x
        obj.y = y
        obj.w = w
        obj.h = h
        return SetTemplate(bitmap,obj)
    }

}
