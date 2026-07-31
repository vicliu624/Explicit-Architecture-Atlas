package io.github.vicliu624.explicitarchitecture.atlas.cli;

import java.io.PrintStream;
import java.util.List;

/**
 * Atlas 的命令行入口。
 *
 * <p>骨架阶段只定义稳定的命令面；真正的 source/runtime/content 参数将在各适配器实现就绪后接入。</p>
 */
public final class AtlasCli {

    private AtlasCli() {
    }

    public static void main(String[] arguments) {
        run(List.of(arguments), System.out, System.err);
    }

    static int run(List<String> arguments, PrintStream output, PrintStream error) {
        if (arguments.isEmpty() || arguments.contains("--help") || arguments.contains("help")) {
            output.println("Explicit Architecture Atlas CLI");
            output.println("可用命令：extract、validate、serve（当前为工程骨架命令面）");
            return 0;
        }
        error.println("尚未实现的 Atlas 命令：" + arguments.getFirst());
        return 2;
    }
}
