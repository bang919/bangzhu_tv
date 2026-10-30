package com.xiaohei.tv.data.provider

import java.io.IOException

class UnsupportedFeatureException(
    message: String = "该视频源不支持此功能",
) : IOException(message)
