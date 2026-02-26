#pragma once

#include <memory>
#include <string>
#include <cstdarg>
#include <cstdio>
#include <spdlog/spdlog.h>
#include <spdlog/sinks/stdout_color_sinks.h>
#include <spdlog/sinks/rotating_file_sink.h>

namespace mylog {

/**
 * @brief 日志级别枚举
 */
enum class Level {
    trace = SPDLOG_LEVEL_TRACE,
    debug = SPDLOG_LEVEL_DEBUG,
    info = SPDLOG_LEVEL_INFO,
    warn = SPDLOG_LEVEL_WARN,
    error = SPDLOG_LEVEL_ERROR,
    critical = SPDLOG_LEVEL_CRITICAL,
    off = SPDLOG_LEVEL_OFF
};

/**
 * @brief 简洁的日志封装（头文件库实现）
 */
class Logger {
public:
    Logger() = default;
    ~Logger() = default;

    // 禁止拷贝
    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;

    // 允许移动
    Logger(Logger&& other) noexcept = default;
    Logger& operator=(Logger&& other) noexcept = default;

    /**
     * @brief 初始化日志系统（文件日志）
     * @param tag 日志标签（保留兼容）
     * @param logDir 日志文件目录，例如 /data/data/{package}/files/logs
     */
    static void init(const std::string& tag, const std::string& logDir);

    /**
     * @brief 设置日志级别
     */
    static void setLevel(Level level);

    /**
     * @brief 记录 trace 级别日志
     */
    static void trace(const char* fmt, ...);
    static void trace(const std::string& msg);

    /**
     * @brief 记录 debug 级别日志
     */
    static void debug(const char* fmt, ...);
    static void debug(const std::string& msg);

    /**
     * @brief 记录 info 级别日志
     */
    static void info(const char* fmt, ...);
    static void info(const std::string& msg);

    /**
     * @brief 记录 warn 级别日志
     */
    static void warn(const char* fmt, ...);
    static void warn(const std::string& msg);

    /**
     * @brief 记录 error 级别日志
     */
    static void error(const char* fmt, ...);
    static void error(const std::string& msg);

    /**
     * @brief 记录 critical 级别日志
     */
    static void critical(const char* fmt, ...);
    static void critical(const std::string& msg);

private:
    static std::shared_ptr<spdlog::logger> s_logger;
    static bool s_initialized;
};

// 静态成员 inline 初始化
inline std::shared_ptr<spdlog::logger> Logger::s_logger = nullptr;
inline bool Logger::s_initialized = false;

// inline 实现
inline void Logger::init(const std::string& tag, const std::string& logDir) {
    if (s_initialized && s_logger) {
        return;
    }

    (void)tag;  // 保留兼容参数

    try {
        // 组合日志文件路径
        std::string logFile = logDir + "/mylog.log";

        // 使用 rotating_file_sink：文件路径, 单文件最大大小, 最大文件数
        auto file_sink = std::make_shared<spdlog::sinks::rotating_file_sink_mt>(
            logFile, 1024 * 1024 * 10, 3);  // 10MB, 3个文件轮转

        s_logger = std::make_shared<spdlog::logger>("mylog", file_sink);
        spdlog::register_logger(s_logger);
        s_initialized = true;
    } catch (const spdlog::spdlog_ex& ex) {
        // 静默失败
    }
}

inline void Logger::setLevel(Level level) {
    if (s_logger) {
        s_logger->set_level(static_cast<spdlog::level::level_enum>(level));
    }
}

inline void Logger::trace(const char* fmt, ...) {
    if (!s_logger) return;
    va_list args;
    va_start(args, fmt);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    s_logger->trace("{}", buf);
}

inline void Logger::trace(const std::string& msg) {
    if (!s_logger) return;
    s_logger->trace("{}", msg);
}

inline void Logger::debug(const char* fmt, ...) {
    if (!s_logger) return;
    va_list args;
    va_start(args, fmt);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    s_logger->debug("{}", buf);
}

inline void Logger::debug(const std::string& msg) {
    if (!s_logger) return;
    s_logger->debug("{}", msg);
}

inline void Logger::info(const char* fmt, ...) {
    if (!s_logger) return;
    va_list args;
    va_start(args, fmt);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    s_logger->info("{}", buf);
}

inline void Logger::info(const std::string& msg) {
    if (!s_logger) return;
    s_logger->info("{}", msg);
}

inline void Logger::warn(const char* fmt, ...) {
    if (!s_logger) return;
    va_list args;
    va_start(args, fmt);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    s_logger->warn("{}", buf);
}

inline void Logger::warn(const std::string& msg) {
    if (!s_logger) return;
    s_logger->warn("{}", msg);
}

inline void Logger::error(const char* fmt, ...) {
    if (!s_logger) return;
    va_list args;
    va_start(args, fmt);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    s_logger->error("{}", buf);
}

inline void Logger::error(const std::string& msg) {
    if (!s_logger) return;
    s_logger->error("{}", msg);
}

inline void Logger::critical(const char* fmt, ...) {
    if (!s_logger) return;
    va_list args;
    va_start(args, fmt);
    char buf[1024];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    s_logger->critical("{}", buf);
}

inline void Logger::critical(const std::string& msg) {
    if (!s_logger) return;
    s_logger->critical("{}", msg);
}

// 便捷函数：直接使用静态方法
inline void init(const std::string& tag, const std::string& logDir) {
    Logger::init(tag, logDir);
}

inline void setLevel(Level level) {
    Logger::setLevel(level);
}

inline void trace(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    Logger::trace(fmt, args);
    va_end(args);
}

inline void trace(const std::string& msg) {
    Logger::trace(msg);
}

inline void debug(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    Logger::debug(fmt, args);
    va_end(args);
}

inline void debug(const std::string& msg) {
    Logger::debug(msg);
}

inline void info(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    Logger::info(fmt, args);
    va_end(args);
}

inline void info(const std::string& msg) {
    Logger::info(msg);
}

inline void warn(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    Logger::warn(fmt, args);
    va_end(args);
}

inline void warn(const std::string& msg) {
    Logger::warn(msg);
}

inline void error(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    Logger::error(fmt, args);
    va_end(args);
}

inline void error(const std::string& msg) {
    Logger::error(msg);
}

inline void critical(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    Logger::critical(fmt, args);
    va_end(args);
}

inline void critical(const std::string& msg) {
    Logger::critical(msg);
}

}  // namespace mylog
