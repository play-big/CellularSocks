# CellularSocks

一个 Android SOCKS5 代理应用，监听 Wi-Fi 局域网地址，但所有外连强制走蜂窝网络（无需 Root）。

## 功能特性

- **SOCKS5 协议支持**：完整的 SOCKS5 实现，支持 CONNECT 和 UDP ASSOCIATE
- **蜂窝网络绑定**：通过 Android Network API 强制外连走移动数据
- **用户认证**：可选的用户名/密码鉴权
- **访问控制**：IP 白名单/黑名单，防暴力破解
- **服务发现**：NSD/mDNS 广播，局域网设备可自动发现
- **持久化设置**：端口、鉴权等配置自动保存
- **实时统计**：连接数、传输量等运行状态
- **前台服务**：常驻后台，支持通知控制

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose
- **网络**：Android Network API + SOCKS5
- **存储**：DataStore
- **测试**：JUnit + Mockito + Coroutines Test

## 使用步骤

1. **安装应用**：下载并安装 APK
2. **授权通知**：首次启动时允许通知权限
3. **确认网络**：确保手机连接 Wi-Fi 且蜂窝数据已开启
4. **启动代理**：设置端口（可选鉴权）→ 点击【启动代理】
5. **配置客户端**：局域网设备设置 SOCKS5 代理为手机 Wi-Fi IP + 端口
6. **验证连接**：观察日志中的"转发 ...（蜂窝）"信息

## 开发环境

- Android Studio Hedgehog | 2023.1.1
- JDK 17
- Android SDK 34
- Gradle 8.5

## 构建

```bash
# 克隆项目
git clone https://github.com/your-username/CellularSocks.git
cd CellularSocks

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 运行测试
./gradlew test

# 代码检查
./gradlew lintDebug
```

## 项目结构

```
CellularSocks/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/cellularsocks/
│   │   │   ├── MainActivity.kt              # Compose UI
│   │   │   ├── service/
│   │   │   │   ├── ProxyForegroundService.kt # 前台服务
│   │   │   │   └── CellularBinder.kt        # 蜂窝网络绑定
│   │   │   ├── core/
│   │   │   │   ├── Socks5Server.kt          # SOCKS5 服务器
│   │   │   │   ├── Socks5Session.kt         # 会话处理
│   │   │   │   ├── UdpAssociate.kt          # UDP 中继
│   │   │   │   └── Pump.kt                  # 数据泵
│   │   │   └── util/
│   │   │       ├── Settings.kt              # 设置持久化
│   │   │       ├── NetUtils.kt              # 网络工具
│   │   │       ├── LogBus.kt                # 日志总线
│   │   │       ├── Nsd.kt                   # 服务发现
│   │   │       └── IpAcl.kt                 # IP 访问控制
│   │   └── res/                             # 资源文件
│   └── src/test/                            # 单元测试
├── gradle/                                  # Gradle Wrapper
├── .github/workflows/                       # CI/CD
└── README.md
```

## 配置说明

### 网络要求

- Android 8.0+ (API 26+)
- 开发者选项中开启"移动数据始终保持活动"
- Wi-Fi 与蜂窝数据同时开启

### 权限说明

- `INTERNET`：网络访问
- `ACCESS_NETWORK_STATE`：网络状态检查
- `ACCESS_WIFI_STATE`：Wi-Fi 状态检查
- `WAKE_LOCK`：保持网络活跃
- `POST_NOTIFICATIONS`：前台服务通知
- `FOREGROUND_SERVICE`：前台服务

## 开发指南

### 添加新功能

1. 在对应包下创建新类
2. 添加单元测试
3. 更新 README 文档
4. 提交 Pull Request

### 测试

```bash
# 运行所有测试
./gradlew test

# 运行特定测试类
./gradlew test --tests Socks5SessionTest

# 生成测试报告
./gradlew test jacocoTestReport
```

### 代码规范

- 使用 Kotlin 编码规范
- 函数和变量使用驼峰命名
- 类名使用 PascalCase
- 常量使用 UPPER_SNAKE_CASE

## 许可证

Apache License 2.0

## 贡献

欢迎提交 Issue 和 Pull Request！

## 更新日志

### v0.1.0
- 初始版本
- 基础 SOCKS5 功能
- 蜂窝网络绑定
- 用户认证
- 访问控制
- 服务发现
- 持久化设置
- 实时统计 