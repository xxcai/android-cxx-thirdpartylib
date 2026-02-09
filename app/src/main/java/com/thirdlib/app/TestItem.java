package com.thirdlib.app;

/**
 * 测试项数据类
 */
public class TestItem {
    private final String title;
    private final Runnable testAction;

    public TestItem(String title, Runnable testAction) {
        this.title = title;
        this.testAction = testAction;
    }

    public String getTitle() {
        return title;
    }

    public Runnable getTestAction() {
        return testAction;
    }
}
