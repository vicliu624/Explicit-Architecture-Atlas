package fixture;

import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasDomainPrimitive;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasGuard;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasGuardKind;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasInput;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasOperation;
import io.github.vicliu624.explicitarchitecture.atlas.annotation.AtlasOutput;

@AtlasDomainPrimitive(
        id = "value:customer-id",
        name = "客户标识",
        description = "业务客户的稳定身份")
record CustomerId(String value) {
}

final class IntentSample {

    @AtlasGuard(id = "guard:required", kind = AtlasGuardKind.REQUIRED, subjectParameter = 0, messageParameter = 1)
    static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    @AtlasOperation(id = "operation:register-customer", name = "登记客户", purpose = "登记一个合法客户")
    @AtlasOutput(id = "value:registered-customer", name = "已登记客户")
    static CustomerId register(@AtlasInput(id = "input:raw-customer-id", name = "原始客户标识") String rawCustomerId) {
        return new CustomerId(require(rawCustomerId, "客户标识不能为空"));
    }
}
