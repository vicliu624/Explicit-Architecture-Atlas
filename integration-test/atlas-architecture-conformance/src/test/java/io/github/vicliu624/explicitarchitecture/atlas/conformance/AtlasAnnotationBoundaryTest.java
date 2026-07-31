package io.github.vicliu624.explicitarchitecture.atlas.conformance;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasConstraint;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 防止面向业务的声明注解意外成为应用运行期依赖。 */
class AtlasAnnotationBoundaryTest {

    @Test
    void annotationsAreCompileTimeIntentOnly() {
        Retention retention = AtlasConstraint.class.getAnnotation(Retention.class);
        assertEquals(RetentionPolicy.SOURCE, retention.value());
    }
}
