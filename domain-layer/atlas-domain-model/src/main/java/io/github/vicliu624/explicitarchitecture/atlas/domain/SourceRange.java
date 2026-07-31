package io.github.vicliu624.explicitarchitecture.atlas.domain;

/** 源码中的一段稳定行列范围；未知时使用 {@link #unknown()}。 */
public record SourceRange(int startLine, int startColumn, int endLine, int endColumn) {

    public SourceRange {
        boolean unknown = startLine == 0 && startColumn == 0 && endLine == 0 && endColumn == 0;
        if (!unknown && (startLine < 1 || endLine < startLine || startColumn < 1 || endColumn < 1
                || (startLine == endLine && endColumn < startColumn))) {
            throw new IllegalArgumentException("源码范围不合法");
        }
    }

    public static SourceRange unknown() {
        return new SourceRange(0, 0, 0, 0);
    }
}
