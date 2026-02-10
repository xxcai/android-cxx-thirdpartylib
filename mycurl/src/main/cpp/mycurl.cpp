#include "mycurl.h"
#include <curl/curl.h>
#include <cstring>

namespace mycurl {

struct MyCurl::Impl {
    CURL* curl = nullptr;
    struct curl_slist* headers = nullptr;
    int timeout = 30;
    bool sslVerify = true;

    ~Impl() {
        if (headers) {
            curl_slist_free_all(headers);
        }
        if (curl) {
            curl_easy_cleanup(curl);
        }
    }

    static size_t writeCallback(void* contents, size_t size, size_t nmemb, void* userp) {
        size_t realsize = size * nmemb;
        std::string* response = static_cast<std::string*>(userp);
        response->append(static_cast<char*>(contents), realsize);
        return realsize;
    }

    Response perform(const std::string& url, const std::string* postData = nullptr) {
        Response response;

        // 清理之前的句柄（如果有）
        if (curl) {
            curl_easy_cleanup(curl);
            curl = nullptr;
        }

        curl = curl_easy_init();
        if (!curl) {
            response.error = "Failed to initialize curl";
            return response;
        }

        // 设置 URL
        curl_easy_setopt(curl, CURLOPT_URL, url.c_str());

        // 设置超时
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, timeout);

        // 禁用 SSL 证书验证（可选）
        if (!sslVerify) {
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
        }

        // 设置写入回调
        std::string responseBody;
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &responseBody);

        // 设置错误缓冲区
        char errbuf[CURL_ERROR_SIZE];
        errbuf[0] = '\0';
        curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, errbuf);

        // POST 请求设置
        if (postData && !postData->empty()) {
            curl_easy_setopt(curl, CURLOPT_POST, 1L);
            curl_easy_setopt(curl, CURLOPT_POSTFIELDS, postData->c_str());
            curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, postData->length());
        }

        // 添加自定义请求头
        if (headers) {
            curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        }

        // 执行请求
        CURLcode res = curl_easy_perform(curl);

        if (res != CURLE_OK) {
            response.error = errbuf;
            return response;
        }

        // 获取响应码
        long httpCode = 0;
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &httpCode);
        response.code = static_cast<int>(httpCode);
        response.body = std::move(responseBody);

        return response;
    }
};

MyCurl::MyCurl() : impl_(std::make_unique<Impl>()) {
    // 全局初始化（线程安全）
    curl_global_init(CURL_GLOBAL_ALL);
}

MyCurl::~MyCurl() = default;

MyCurl::MyCurl(MyCurl&& other) noexcept : impl_(std::move(other.impl_)) {
    other.impl_ = nullptr;
}

MyCurl& MyCurl::operator=(MyCurl&& other) noexcept {
    if (this != &other) {
        impl_ = std::move(other.impl_);
        other.impl_ = nullptr;
    }
    return *this;
}

Response MyCurl::get(const std::string& url) {
    return impl_->perform(url, nullptr);
}

Response MyCurl::post(const std::string& url, const std::string& data) {
    return impl_->perform(url, &data);
}

void MyCurl::setTimeout(int timeout) {
    impl_->timeout = timeout > 0 ? timeout : 30;
}

void MyCurl::addHeader(const std::string& key, const std::string& value) {
    std::string header = key + ": " + value;
    impl_->headers = curl_slist_append(impl_->headers, header.c_str());
}

void MyCurl::clearHeaders() {
    if (impl_->headers) {
        curl_slist_free_all(impl_->headers);
        impl_->headers = nullptr;
    }
}

void MyCurl::setUserAgent(const std::string& userAgent) {
    curl_easy_setopt(impl_->curl, CURLOPT_USERAGENT, userAgent.c_str());
}

void MyCurl::setSSLVerify(bool verify) {
    impl_->sslVerify = verify;
}

}  // namespace mycurl
