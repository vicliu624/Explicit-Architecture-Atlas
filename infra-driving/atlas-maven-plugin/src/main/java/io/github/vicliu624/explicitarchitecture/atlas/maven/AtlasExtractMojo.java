package io.github.vicliu624.explicitarchitecture.atlas.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Maven 构建中生成 Atlas 事实包的扩展点。 */
@Mojo(name = "extract", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class AtlasExtractMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Explicit Architecture Atlas：已进入 extract 扩展点；分析器将在后续版本写入事实包。");
    }
}
