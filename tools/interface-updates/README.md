# 接口模块发布

红果和小黑咪模块从已验证的 release APK 提取 DEX，使用主程序内置的公钥验证清单。接口模块版本和主程序的插件 ABI 是独立版本；当前 ABI 为 3。

1. 构建并测试 release APK。
2. 运行 `python tools/interface-updates/prepare.py --apk app/build/outputs/apk/release/app-release-unsigned.apk --version 2 --host-version 3 --tag interface-v2 --java <java路径>`。每次发布使用递增的模块版本和新的 tag。
3. 将 `build/interface-updates/sources-v2.zip` 上传到对应 GitHub Release；确认下载地址可用。
4. 将生成的 `manifest.json` 和 `manifest.sig` 一起更新到仓库的 `interface-updates/`，并推送主分支。客户端下一次启动会检查更新。

签名密钥位于本机 `.tools/interface-update.p12`，密码文件为 `.tools/interface-update-pass.txt`，这两个文件均未纳入 Git。请安全备份；私钥丢失后已有安装包不能信任新签名，泄露则攻击者可发布有权在 App 内运行的模块。绝不要上传私钥或密码。`app/src/main/assets/interface-updates/signing.cer` 仅包含公开证书。

插件依赖与宿主 ABI 必须兼容。更改 `MediaProvider`、模型、第三方库版本或宿主播放机制时，检查兼容性，并在不兼容时更新 ABI 后发布新 APK。上线前在电视模拟器验证两源的首页、详情、播放及更新、重启、失败回退。
