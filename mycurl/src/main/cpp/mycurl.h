#pragma once

#include <string>
#include <memory>
#include <vector>

namespace mycurl {

/**
 * HTTP 响应结构
 */
struct Response {
    int code = 0;                  // HTTP 状态码
    std::string body;               // 响应体
    std::string error;              // 错误信息（若有）
};

/**
 * mycurl - 基于 libcurl 的简单 HTTP 客户端封装
 *
 * 使用示例：
 *   mycurl::MyCurl client;
 *   auto response = client.get("https://example.com");
 *   if (response.code == 200) {
 *       // 成功
 *   }
 */
class MyCurl {
public:
    MyCurl();
    ~MyCurl();

    // 禁止拷贝
    MyCurl(const MyCurl&) = delete;
    MyCurl& operator=(const MyCurl&) = delete;

    // 允许移动
    MyCurl(MyCurl&&) noexcept;
    MyCurl& operator=(MyCurl&&) noexcept;

    /**
     * HTTP GET 请求
     * @param url 目标 URL
     * @return Response 响应结果
     */
    Response get(const std::string& url);

    /**
     * HTTP POST 请求
     * @param url 目标 URL
     * @param data POST 数据
     * @return Response 响应结果
     */
    Response post(const std::string& url, const std::string& data);

    /**
     * 设置超时时间（秒）
     * @param timeout 超时时间，默认 30 秒
     */
    void setTimeout(int timeout);

    /**
     * 添加请求头
     * @param key 请求头键
     * @param value 请求头值
     */
    void addHeader(const std::string& key, const std::string& value);

    /**
     * 清除所有自定义请求头
     */
    void clearHeaders();

    /**
     * 设置 User-Agent
     * @param userAgent User-Agent 字符串
     */
    void setUserAgent(const std::string& userAgent);

    /**
     * 启用/禁用 SSL 证书验证
     * @param verify 是否验证，默认 true
     */
    void setSSLVerify(bool verify);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace mycurl
