package org.example.ai.service.agent;

/**
 * Agent接口 - 所有Agent都需要实现
 */
public interface IAgent {

    /**
     * 执行Agent任务
     * @param input 输入参数
     * @return 处理结果
     */
    String execute(String input);

    /**
     * 获取Agent名称
     */
    String getName();

    /**
     * 获取Agent描述
     */
    String getDescription();

}
