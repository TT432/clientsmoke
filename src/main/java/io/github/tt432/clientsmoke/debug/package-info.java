/**
 * AI 调试 HTTP 服务器（{@code AIDebugServer}）与配套工具（{@code ScriptEvalService} /
 * {@code RenderDocCapturer}）。端口经系统属性 {@code ai_debug_port}
 * （fallback 环境变量 {@code AI_DEBUG_PORT}）指定，未配置默认不开启。
 *
 * @author TT432
 */
@NullMarked
package io.github.tt432.clientsmoke.debug;

import org.jspecify.annotations.NullMarked;
