package com.lhstack.env;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 数据库访问统一走后台线程, 结果回到 EDT 使用。
 *
 * <p>插件面板与对话框都运行在 EDT 上, 在 EDT 上直接访问 SQLite 会触发平台的
 * 慢操作断言, 并且会阻塞整个 IDE。因此所有 UI 入口都必须经由这里发起数据库访问。
 */
public final class AsyncLoader {

    private static final Logger LOG = Logger.getInstance(AsyncLoader.class);

    private AsyncLoader() {
    }

    /**
     * 在后台线程执行 load, 再在 EDT 上把结果交给 onEdt。
     *
     * <p>回调使用 {@link ModalityState#any()}: 面板与对话框的数据加载都在
     * {@code show()} 之前发起, 若使用默认的 NON_MODAL 状态, 回调会被推迟到模态
     * 对话框关闭之后才执行, 表现为对话框内没有任何数据。回调只更新 Swing 组件,
     * 不修改 PSI 或项目模型, 因此使用 any() 是安全的。
     *
     * <p>{@code executeOnPooledThread} 会把异常收进 Future, 无人读取 Future 时
     * 异常会被静默丢弃, 因此这里显式捕获并上报, 避免失败无声无息。
     */
    public static <T> void loadThenOnEdt(Supplier<T> load, Consumer<T> onEdt) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            T result;
            try {
                result = load.get();
            } catch (Throwable error) {
                LOG.error("运行环境数据加载失败", error);
                return;
            }
            ApplicationManager.getApplication().invokeLater(() -> onEdt.accept(result), ModalityState.any());
        });
    }

    /** 只写库、不需要回到 EDT 的场景。异常同样显式上报, 不静默丢弃。 */
    public static void runInBackground(Runnable task) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                LOG.error("运行环境数据写入失败", error);
            }
        });
    }
}
