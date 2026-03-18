# APK 签名配置指南

## 为什么需要签名？

Android 应用必须使用数字证书签名才能安装到设备上。已签名的APK具有以下优势：
- 用户可以验证应用来源和完整性
- Android 系统要求安装应用必须签名
- 应用更新必须使用相同的签名密钥

---

## 本地开发签名配置

### 1. 生成签名密钥

```bash
# 进入项目目录
cd app/src/main

# 生成签名密钥库
keytool -genkey -v -keystore release.keystore -alias pushmessage -keyalg RSA -keysize 2048 -validity 10000
```

参数说明：
- `-keystore release.keystore`: 密钥库文件名
- `-alias pushmessage`: 密钥别名
- `-validity 10000`: 有效期（天数）

执行后会要求输入：
- 密钥库密码
- 密钥密码（可以与密钥库密码相同）
- 姓名、组织等证书信息

### 2. 配置本地签名

在 `local.properties` 文件中添加：

```properties
keystore.path=app/src/main/release.keystore
keystore.password=你的密钥库密码
key.alias=pushmessage
key.password=你的密钥密码
```

⚠️ **注意**: `local.properties` 已添加到 `.gitignore`，不会被提交到Git。

---

## GitHub Actions 自动签名配置

### 1. 准备密钥

将生成的密钥库文件转换为 Base64：

```bash
# macOS/Linux
base64 -i app/src/main/release.keystore | pbcopy

# Linux
base64 -w 0 app/src/main/release.keystore

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app/src/main/release.keystore"))
```

### 2. 添加 GitHub Secrets

在 GitHub 仓库页面：
1. 进入 **Settings** → **Secrets and variables** → **Actions**
2. 点击 **New repository secret** 添加以下密钥：

| Secret 名称 | 值 |
|------------|-----|
| `KEYSTORE_BASE64` | Base64 编码的密钥库文件内容 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名（如 pushmessage）|
| `KEY_PASSWORD` | 密钥密码 |

### 3. 验证配置

提交代码到 main/master 分支，GitHub Actions 会自动：
1. 解码密钥库文件
2. 使用密钥签名 APK
3. 上传签名后的 APK 作为 Artifact
4. 推送标签时自动创建 Release

---

## 签名验证

### 本地验证 APK 签名

```bash
# 查看APK签名信息
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk

# 使用 apksigner（推荐）
$ANDROID_SDK/build-tools/34.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

### 查看证书指纹

```bash
keytool -list -v -keystore app/src/main/release.keystore -alias pushmessage
```

---

## 安全建议

1. **保护密钥库文件**
   - 永远不要将 `release.keystore` 提交到 Git
   - 备份密钥库到安全位置（如密码管理器）
   - 丢失密钥库将无法更新已发布的应用

2. **密钥密码管理**
   - 使用强密码（至少8位，包含大小写字母和数字）
   - 定期更换密码
   - 使用 GitHub Secrets 管理 CI/CD 密码

3. **密钥有效期**
   - 建议设置较长的有效期（如25年）
   - Google Play 要求有效期至少到2033年

---

## 故障排除

### 构建失败：无法找到密钥库

确保 `local.properties` 中的路径正确：
```properties
# 相对路径（推荐）
keystore.path=app/src/main/release.keystore

# 或使用绝对路径
keystore.path=/Users/username/projects/PushMessage/app/src/main/release.keystore
```

### GitHub Actions 签名失败

检查 Secrets 是否配置正确：
1. 确认 `KEYSTORE_BASE64` 是完整的 Base64 字符串
2. 确认密码和别名与创建密钥时一致
3. 查看 Actions 日志获取详细错误信息

### 更新已签名应用时提示"签名冲突"

确保使用相同的密钥库和别名进行签名。不同签名密钥无法覆盖安装。
