package com.trackflow.automation.node.model;

/**
 * 节点输入可以接受的值来源。
 *
 * 这是节点端口契约的一部分：画布、校验器与执行器都以此判断一条输入是否可用，
 * 避免前端能连线、后端却无法在运行时解析的割裂状态。
 */
public enum InputBindingMode {
    literal,
    reference,
    template
}
