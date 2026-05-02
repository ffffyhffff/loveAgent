package com.aichat.app.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 终端命令执行工具
 */
@Slf4j
public class TerminalTool {

    @Tool("执行终端/Shell 命令，输入要执行的命令")
    public String executeCommand(String command) {
        // 安全限制：禁止危险命令
        String lowerCmd = command.toLowerCase().trim();
        if (lowerCmd.contains("rm -rf /") || lowerCmd.contains("format")
                || lowerCmd.contains("shutdown") || lowerCmd.contains("reboot")) {
            return "禁止执行危险命令";
        }

        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            log.error("命令执行失败", e);
            return "命令执行失败: " + e.getMessage();
        }
    }
}
