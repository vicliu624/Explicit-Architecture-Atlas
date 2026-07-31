package io.github.vicliu624.explicitarchitecture.atlas.store.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AssetKind;
import io.github.vicliu624.explicitarchitecture.atlas.domain.AtlasId;
import io.github.vicliu624.explicitarchitecture.atlas.domain.Evidence;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceClaim;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceOrigin;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceStatus;
import io.github.vicliu624.explicitarchitecture.atlas.domain.EvidenceVisibility;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentBundle;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentDeclaration;
import io.github.vicliu624.explicitarchitecture.atlas.domain.IntentTarget;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceLocator;
import io.github.vicliu624.explicitarchitecture.atlas.domain.SourceRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** 将注解处理器生成的 Manifest 转化为带可审计证据的 Intent Bundle。 */
public final class AtlasIntentManifestReader {

    private static final String READER_ID = "atlas-intent-manifest-reader";
    private final ObjectMapper mapper = new ObjectMapper();

    public IntentBundle read(List<Path> manifestFiles, String sourceRevision) {
        Objects.requireNonNull(manifestFiles, "manifestFiles 不能为空");
        List<IntentDeclaration> declarations = new ArrayList<>();
        for (Path manifestFile : manifestFiles) {
            Path file = Objects.requireNonNull(manifestFile, "manifestFile 不能为空").toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Atlas Intent Manifest 不存在: " + file);
            }
            readOne(file, sourceRevision, declarations);
        }
        return new IntentBundle(declarations);
    }

    private void readOne(Path file, String sourceRevision, List<IntentDeclaration> declarations) {
        try {
            JsonNode root = mapper.readTree(file.toFile());
            if (root == null || !root.isObject() || !"1.0".equals(ConventionProfileJsonReader.text(root, "schemaVersion"))) {
                throw new IllegalArgumentException("Atlas Intent Manifest 必须是 schemaVersion 1.0: " + file);
            }
            JsonNode entries = root.path("declarations");
            if (!entries.isArray()) {
                throw new IllegalArgumentException("Atlas Intent Manifest declarations 必须是数组: " + file);
            }
            int index = 0;
            for (JsonNode entry : entries) {
                JsonNode target = entry.path("target");
                String id = ConventionProfileJsonReader.text(entry, "id");
                String annotationType = ConventionProfileJsonReader.text(entry, "annotationType");
                IntentTarget intentTarget = new IntentTarget(
                        ConventionProfileJsonReader.text(target, "kind"),
                        ConventionProfileJsonReader.text(target, "binaryName"),
                        ConventionProfileJsonReader.textOrEmpty(target, "memberDescriptor"));
                declarations.add(new IntentDeclaration(
                        AtlasId.of(id),
                        annotationType,
                        AssetKind.valueOf(ConventionProfileJsonReader.text(entry, "assetKind")),
                        intentTarget,
                        ConventionProfileJsonReader.values(entry.path("attributes")),
                        evidence(file, sourceRevision, index, id, annotationType, intentTarget)));
                index++;
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Atlas Intent Manifest: " + file, exception);
        }
    }

    private static Evidence evidence(
            Path file,
            String sourceRevision,
            int index,
            String declarationId,
            String annotationType,
            IntentTarget target) {
        String identity = file.getFileName() + ":" + index + ":" + declarationId;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(identity.getBytes(StandardCharsets.UTF_8));
        return new Evidence(
                AtlasId.of("evidence:intent-manifest:" + encoded),
                EvidenceOrigin.ANNOTATION_MANIFEST,
                EvidenceClaim.DECLARED,
                EvidenceStatus.VERIFIED,
                new SourceLocator("generated://atlas-intent/" + encoded, target.binaryName() + " " + target.memberDescriptor(), SourceRange.unknown()),
                sourceRevision,
                EvidenceVisibility.EXPERT_ONLY,
                READER_ID,
                "由 " + annotationType + " 显式声明的 Atlas 意图");
    }
}
