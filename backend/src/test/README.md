# 后端测试

从老师项目继承了27个测试源文件，包含单元、控制器、工具和集成测试。这是源码数量，不是本次通过数量。

使用 Java 21 执行 `mvn -B -Dspring.profiles.active=test test`。H2 test profile 与真实运行配置隔离。实际结果以当次 Surefire XML 和 CI 为准，原团队覆盖率已移到本地备份，不作为新项目证据。
