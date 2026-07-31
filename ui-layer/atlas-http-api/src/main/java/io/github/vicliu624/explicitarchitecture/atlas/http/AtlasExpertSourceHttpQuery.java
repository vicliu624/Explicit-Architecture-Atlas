package io.github.vicliu624.explicitarchitecture.atlas.http;

import io.github.vicliu624.explicitarchitecture.atlas.application.api.ExpertAtlasView;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;

import java.util.Optional;

/**
 * 专家源码正文的受控查询边界。
 *
 * <p>调用者只能提供 Package 中已存在的资产身份，不能提交任意文件路径或 SourceLocator。实际 source JAR 解析和
 * 本地 Workspace 路径只存在于服务组合根。</p>
 */
@FunctionalInterface
public interface AtlasExpertSourceHttpQuery {

    Optional<ExpertAtlasView.SourceFile> findSource(AtlasId packageId, AtlasId assetId);
}
