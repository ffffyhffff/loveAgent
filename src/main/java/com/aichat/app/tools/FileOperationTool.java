package com.aichat.app.tools;

import cn.hutool.core.io.FileUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件操作工具
 */
@Slf4j
public class FileOperationTool {

    private static final Path FILE_DIR = Paths.get(System.getProperty("user.dir"), "tmp", "files");

    @Tool("读取文件内容，输入文件名")
    public String readFile(String fileName) {
        try {
            Path filePath = FILE_DIR.resolve(fileName);
            return FileUtil.readUtf8String(filePath.toFile());
        } catch (Exception e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool("写入文件，输入格式为 JSON: {\"fileName\":\"文件名\",\"content\":\"内容\"}")
    public String writeFile(String input) {
        try {
            // 简单解析 JSON 格式的输入
            String fileName = extractJsonValue(input, "fileName");
            String content = extractJsonValue(input, "content");

            FileUtil.mkdir(FILE_DIR.toFile());
            Path filePath = FILE_DIR.resolve(fileName);
            FileUtil.writeUtf8String(content, filePath.toFile());
            return "文件写入成功: " + filePath;
        } catch (Exception e) {
            return "写入文件失败: " + e.getMessage();
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return "";
        int colonIndex = json.indexOf(':', keyIndex);
        int startIndex = json.indexOf('"', colonIndex + 1) + 1;
        int endIndex = json.indexOf('"', startIndex);
        return json.substring(startIndex, endIndex);
    }
}
